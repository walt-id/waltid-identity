package id.walt.walletdemo.compose.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FidoQrHandoffAndroidTest {
    @Test
    fun canonicalizesSchemeAndPreservesEveryPayloadDigit() {
        val context = RecordingContext()
        assertEquals(FidoQrHandoffResult.Opened, handoffFidoQr(context, "fido:/001234567890123456789"))
        assertEquals(Intent.ACTION_VIEW, context.started?.action)
        assertEquals("FIDO:/001234567890123456789", context.started?.dataString)
        assertTrue((context.started!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertNull(context.started?.extras)
    }

    @Test
    fun activityAndWrappedActivityDoNotOpenANewTask() {
        var started: Intent? = null
        val activity = object : Activity() {
            override fun startActivity(intent: Intent) { started = intent }
        }
        for (context in listOf(activity, ContextWrapper(ContextWrapper(activity)))) {
            started = null
            assertEquals(FidoQrHandoffResult.Opened, handoffFidoQr(context, "FIDO:/123"))
            assertFalse((started!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        }
    }

    @Test
    fun rejectsMalformedOrUnrelatedCodesBeforeDispatch() {
        for (code in listOf("FIDO://123", "FIDO:/12", "FIDO:/１２３", "FIDO:/123?redirect=evil",
            "FIDO:/" + "0".repeat(7090), "https://issuer.example/offer", "openid4vp://request")) {
            val context = RecordingContext()
            assertEquals(FidoQrHandoffResult.InvalidCode, handoffFidoQr(context, code))
            assertNull(context.started)
        }
    }

    @Test
    fun missingHandlerIsALaunchFailure() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        assertEquals(FidoQrHandoffResult.Unavailable, handoffFidoQr(context, "FIDO:/123"))
    }

    @Test
    fun blockedHandlerIsALaunchFailure() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) { throw SecurityException() }
        }
        assertEquals(FidoQrHandoffResult.Blocked, handoffFidoQr(context, "FIDO:/123"))
    }

    private class RecordingContext : ContextWrapper(RuntimeEnvironment.getApplication()) {
        var started: Intent? = null
        override fun startActivity(intent: Intent) { started = intent }
    }
}
