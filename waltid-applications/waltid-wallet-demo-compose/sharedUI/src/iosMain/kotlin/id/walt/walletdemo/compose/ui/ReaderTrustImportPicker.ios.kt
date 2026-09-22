@file:OptIn(ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import id.walt.wallet2.mobile.ProximityReaderTrustSettingsCodec
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.cinterop.reinterpret
import platform.Foundation.NSInputStream
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

@Composable
internal actual fun rememberReaderTrustImportPicker(
    onResult: (ReaderTrustImportPickerResult) -> Unit,
): ReaderTrustImportPicker {
    val scope = rememberCoroutineScope()
    val delegate = remember(scope) { ReaderTrustDocumentPickerDelegate(scope) }
    delegate.onResult = onResult
    return remember(delegate) {
        ReaderTrustImportPicker {
            val picker = UIDocumentPickerViewController(
                forOpeningContentTypes = listOf(UTTypeItem),
            )
            picker.delegate = delegate
            UIApplication.sharedApplication.keyWindow?.rootViewController
                ?.topPresentedViewController()
                ?.presentViewController(picker, animated = true, completion = null)
                ?: onResult(
                    ReaderTrustImportPickerResult.Failed(
                        IllegalStateException("File picker is unavailable")
                    )
                )
        }
    }
}

private class ReaderTrustDocumentPickerDelegate(private val scope: CoroutineScope) : NSObject(), UIDocumentPickerDelegateProtocol {
    private var readJob: Job? = null
    var onResult: (ReaderTrustImportPickerResult) -> Unit = {}

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.singleOrNull() as? NSURL
        if (url == null) {
            onResult(
                ReaderTrustImportPickerResult.Failed(
                    IllegalArgumentException("Select one reader trust file")
                )
            )
            return
        }
        readJob?.cancel()
        readJob = scope.launch {
            val result = withContext(Dispatchers.Default) { runCatching {
                val accessGranted = url.startAccessingSecurityScopedResource()
                try {
                    val stream = NSInputStream(uRL = url)
                    stream.open()
                    val buffer = ByteArray(ProximityReaderTrustSettingsCodec.MaximumImportBytes + 1)
                    val size = try {
                        var size = 0
                        buffer.usePinned { pinned ->
                            while (size < buffer.size) {
                                val count = stream.read(pinned.addressOf(size).reinterpret(), (buffer.size - size).toULong()).toInt()
                                require(count >= 0) { "The selected file could not be read" }
                                if (count == 0) break
                                size += count
                            }
                        }
                        size
                    } finally { stream.close() }
                    require(size <= ProximityReaderTrustSettingsCodec.MaximumImportBytes) { "The imported file exceeds 1 MiB" }
                    ReaderTrustImportFile(url.lastPathComponent ?: "reader-trust-import", buffer.copyOf(size))
                } finally {
                    if (accessGranted) url.stopAccessingSecurityScopedResource()
                }
            } }
            result.fold(
                onSuccess = { onResult(ReaderTrustImportPickerResult.Selected(it)) },
                onFailure = { onResult(ReaderTrustImportPickerResult.Failed(it)) },
            )
        }
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        readJob?.cancel()
        onResult(ReaderTrustImportPickerResult.Cancelled)
    }
}

private fun platform.UIKit.UIViewController.topPresentedViewController(): platform.UIKit.UIViewController =
    presentedViewController?.topPresentedViewController() ?: this
