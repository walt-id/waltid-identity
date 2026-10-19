package id.walt.walletdemo.compose.ui

import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.components.ReviewScaffold
import id.walt.walletdemo.compose.ui.components.WalletAction
import id.walt.walletdemo.compose.ui.components.WalletActions
import java.util.concurrent.ConcurrentLinkedQueue

/** Isolated GPU fixture: production chrome, synthetic calibration content, no wallet services. */
class WalletFooterGpuActivity : ComponentActivity() {
    val frames = ConcurrentLinkedQueue<Pair<Long, Long>>()
    private val frameListener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        frames.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION) to
            if (Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else -1L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addOnFrameMetricsAvailableListener(frameListener, Handler(Looper.getMainLooper()))
        setContent {
            WalletDemoTheme {
                CompositionLocalProvider(LocalWalletVisualPreferences provides WalletVisualPreferences(
                    reduceMotion = true, opaqueControls = intent.getBooleanExtra("opaque", false))) {
                    Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                        ReviewScaffold(actions = {
                            WalletActions(primary = WalletAction("Continue", onClick = {}, testTag = "gpu.continue"),
                                secondary = WalletAction("Cancel", onClick = {}))
                        }) {
                            repeat(24) { index ->
                                Box(Modifier.fillMaxWidth().height(64.dp)
                                    .background(if (index % 2 == 0) Color(0xFFDC3030) else Color(0xFF3030DC))
                                    .testTag("gpu.row.$index")) { Text("Synthetic row $index") }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        window.removeOnFrameMetricsAvailableListener(frameListener)
        super.onDestroy()
    }
}
