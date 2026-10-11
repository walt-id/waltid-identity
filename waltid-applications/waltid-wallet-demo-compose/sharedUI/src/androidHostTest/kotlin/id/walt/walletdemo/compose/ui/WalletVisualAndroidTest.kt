package id.walt.walletdemo.compose.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runComposeUiTest
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Bounded production content. System bars and provider hosts have separate integration coverage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w393dp-h852dp-notnight-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class WalletVisualAndroidTest {
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

    private fun scenario(fontScale: Float = 1f, block: WalletVisualScenarios.() -> Unit) = runComposeUiTest {
        WalletVisualScenarios(this, captureImage = { id, direction ->
            val directory = checkNotNull(System.getProperty("roborazzi.output.dir")) { "Roborazzi output directory is not configured" }
            val configuration = android.content.res.Resources.getSystem().configuration
            val density = android.content.res.Resources.getSystem().displayMetrics.density
            val metadata: Map<String, Any> = mapOf(
                "runId" to (System.getenv("WALLET_VISUAL_RUN_ID") ?: ""),
                "sha" to (System.getenv("WALLET_VISUAL_SHA") ?: ""),
                "fingerprint" to (System.getenv("WALLET_VISUAL_FINGERPRINT") ?: ""),
                "id" to id, "renderer" to "android", "kind" to "deterministic",
                "viewportWidth" to configuration.screenWidthDp, "viewportHeight" to configuration.screenHeightDp,
                "scale" to density.toDouble(), "fontScale" to fontScale.toDouble(),
                "theme" to if (configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                    android.content.res.Configuration.UI_MODE_NIGHT_YES) "dark" else "light",
                "locale" to configuration.locales[0].toLanguageTag(), "timezone" to java.util.TimeZone.getDefault().id,
                "layoutDirection" to direction)
            onRoot().captureRoboImage("$directory/android-api35-phone-en-light/$id.png",
                roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(contextData = metadata))
        }, platformTheme = { content ->
            // Android RenderThread ripples do not follow the Compose test clock.
            CompositionLocalProvider(LocalRippleConfiguration provides null,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(
                    androidx.compose.ui.platform.LocalDensity.current.density, fontScale), content = content)
        }).block()
    }
}
