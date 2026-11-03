package id.walt.walletdemo.compose.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.ClaimItemPath
import id.walt.walletdemo.compose.logic.DisplayValue
import id.walt.walletdemo.compose.ui.components.ClaimValueRow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.TimeZone as HostTimeZone
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
class WalletDateTimeAndroidTest {
    @Test
    fun cachedClaimRefreshesAfterTimeZoneAndClockPreferenceChanges() {
        val app = RuntimeEnvironment.getApplication()
        val previousZone = HostTimeZone.getDefault()
        val previousClock = Settings.System.getString(app.contentResolver, Settings.System.TIME_12_24)
        try {
            HostTimeZone.setDefault(HostTimeZone.getTimeZone("Europe/Budapest"))
            Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "24")
            val item = ClaimItem(ClaimItemPath.topLevel("iat"), label = "Issued at",
                value = DisplayValue.InstantValue(Instant.parse("2026-10-30T00:00:00Z")), rawValue = "1793318400")
            runComposeUiTest {
                setContent { WalletDemoTheme { ClaimValueRow(item) } }
                onNodeWithText("01:00:00 UTC+01:00", substring = true).assertIsDisplayed()
                runOnIdle {
                    HostTimeZone.setDefault(HostTimeZone.getTimeZone("America/Los_Angeles"))
                    app.sendBroadcast(Intent(Intent.ACTION_TIMEZONE_CHANGED))
                }
                onNodeWithText("17:00:00 UTC-07:00", substring = true).assertIsDisplayed()
                onNodeWithText("Oct 29, 2026", substring = true).assertIsDisplayed()
                runOnIdle {
                    Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, "12")
                    app.sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
                }
                onNodeWithText("5:00:00", substring = true).assertIsDisplayed()
                onNodeWithText("PM", substring = true).assertIsDisplayed()
            }
        } finally {
            HostTimeZone.setDefault(previousZone)
            Settings.System.putString(app.contentResolver, Settings.System.TIME_12_24, previousClock)
        }
    }
}
