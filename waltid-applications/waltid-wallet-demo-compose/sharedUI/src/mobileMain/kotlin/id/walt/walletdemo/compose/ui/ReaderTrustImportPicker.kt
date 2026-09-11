package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable

internal data class ReaderTrustImportFile(
    val name: String,
    val bytes: ByteArray,
)

internal sealed interface ReaderTrustImportPickerResult {
    data class Selected(val file: ReaderTrustImportFile) : ReaderTrustImportPickerResult

    data object Cancelled : ReaderTrustImportPickerResult

    data class Failed(val error: Throwable) : ReaderTrustImportPickerResult
}

internal fun interface ReaderTrustImportPicker {
    fun launch()
}

@Composable
internal expect fun rememberReaderTrustImportPicker(
    onResult: (ReaderTrustImportPickerResult) -> Unit,
): ReaderTrustImportPicker
