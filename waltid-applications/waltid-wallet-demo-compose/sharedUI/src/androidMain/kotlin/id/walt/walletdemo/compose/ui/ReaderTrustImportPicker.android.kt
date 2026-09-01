package id.walt.walletdemo.compose.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import id.walt.wallet2.mobile.MobileWalletProximityReaderTrustSettingsCodec
import java.io.ByteArrayOutputStream

@Composable
internal actual fun rememberReaderTrustImportPicker(
    onResult: (Result<ReaderTrustImportFile>) -> Unit,
): ReaderTrustImportPicker {
    val resolver = LocalContext.current.contentResolver
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onResult(runCatching { resolver.readReaderTrustFile(uri) })
    }
    return remember(launcher) {
        ReaderTrustImportPicker {
            launcher.launch(
                arrayOf(
                    "application/json",
                    "application/x-x509-ca-cert",
                    "application/pkix-cert",
                    "application/pem-certificate-chain",
                    "application/octet-stream",
                    "text/plain",
                )
            )
        }
    }
}

private fun ContentResolver.readReaderTrustFile(uri: Uri): ReaderTrustImportFile {
    val name = query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.takeIf(String::isNotBlank) ?: "reader-trust-import"
    val bytes = requireNotNull(openInputStream(uri)) { "The selected file could not be opened" }.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= MobileWalletProximityReaderTrustSettingsCodec.MaximumImportBytes) {
                "The imported file exceeds 1 MiB"
            }
        }
        output.toByteArray()
    }
    return ReaderTrustImportFile(name, bytes)
}
