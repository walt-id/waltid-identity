package id.walt.walletdemo.compose.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.pm.Signature
import android.net.Uri
import android.os.Bundle
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FidoQrHandoffAndroidTest {
    @Before
    fun declaresPlayServicesClientVersion() {
        val application = RuntimeEnvironment.getApplication()
        val packageInfo = application.packageManager.getPackageInfo(application.packageName, PackageManager.GET_META_DATA)
        packageInfo.applicationInfo!!.metaData = Bundle().apply {
            putInt("com.google.android.gms.version", GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE)
        }
        shadowOf(application.packageManager).installPackage(packageInfo)
    }

    @Test
    fun scopesBootstrapToPlayServicesAndPreservesEveryPayloadDigit() {
        val context = RecordingContext()
        assertEquals(FidoQrHandoffResult.Opened,
            handoffFidoQr(context, "fido:/001234567890123456789", availablePlayServices()))
        assertEquals(Intent.ACTION_VIEW, context.started?.action)
        assertEquals("FIDO:/001234567890123456789", context.started?.dataString)
        assertEquals("com.google.android.gms", context.started?.`package`)
        // No internal Activity name or holder-app selection is imposed on the broker.
        assertNull(context.started?.component)
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
            assertEquals(FidoQrHandoffResult.Opened, handoffFidoQr(context, "FIDO:/123", availablePlayServices()))
            assertFalse((started!!.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        }
    }

    @Test
    fun rejectsMalformedOrUnrelatedCodesBeforeDispatch() {
        for (code in listOf("FIDO://123", "FIDO:/12", "FIDO:/１２３", "FIDO:/123?redirect=evil",
            "FIDO:/" + "0".repeat(7090), "https://issuer.example/offer", "openid4vp://request")) {
            val context = RecordingContext()
            val playServices = object : GoogleApiAvailability() {
                override fun isGooglePlayServicesAvailable(context: Context): Int = error("Malformed code reached receiver check")
            }
            assertEquals(FidoQrHandoffResult.InvalidCode, handoffFidoQr(context, code, playServices))
            assertNull(context.started)
        }
    }

    @Test
    fun matchingReceiverWithForgedPlayServicesPackageNameIsRejectedByRealVerifier() {
        installUntrustedPackage("com.android.vending")
        installUntrustedPackage("com.google.android.gms")
        addMatchingReceiver("com.google.android.gms")
        val context = RecordingContext()
        assertEquals("com.google.android.gms", context.packageManager.resolveActivity(fidoIntent(), 0)?.activityInfo?.packageName)
        assertEquals(ConnectionResult.SERVICE_INVALID,
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context))
        assertEquals(FidoQrHandoffResult.Blocked, handoffFidoQr(context, "FIDO:/123"))
        assertNull(context.started)
    }

    @Test
    fun missingPlayServicesNeverFallsBackToAnUntrustedMatchingApp() {
        installUntrustedPackage("com.android.vending")
        installUntrustedPackage("example.untrusted.receiver")
        addMatchingReceiver("example.untrusted.receiver")
        val context = RecordingContext()
        assertEquals("example.untrusted.receiver", context.packageManager.resolveActivity(fidoIntent(), 0)?.activityInfo?.packageName)
        assertEquals(FidoQrHandoffResult.Unavailable, handoffFidoQr(context, "FIDO:/123"))
        assertNull(context.started)
    }

    @Test
    fun unavailablePlayServicesDoesNotDispatch() {
        for (status in listOf(ConnectionResult.SERVICE_DISABLED, ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED,
            ConnectionResult.SERVICE_UPDATING, ConnectionResult.SERVICE_MISSING_PERMISSION)) {
            val context = RecordingContext()
            val playServices = object : GoogleApiAvailability() {
                override fun isGooglePlayServicesAvailable(context: Context): Int = status
            }
            assertEquals(FidoQrHandoffResult.Unavailable, handoffFidoQr(context, "FIDO:/123", playServices))
            assertNull(context.started)
        }
    }

    @Test
    fun blockedReceiverVerificationDoesNotDispatch() {
        val context = RecordingContext()
        val playServices = object : GoogleApiAvailability() {
            override fun isGooglePlayServicesAvailable(context: Context): Int = throw SecurityException()
        }
        assertEquals(FidoQrHandoffResult.Blocked, handoffFidoQr(context, "FIDO:/123", playServices))
        assertNull(context.started)
    }

    @Test
    fun missingHandlerIsALaunchFailure() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) { throw ActivityNotFoundException() }
        }
        assertEquals(FidoQrHandoffResult.Unavailable, handoffFidoQr(context, "FIDO:/123", availablePlayServices()))
    }

    @Test
    fun blockedHandlerIsALaunchFailure() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun startActivity(intent: Intent) { throw SecurityException() }
        }
        assertEquals(FidoQrHandoffResult.Blocked, handoffFidoQr(context, "FIDO:/123", availablePlayServices()))
    }

    private fun availablePlayServices() = object : GoogleApiAvailability() {
        override fun isGooglePlayServicesAvailable(context: Context): Int = ConnectionResult.SUCCESS
    }

    @Suppress("DEPRECATION")
    private fun installUntrustedPackage(name: String) {
        shadowOf(RuntimeEnvironment.getApplication().packageManager).installPackage(PackageInfo().apply {
            packageName = name
            versionCode = GoogleApiAvailability.GOOGLE_PLAY_SERVICES_VERSION_CODE
            applicationInfo = ApplicationInfo().apply { packageName = name; enabled = true }
            signatures = arrayOf(Signature(ByteArray(32) { 1 }))
        })
    }

    private fun fidoIntent() = Intent(Intent.ACTION_VIEW, Uri.parse("FIDO:/123"))

    private fun addMatchingReceiver(name: String) {
        shadowOf(RuntimeEnvironment.getApplication().packageManager).addResolveInfoForIntent(fidoIntent(), ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = name; this.name = "$name.ScanActivity"; exported = true }
        })
    }

    private class RecordingContext : ContextWrapper(RuntimeEnvironment.getApplication()) {
        var started: Intent? = null
        override fun startActivity(intent: Intent) { started = intent }
    }
}
