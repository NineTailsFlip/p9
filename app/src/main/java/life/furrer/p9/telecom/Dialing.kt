package life.furrer.p9.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager

/** Places a call, preferring `TelecomManager.placeCall` (needed to ever get a call screen — see
 *  [DialerRole]) and falling back to `ACTION_CALL` when P9 doesn't hold the role. Both paths need
 *  `CALL_PHONE`, already requested by every caller of this function before it's ever reached —
 *  checked again here regardless, since `placeCall` requires it be verified at the call site
 *  lint can see, not just by whoever called us. SIM selection (`EXTRA_PHONE_ACCOUNT_HANDLE`)
 *  lands in M6, once there's a Settings row to configure it. */
fun placeCall(context: Context, number: String) {
    if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return
    if (DialerRole.isHeld(context)) {
        val telecomManager = context.getSystemService(TelecomManager::class.java)
        telecomManager.placeCall(Uri.fromParts("tel", number, null), null)
    } else {
        context.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")))
    }
}
