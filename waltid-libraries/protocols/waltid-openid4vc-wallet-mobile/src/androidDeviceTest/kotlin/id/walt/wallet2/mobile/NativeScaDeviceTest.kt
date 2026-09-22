package id.walt.wallet2.mobile

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit operator opt-in: -e wallet.sca approve|cancel-retry. Uses only disposable synthetic data. */
class NativeScaDeviceTest {
    @Test
    fun protectedPaymentProof() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val route = InstrumentationRegistry.getArguments().getString("wallet.sca")
        assumeTrue("Requires a physical-device operator", route in setOf("approve", "cancel-retry"))
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, ScaAuthorizationTestActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as ScaAuthorizationTestActivity
        instrumentation.waitForIdleSync()
        try {
            val result = exerciseNativeSca(AndroidPlatformKeyProvider(activity) { activity }, route == "cancel-retry")
            instrumentation.sendStatus(0, Bundle().apply { putString("sca_evidence", result) })
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
    }
}

class ScaAuthorizationTestActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(android.widget.TextView(this).apply {
            text = "WAL-1423 synthetic payment proof. Follow the operator instructions in Codex."
        })
    }
}
