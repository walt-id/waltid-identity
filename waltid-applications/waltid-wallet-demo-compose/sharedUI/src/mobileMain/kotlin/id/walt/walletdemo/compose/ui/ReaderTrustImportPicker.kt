package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable

internal data class ReaderTrustImportFile(
    val name: String,
    val bytes: ByteArray,
)

internal fun interface ReaderTrustImportPicker {
    fun launch()
}

@Composable
internal expect fun rememberReaderTrustImportPicker(
    onResult: (Result<ReaderTrustImportFile>) -> Unit,
): ReaderTrustImportPicker
