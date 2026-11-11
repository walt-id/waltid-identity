package id.walt.wallet2.mobile

import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.mobile.test.PhysicalDeviceTest
import id.walt.wallet2.persistence.keys.AndroidPlatformKeyProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit operator opt-in: -e wallet.sca approve|cancel-retry. Uses only disposable synthetic data. */
@PhysicalDeviceTest
class NativeScaDeviceTest {
    @Test
    fun timedPaymentAuthorization() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue("Requires a physical-device operator", InstrumentationRegistry.getArguments().getString("wallet.sca") == "timed")
        val activity = instrumentation.startActivitySync(
            Intent(instrumentation.targetContext, ScaAuthorizationTestActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as ScaAuthorizationTestActivity
        instrumentation.waitForIdleSync()
        try {
            val result = exerciseTimedNativeSca(AndroidPlatformKeyProvider(activity) { activity }) { step ->
                instrumentation.sendStatus(0, Bundle().apply { putString("sca_step", step) })
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine<Unit> { continuation ->
                        activity.setContentView(LinearLayout(activity).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(32, 100, 32, 32)
                            addView(TextView(activity).apply { text = "Synthetic EUR 12.50 payment to Synthetic shop.\n\n$step" })
                            addView(Button(activity).apply {
                                text = "Start step"
                                setOnClickListener { isEnabled = false; continuation.resume(Unit) }
                            })
                        })
                    }
                }
            }
            instrumentation.sendStatus(0, Bundle().apply { putString("sca_evidence", result) })
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }

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
        setContentView(TextView(this).apply {
            text = "Synthetic payment proof test. Follow the operator instructions in Codex."
        })
    }
}
