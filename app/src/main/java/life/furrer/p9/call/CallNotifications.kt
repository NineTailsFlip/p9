package life.furrer.p9.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.PowerManager

const val NOTIFICATION_ID_CALL = 1

private const val CHANNEL_INCOMING = "incoming_calls"
private const val CHANNEL_ONGOING = "ongoing_call"

/** The two notifications a live call needs (docs/p9-plan.md's "Staying alive and reachable") —
 *  the missed-calls channel joins these once M7 posts missed-call notifications. Both notify P9's
 *  own `ic_launcher_monochrome` as the small icon: it's already exactly the plain white-on-
 *  transparent silhouette a notification icon needs, no separate asset required. */
object CallNotifications {

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Ongoing call", NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** A full-screen intent to [CallActivity] is what actually shows the ringing screen over a
     *  locked or off screen (docs/p9-plan.md: "a dialer qualifies for it on Android 14"). But
     *  confirmed live on device: when the screen's already on, [P9InCallService]'s own direct
     *  `startActivity` call already shows that same screen immediately, so the full-screen-intent
     *  channel's heads-up peek just layers a redundant, confusing banner on top of the ringing
     *  screen it didn't need to open. [PowerManager.isInteractive] at post time (not
     *  [CallActivity]'s own resumed state, which lags behind an async `startActivity` and would
     *  still be false the first time this runs) is what the screen-already-on fast path in
     *  [P9InCallService]'s own doc comment already keys off of — reused here for the same
     *  screen-on/off distinction, posting the quiet ongoing-call channel instead of the urgent
     *  one whenever the fast path is expected to have it covered. */
    fun incomingCallNotification(context: Context, call: CallSession): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            context, 0, callActivityIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val screenAlreadyOn = context.getSystemService(PowerManager::class.java)?.isInteractive == true
        val builder = Notification.Builder(context, if (screenAlreadyOn) CHANNEL_ONGOING else CHANNEL_INCOMING)
            .setContentTitle(call.displayName ?: call.handle ?: "Unknown")
            .setContentText("Incoming call")
            .setSmallIcon(life.furrer.p9.R.drawable.ic_launcher_monochrome)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(fullScreenIntent)
        if (!screenAlreadyOn) {
            builder.setFullScreenIntent(fullScreenIntent, true)
        }
        return builder.build()
    }

    /** `Notification.CallStyle`'s own "return to call" affordance — this is how someone gets back
     *  to [CallActivity] after pressing Back (M6 makes Back leave the call screen without ending
     *  the call; until then there's little to "return" to, but the notification is harmless). */
    fun ongoingCallNotification(context: Context, call: CallSession): Notification {
        val person = Person.Builder().setName(call.displayName ?: call.handle ?: "Unknown").build()
        val contentIntent = PendingIntent.getActivity(
            context, 0, callActivityIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = PendingIntent.getBroadcast(
            context, 0, Intent(context, DisconnectCallReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(life.furrer.p9.R.drawable.ic_launcher_monochrome)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setStyle(Notification.CallStyle.forOngoingCall(person, disconnectIntent))
            .build()
    }

    private fun callActivityIntent(context: Context): Intent =
        Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
