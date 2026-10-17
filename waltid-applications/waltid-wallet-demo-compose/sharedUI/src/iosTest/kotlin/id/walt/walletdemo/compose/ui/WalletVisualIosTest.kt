@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test
import kotlinx.cinterop.toKString

/** Compose iOS/Skia content on an iOS simulator; does not imitate a UIKit provider container. */
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class, InternalComposeUiApi::class)
class WalletVisualIosTest {
    @Test
    fun settingsRoot() = scenario { settingsRoot() }

    @Test
    fun credentialDetails() = scenario { credentialDetails() }

    @Test
    fun batchOffer() = scenario { batchOffer() }

    @Test
    fun batchOfferWithNothingSelected() = scenario { batchOffer(noneSelected = true) }

    @Test
    fun paymentReview() = scenario { paymentReview() }

    @Test
    fun credentialImages() = scenario { credentialImages() }

    @Test
    fun partialBatchResult() = scenario { partialBatchResult() }

    @Test
    fun nearbyReady() = scenario { nearbyReady() }

    private fun scenario(size: Size = Size(393f, 852f), dark: Boolean = false, fontScale: Float = 1f,
                         block: WalletVisualScenarios.() -> Unit) = runSkikoComposeUiTest(size = size) {
        WalletVisualScenarios(this,
            captureImage = { id, direction ->
                fun environment(key: String) = platform.posix.getenv(key)?.toKString() ?: ""
                val metadata: Map<String, Any> = mapOf(
                    "runId" to environment("WALLET_VISUAL_RUN_ID"), "sha" to environment("WALLET_VISUAL_SHA"),
                    "fingerprint" to environment("WALLET_VISUAL_FINGERPRINT"),
                    "id" to id, "renderer" to "compose-ios", "kind" to "deterministic",
                    "viewportWidth" to size.width.toDouble(), "viewportHeight" to size.height.toDouble(),
                    "scale" to 1.0, "fontScale" to fontScale.toDouble(), "theme" to if (dark) "dark" else "light",
                    "locale" to "en-US", "timezone" to environment("TZ"), "layoutDirection" to direction)
                onRoot().captureRoboImage(this, filePath = "compose-ios-phone-en-light/$id.png",
                    roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(contextData = metadata))
            },
            // Headless Skia tests have no UIKit window from which to read the display theme.
            platformTheme = { content -> CompositionLocalProvider(LocalSystemTheme provides if (dark) SystemTheme.Dark else SystemTheme.Light,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    androidx.compose.ui.platform.LocalDensity.current.density, fontScale), content = content) },
        ).block()
    }
}
