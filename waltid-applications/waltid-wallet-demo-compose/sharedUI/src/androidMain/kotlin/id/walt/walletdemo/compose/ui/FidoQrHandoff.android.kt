package id.walt.walletdemo.compose.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

private val fidoQrPattern = Regex("^FIDO:/[0-9]{3,7089}$", RegexOption.IGNORE_CASE)

@Composable
internal actual fun rememberFidoQrHandoff(): ((String) -> FidoQrHandoffResult)? {
    val context = LocalContext.current
    return remember(context) { { code -> handoffFidoQr(context, code) } }
}

internal fun handoffFidoQr(
    context: Context,
    code: String,
    playServices: GoogleApiAvailability = GoogleApiAvailability.getInstance(),
): FidoQrHandoffResult {
    if (!fidoQrPattern.matches(code)) return FidoQrHandoffResult.InvalidCode

    return try {
        // A FIDO QR contains pairing secrets. Verify the receiver before sending it any data.
        when (playServices.isGooglePlayServicesAvailable(context)) {
            ConnectionResult.SUCCESS -> Unit
            ConnectionResult.SERVICE_INVALID -> return FidoQrHandoffResult.Blocked
            else -> return FidoQrHandoffResult.Unavailable
        }
        // Android scheme matching is case-sensitive. Preserve every encoded payload digit.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("FIDO:/" + code.drop(6)))
            // Scope the bootstrap receiver; Play Services still selects from eligible holders.
            .setPackage(GoogleApiAvailability.GOOGLE_PLAY_SERVICES_PACKAGE)
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        FidoQrHandoffResult.Opened
    } catch (_: ActivityNotFoundException) {
        FidoQrHandoffResult.Unavailable
    } catch (_: SecurityException) {
        FidoQrHandoffResult.Blocked
    }
}
