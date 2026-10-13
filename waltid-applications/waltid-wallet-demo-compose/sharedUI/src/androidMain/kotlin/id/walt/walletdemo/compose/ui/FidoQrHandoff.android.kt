package id.walt.walletdemo.compose.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private val fidoQrPattern = Regex("^FIDO:/[0-9]{3,7089}$", RegexOption.IGNORE_CASE)

@Composable
internal actual fun rememberFidoQrHandoff(): ((String) -> FidoQrHandoffResult)? {
    val context = LocalContext.current
    return remember(context) { { code -> handoffFidoQr(context, code) } }
}

internal fun handoffFidoQr(context: Context, code: String): FidoQrHandoffResult {
    if (!fidoQrPattern.matches(code)) return FidoQrHandoffResult.InvalidCode

    // Android scheme matching is case-sensitive. Preserve every encoded payload digit.
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("FIDO:/" + code.drop(6)))
    if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return try {
        // Direct dispatch also works when package visibility hides the installed handler.
        context.startActivity(intent)
        FidoQrHandoffResult.Opened
    } catch (_: ActivityNotFoundException) {
        FidoQrHandoffResult.Unavailable
    } catch (_: SecurityException) {
        FidoQrHandoffResult.Blocked
    }
}
