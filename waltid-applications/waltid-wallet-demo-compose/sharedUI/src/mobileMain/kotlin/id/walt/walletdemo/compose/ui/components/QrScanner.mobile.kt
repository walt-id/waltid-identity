package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.publicvalue.multiplatform.qrcode.CameraPosition
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions

@Composable
internal actual fun PlatformQrScanner(
    modifier: Modifier,
    onCodeScanned: (String) -> Unit,
) {
    ScannerWithPermissions(
        modifier = modifier,
        onScanned = { value ->
            onCodeScanned(value)
            true
        },
        types = listOf(CodeType.QR),
        cameraPosition = CameraPosition.BACK,
        enableTorch = false,
    )
}
