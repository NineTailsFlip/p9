package life.furrer.p9.call

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager

private const val TAG = "P9:proximity"

/** AOSP's own threshold: a proximity reading under 5cm (or under the sensor's range, for a binary
 *  sensor whose range is smaller) counts as "something is against the screen". */
private const val NEAR_THRESHOLD_CM = 5f

/** Pure: should the screen go dark when something covers it? Only while a call is up at the ear —
 *  dialing, connected or on hold, through the earpiece. Not while anything is ringing (you have to
 *  see who's calling), not on speaker/Bluetooth/headset (the phone isn't at your face), and not
 *  with the in-call keypad open (you're looking at it to press digits). */
fun wantsProximityScreenOff(states: List<CallState>, onEarpiece: Boolean, keypadOpen: Boolean): Boolean {
    if (!onEarpiece || keypadOpen) return false
    if (CallState.RINGING in states) return false
    return states.any { it == CallState.DIALING || it == CallState.ACTIVE || it == CallState.HOLDING }
}

/** Pure: does this proximity reading mean covered? */
fun isProximityNear(distance: Float, maximumRange: Float): Boolean =
    distance >= 0f && distance < minOf(NEAR_THRESHOLD_CM, maximumRange)

/** Turns the screen off against the cheek during a call, with the platform's proximity wake lock —
 *  the same mechanism the stock dialer uses, so the display itself handles the on/off and P9
 *  only says when it applies. Lives with [P9InCallService] rather than [CallActivity]: a call
 *  held to the ear after Back has left the call screen still wants it.
 *
 *  The screen going dark is only half of it on a keypad phone. A proximity screen-off leaves the
 *  device interactive, so keys pressed by a cheek are still delivered to whatever has focus;
 *  [isCovered] is what lets [CallActivity] swallow them. */
object ProximityScreenOff {
    private var wakeLock: PowerManager.WakeLock? = null
    private var sensorManager: SensorManager? = null
    private var sensor: Sensor? = null
    private var listening = false

    /** Set by [CallActivity] while its keypad is showing. */
    var keypadOpen = false
        set(value) {
            field = value
            update()
        }

    /** Whether the proximity sensor currently reads near while the lock applies. */
    var isCovered = false
        private set

    private val onChanged = { update() }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            isCovered = isProximityNear(event.values[0], event.sensor.maximumRange)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun attach(context: Context) {
        val power = context.getSystemService(PowerManager::class.java)
        if (power != null && power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            wakeLock = power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG)
        }
        sensorManager = context.getSystemService(SensorManager::class.java)
        sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        CallRegistry.addListener(onChanged)
        AudioRouting.addListener(onChanged)
        update()
    }

    fun detach() {
        CallRegistry.removeListener(onChanged)
        AudioRouting.removeListener(onChanged)
        apply(false)
        wakeLock = null
        sensorManager = null
        sensor = null
        keypadOpen = false
    }

    /** Test-only: lets a test stand in for the sensor. */
    internal fun setCoveredForTest(covered: Boolean) {
        isCovered = covered
    }

    private fun update() {
        val states = CallRegistry.all().map { it.state }
        apply(wantsProximityScreenOff(states, AudioRouting.isOnEarpiece(), keypadOpen))
    }

    // An indefinite acquire is the point: the lock lasts exactly as long as the call is at the
    // ear, and every path out of that (call ends, speaker, keypad, service unbound) releases it.
    @android.annotation.SuppressLint("WakelockTimeout")
    private fun apply(wanted: Boolean) {
        val lock = wakeLock
        if (lock != null) {
            if (wanted && !lock.isHeld) lock.acquire()
            // Wait for no proximity: a call ending at the ear shouldn't light the screen against
            // the cheek — it comes on as the phone moves away.
            if (!wanted && lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
        val manager = sensorManager
        val proximity = sensor
        if (wanted && !listening && manager != null && proximity != null) {
            listening = manager.registerListener(sensorListener, proximity, SensorManager.SENSOR_DELAY_NORMAL)
        } else if (!wanted && listening) {
            manager?.unregisterListener(sensorListener)
            listening = false
        }
        if (!wanted) isCovered = false
    }
}
