@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import id.walt.wallet2.mobile.MobileWalletProximityReaderTrustSettingsCodec
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject
import platform.posix.memcpy

@Composable
internal actual fun rememberReaderTrustImportPicker(
    onResult: (Result<ReaderTrustImportFile>) -> Unit,
): ReaderTrustImportPicker {
    val delegate = remember { ReaderTrustDocumentPickerDelegate() }
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
                ?: onResult(Result.failure(IllegalStateException("File picker is unavailable")))
        }
    }
}

private class ReaderTrustDocumentPickerDelegate : NSObject(), UIDocumentPickerDelegateProtocol {
    var onResult: (Result<ReaderTrustImportFile>) -> Unit = {}

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>,
    ) {
        val url = didPickDocumentsAtURLs.singleOrNull() as? NSURL
        if (url == null) {
            onResult(Result.failure(IllegalArgumentException("Select one reader trust file")))
            return
        }
        onResult(runCatching {
            require(url.startAccessingSecurityScopedResource()) {
                "The selected file could not be accessed"
            }
            try {
                val data = requireNotNull(NSData.dataWithContentsOfURL(url)) {
                    "The selected file could not be opened"
                }
                require(
                    data.length <=
                        MobileWalletProximityReaderTrustSettingsCodec.MaximumImportBytes.toULong()
                ) {
                    "The imported file exceeds 1 MiB"
                }
                ReaderTrustImportFile(
                    name = url.lastPathComponent?.takeIf { it.isNotBlank() }
                        ?: "reader-trust-import",
                    bytes = data.toByteArray(),
                )
            } finally {
                url.stopAccessingSecurityScopedResource()
            }
        })
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) = Unit
}

private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { result ->
    if (result.isNotEmpty()) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }
}

private fun platform.UIKit.UIViewController.topPresentedViewController(): platform.UIKit.UIViewController =
    presentedViewController?.topPresentedViewController() ?: this
