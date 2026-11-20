@file:OptIn(ExperimentalForeignApi::class)

package id.walt.walletdemo.compose.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import id.walt.walletdemo.compose.logic.ClaimItem
import id.walt.walletdemo.compose.logic.ClaimItemPath
import id.walt.walletdemo.compose.logic.DisplayValue
import id.walt.walletdemo.compose.ui.components.ClaimValueRow
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSSystemTimeZoneDidChangeNotification
import platform.posix.getenv
import platform.posix.setenv
import platform.posix.tzset
import platform.posix.unsetenv
import kotlin.test.Test
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class WalletDateTimeIosTest {
    @Test
    fun cachedClaimRefreshesAfterTimeZoneChanges() {
        val previousZone = getenv("TZ")?.toKString()
        try {
            setenv("TZ", "Europe/Budapest", 1)
            tzset()
            val item = ClaimItem(
                ClaimItemPath.topLevel("iat"),
                label = "Issued at",
                value = DisplayValue.InstantValue(Instant.parse("2026-10-30T00:00:00Z")),
                rawValue = "1793318400",
            )
            runSkikoComposeUiTest(size = Size(393f, 852f)) {
                setContent { WalletDemoTheme { ClaimValueRow(item) } }
                onNodeWithText("Budapest,", substring = true).assertIsDisplayed()
                runOnIdle {
                    setenv("TZ", "America/Los_Angeles", 1)
                    tzset()
                    NSNotificationCenter.defaultCenter.postNotificationName(NSSystemTimeZoneDidChangeNotification, null)
                }
                onNodeWithText("Los Angeles,", substring = true).assertIsDisplayed()
            }
        } finally {
            previousZone?.let { setenv("TZ", it, 1) } ?: unsetenv("TZ")
            tzset()
        }
    }
}
