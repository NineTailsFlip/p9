package life.furrer.p9.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The ongoing-call notification's hang-up action (docs/p9-plan.md's `Notification.CallStyle`) —
 *  disconnects whatever call [CallRegistry] currently holds without opening [CallActivity] at
 *  all, the same "act on the call directly" shape [CallActivity]'s own End key already uses. */
class DisconnectCallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        CallRegistry.all().firstOrNull()?.disconnect()
    }
}
