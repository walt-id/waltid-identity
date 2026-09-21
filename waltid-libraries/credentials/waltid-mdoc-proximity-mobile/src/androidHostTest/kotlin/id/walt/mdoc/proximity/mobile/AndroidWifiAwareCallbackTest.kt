package id.walt.mdoc.proximity.mobile

import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.AwareResources
import android.net.wifi.aware.Characteristics
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import id.walt.mdoc.proximity.ProximityCloseReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.annotation.Implementation
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowWifiAwareManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], shadows = [PendingAwareManager::class, PendingAwareSession::class,
    PendingPublishSession::class, AwareCharacteristics::class, AwareWifi::class])
class AndroidWifiAwareCallbackTest {
    @Test fun attachFailureClosesLateSuccessAndFreshPublisherCanStart() = runTest {
        val fixture = Fixture()
        val creating = async { runCatching { fixture.create(backgroundScope) } }
        runCurrent()
        fixture.native.attach.onAttachFailed()
        runCurrent()
        assertTrue(creating.await().isFailure)
        val late = fixture.session()
        fixture.native.attach.onAttached(late)
        assertEquals(1, Shadow.extract<PendingAwareSession>(late).closes)
        val fresh = async { fixture.create(backgroundScope) }
        runCurrent()
        val selected = fixture.session()
        fixture.native.attach.onAttached(selected)
        runCurrent()
        val published = fixture.published()
        Shadow.extract<PendingAwareSession>(selected).callback.onPublishStarted(published)
        runCurrent()
        fresh.await().close(ProximityCloseReason.COMPLETED)
        assertEquals(1, Shadow.extract<PendingPublishSession>(published).closes)
        assertEquals(1, Shadow.extract<PendingAwareSession>(selected).closes)
    }

    @Test fun publishFailureDisposesAttachAndLatePublishResourceOnce() = runTest {
        val fixture = Fixture()
        val creating = async { runCatching { fixture.create(backgroundScope) } }
        runCurrent()
        val session = fixture.session()
        fixture.native.attach.onAttached(session)
        runCurrent()
        val nativeSession = Shadow.extract<PendingAwareSession>(session)
        nativeSession.callback.onSessionConfigFailed()
        runCurrent()
        assertTrue(creating.await().isFailure)
        val late = fixture.published()
        nativeSession.callback.onPublishStarted(late)
        assertEquals(1, nativeSession.closes)
        assertEquals(1, Shadow.extract<PendingPublishSession>(late).closes)
    }

    @Test fun networkFailureClosesPendingAcceptAndDisposesItsCallback() = runTest {
        val fixture = Fixture()
        val creating = async { fixture.create(backgroundScope) }
        runCurrent()
        val session = fixture.session()
        fixture.native.attach.onAttached(session)
        runCurrent()
        val discovery = Shadow.extract<PendingAwareSession>(session).callback
        val published = fixture.published()
        discovery.onPublishStarted(published)
        runCurrent()
        val holder = creating.await()
        discovery.onMessageReceived(ReflectionHelpers.callConstructor(PeerHandle::class.java, ClassParameter.from(Int::class.javaPrimitiveType!!, 9)), byteArrayOf())
        val connecting = async { runCatching { holder.awaitConnection() } }
        runCurrent()
        val callback = shadowOf(fixture.connectivity).networkCallbacks.single()
        callback.onUnavailable()
        val result = connecting.await()
        assertTrue(result.isFailure)
        callback.onUnavailable() // A late duplicate must not close another owner's resources.
        holder.close(ProximityCloseReason.CANCELLED)
        assertTrue(shadowOf(fixture.connectivity).networkCallbacks.isEmpty())
        assertEquals(1, Shadow.extract<PendingPublishSession>(published).closes)
        assertEquals(1, Shadow.extract<PendingAwareSession>(session).closes)
    }

    @Test fun cancellationAtAttachOrPublishDisposesLateNativeResources() = runTest {
        for (atPublish in listOf(false, true)) {
            val fixture = Fixture()
            val creating = async { fixture.create(backgroundScope) }
            runCurrent()
            val session = fixture.session()
            if (atPublish) {
                fixture.native.attach.onAttached(session)
                runCurrent()
            }
            creating.cancelAndJoin()
            val nativeSession = Shadow.extract<PendingAwareSession>(session)
            if (atPublish) {
                val late = fixture.published()
                nativeSession.callback.onPublishStarted(late)
                assertEquals(1, Shadow.extract<PendingPublishSession>(late).closes)
            } else {
                fixture.native.attach.onAttached(session)
            }
            assertEquals(1, nativeSession.closes)
            assertTrue(shadowOf(fixture.connectivity).networkCallbacks.isEmpty())
        }
    }

    private class Fixture {
        val context = RuntimeEnvironment.getApplication()
        val manager = context.getSystemService(WifiAwareManager::class.java)
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val native = Shadow.extract<PendingAwareManager>(manager)
        fun session() = ShadowWifiAwareManager.newWifiAwareSession(manager, Binder(), 1)
        fun published() = ShadowWifiAwareManager.newPublishDiscoverySession(manager, 1, 1)
        suspend fun create(scope: CoroutineScope) = AndroidWifiAwarePreparedPublisher.create(context, manager,
            connectivity, context.getSystemService(WifiManager::class.java), "0123456789ABCDEF0123456789ABCDEF",
            "0123456789abcdef", scope)
    }
}

@Implements(WifiAwareManager::class)
class PendingAwareManager {
    lateinit var attach: AttachCallback
    @Implementation fun isAvailable() = true
    @Implementation fun attach(callback: AttachCallback, handler: Handler?) { attach = callback }
    @Implementation fun getCharacteristics() = ReflectionHelpers.callConstructor(Characteristics::class.java, ClassParameter.from(Bundle::class.java, Bundle()))
    @Implementation fun getAvailableAwareResources() = AwareResources(1, 1, 1)
}

@Implements(WifiAwareSession::class)
class PendingAwareSession {
    var closes = 0
    lateinit var callback: DiscoverySessionCallback
    @Implementation fun close() { closes++ }
    @Implementation fun publish(config: PublishConfig, callback: DiscoverySessionCallback, handler: Handler?) {
        this.callback = callback
    }
}

@Implements(DiscoverySession::class)
class PendingPublishSession {
    var closes = 0
    @Implementation fun close() { closes++ }
    @Implementation fun sendMessage(peer: PeerHandle, messageId: Int, message: ByteArray) {}
}

@Implements(Characteristics::class)
class AwareCharacteristics {
    @Implementation fun getSupportedCipherSuites() = Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_SK_128
}

@Implements(WifiManager::class)
class AwareWifi {
    @Implementation fun is24GHzBandSupported() = true
}
