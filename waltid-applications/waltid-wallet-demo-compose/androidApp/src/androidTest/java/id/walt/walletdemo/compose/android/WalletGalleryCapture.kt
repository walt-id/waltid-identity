package id.walt.walletdemo.compose.android

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.platform.io.PlatformTestStorageRegistry
import androidx.test.uiautomator.UiDevice
import java.security.MessageDigest

/** Optional screenshot hook used by the local API-first wallet gallery runner. */
internal object WalletGalleryCapture {
    private const val CAPTURE_ARGUMENT = "walletGalleryCapture"
    private const val NAME_ARGUMENT = "walletGalleryCaptureName"
    private const val FILE_PREFIX = "waltid-wallet-gallery"
    private const val OUTPUT_DIRECTORY = "wallet-gallery"
    private const val REQUEST_DIGEST_FILE = "$OUTPUT_DIRECTORY/request-digests.txt"

    fun capture(device: UiDevice, name: String) {
        val arguments = InstrumentationRegistry.getArguments()
        if (!arguments.getString(CAPTURE_ARGUMENT).toBoolean()) return

        val safeName = (arguments.getString(NAME_ARGUMENT)?.takeIf(String::isNotBlank) ?: name)
            .lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-')
            .ifBlank { "capture" }
        val outputPath = "$OUTPUT_DIRECTORY/$FILE_PREFIX-$safeName.png"
        device.waitForIdle()
        val screenshot = checkNotNull(
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot(),
        ) { "Could not capture wallet gallery image: $outputPath" }
        try {
            PlatformTestStorageRegistry.getInstance().openOutputFile(outputPath).use { output ->
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Could not encode wallet gallery image: $outputPath"
                }
            }
        } finally {
            screenshot.recycle()
        }
        println("WALLET_GALLERY_CAPTURE=$outputPath")
    }

    fun recordRequest(kind: String, value: String) {
        val arguments = InstrumentationRegistry.getArguments()
        if (!arguments.getString(CAPTURE_ARGUMENT).toBoolean()) return
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.encodeToByteArray())
            .joinToString("") { byte -> "%02x".format(byte) }
        val record = "WALLET_GALLERY_REQUEST_DIGEST=$kind:$digest"
        PlatformTestStorageRegistry.getInstance()
            .openOutputFile(REQUEST_DIGEST_FILE, true)
            .bufferedWriter()
            .use { output -> output.appendLine(record) }
        println(record)
    }
}
