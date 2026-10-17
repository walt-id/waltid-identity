package id.walt.walletdemo.compose.android

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertTrue
import java.io.File
import java.security.MessageDigest

/** Opt-in wallet-gallery/v1 evidence; normal E2E runs produce no gallery output. */
internal object WalletGalleryEvidence {
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val enabled get() = arguments.getString("walletGalleryCapture") == "true"

    private fun directory(): File {
        val runId = requireNotNull(arguments.getString("walletGalleryRunId"))
        require(runId.matches(Regex("[a-f0-9]{32}"))) { "Gallery run ID must be a UUID hex value" }
        val root = requireNotNull(arguments.getString("additionalTestOutputDir")) {
            "Gallery capture requires the Android test runner's additional output directory"
        }
        return File(root, "wallet-gallery-$runId").apply {
            check(isDirectory || mkdirs()) { "Could not create gallery output directory" }
        }
    }

    fun request(kind: String, value: String) {
        if (!enabled) return
        require(kind in setOf("issuance", "presentation"))
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val record = "WALLET_GALLERY_REQUEST_DIGEST=$kind:$digest"
        File(directory(), "request-digests.txt").appendText("$record\n")
        println(record)
    }

    /** Call only after the test has asserted the specific review state. */
    fun capture(device: UiDevice, name: String) {
        if (!enabled) return
        require(name.matches(Regex("[a-z0-9-]+")))
        val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
        assertTrue("Gallery app is not foreground", device.currentPackageName == packageName)
        val target = File(directory(), "waltid-wallet-gallery-$name.png")
        check(!target.exists()) { "Gallery capture already exists" }
        assertTrue("Could not capture gallery frame", device.takeScreenshot(target))
    }
}
