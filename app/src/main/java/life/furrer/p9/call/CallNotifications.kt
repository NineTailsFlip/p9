package life.furrer.p9.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.content.Intent

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
     *  locked or off screen (docs/p9-plan.md: "a dialer qualifies for it on Android 14") — this
     *  notification's own visible content barely matters, since CallActivity is meant to already
     *  be on screen by the time anyone would see it. */
    fun incomingCallNotification(context: Context, call: CallSession): Notification {
        val fullScreenIntent = PendingIntent.getActivity(
            context, 0, callActivityIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(context, CHANNEL_INCOMING)
            .setContentTitle(call.displayName ?: call.handle ?: "Unknown")
            .setContentText("Incoming call")
            .setSmallIcon(life.furrer.p9.R.drawable.ic_launcher_monochrome)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenIntent, true)
            .setContentIntent(fullScreenIntent)
            .build()
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
