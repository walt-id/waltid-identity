package id.walt.walletdemo.compose.ui

import android.content.Intent
import android.os.Build
import android.os.Debug
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WalletFooterGpuTest {
    @Test fun footerDrawsAndClearsItsLastRowWithBlurAndOpaqueControls() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        for (opaque in listOf(false, true)) {
            ActivityScenario.launch<WalletFooterGpuActivity>(Intent(context, WalletFooterGpuActivity::class.java)
                .putExtra("opaque", opaque)).use { scenario ->
                assertNotNull(device.wait(Until.findObject(By.res("gpu.continue")), 10_000))
                val content = requireNotNull(device.findObject(By.res("wallet.review.content"))).visibleBounds
                val footer = requireNotNull(device.findObject(By.res("wallet.footer"))).visibleBounds
                assertTrue("The interactive viewport must clear the controls", content.bottom <= footer.top)
                val mode = if (opaque) "opaque" else "blur"
                device.waitForIdle()
                assertTrue(device.takeScreenshot(File(context.filesDir, "footer-$mode-before.png")))
                var beforePssKb = 0L
                scenario.onActivity { beforePssKb = Debug.getPss(); it.frames.clear() }
                repeat(12) {
                    device.swipe(content.centerX(), content.bottom - 24, content.centerX(), content.top + 24, 20)
                }
                val last = device.wait(Until.findObject(By.res("gpu.row.23")), 5_000)
                assertNotNull("The final row must be reachable", last)
                assertTrue("The complete final row must clear the footer", requireNotNull(last).visibleBounds.bottom <= footer.top)
                device.waitForIdle()
                assertTrue(device.takeScreenshot(File(context.filesDir, "footer-$mode-after.png")))
                scenario.onActivity { activity ->
                    val frames = activity.frames.toList()
                    val totals = frames.map { it.first / 1_000_000.0 }.sorted()
                    val profile = JSONObject().put("mode", mode).put("api", Build.VERSION.SDK_INT)
                        .put("model", Build.MODEL).put("package", context.packageName)
                        .put("debuggable", context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
                        .put("refreshRateHz", activity.window.decorView.display?.refreshRate)
                        .put("processPssBeforeKb", beforePssKb).put("processPssAfterKb", Debug.getPss())
                        .put("frameCount", totals.size)
                        .put("p95Ms", totals.getOrNull((totals.size * .95).toInt()))
                        .put("missedDeadlines", frames.count { (total, deadline) -> deadline > 0 && total > deadline })
                        .put("frames", JSONArray(frames.map { (total, deadline) ->
                            JSONObject().put("totalNs", total).put("deadlineNs", deadline)
                        }))
                    File(context.filesDir, "footer-$mode-profile.json").writeText(profile.toString(2))
                    println("WALLET_FOOTER_GPU: mode=$mode frames=${totals.size} p95Ms=${profile.get("p95Ms")} " +
                        "missedDeadlines=${profile.get("missedDeadlines")}")
                }
            }
        }
    }
}
