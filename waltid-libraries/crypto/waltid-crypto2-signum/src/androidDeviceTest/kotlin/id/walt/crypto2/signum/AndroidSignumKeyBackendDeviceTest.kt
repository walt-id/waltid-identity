package id.walt.crypto2.signum

import id.walt.crypto2.keys.PlatformKeyConfiguration
import id.walt.crypto2.keys.HardwarePreference
import android.app.KeyguardManager
import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.test.platform.app.InstrumentationRegistry
import at.asitplus.signum.supreme.os.AndroidKeyStoreProvider
import at.asitplus.signum.supreme.os.AndroidKeystoreSigner
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class AndroidSignumKeyBackendDeviceTest {
    @Test
    fun nativeInvalidationRetainsItsCauseAndTakesPrecedenceOverMissingInteractionContext() {
        val native = KeyPermanentlyInvalidatedException()
        val wrapped = IllegalStateException("Native operation failed", native)
        val mapped = assertIs<SignumKeyInvalidatedException>(wrapped.mapSignumFailure("invalidated"))
        assertEquals("invalidated", mapped.alias)
        assertSame(wrapped, mapped.cause)
        assertSame(mapped, mapped.mapTimedReuseInteractionContextFailure("invalidated", false))
        val cancellation = CancellationException("Cancelled", native)
        assertSame(cancellation, cancellation.mapSignumFailure("invalidated"))
        assertSame(cancellation, cancellation.mapTimedReuseInteractionContextFailure("invalidated", false))
        val locked = UserNotAuthenticatedException()
        assertSame(locked, locked.mapSignumFailure("locked"))
    }

    @Test
    fun invalidationChecksAbortWithoutConsumingTheKeysSingleUse() = runTest {
        assumeTrue(Build.VERSION.SDK_INT >= 31)
        val alias = "invalidation-probe-${Uuid.random()}"
        val backend = AndroidSignumKeyBackend(InstrumentationRegistry.getInstrumentation().targetContext)
        val policy = SignumKeyPolicy(platform = PlatformKeyConfiguration.AndroidKeystore(
            strongBox = HardwarePreference.DISCOURAGED, maxUsageCount = 1))
        try {
            val key = backend.create(alias, KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY), policy)
            // More operations than a device can keep pending also detects a missing explicit abort.
            repeat(40) {
                val signer = assertIs<AndroidKeystoreSigner>(AndroidKeyStoreProvider.getSignerForKey(alias).getOrThrow())
                signer.checkKeyInvalidation()
                assertEquals(1, signer.keyInfo.remainingUsageCount)
            }
            val challenge = "single-use proof".encodeToByteArray()
            val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
            assertTrue(key.verify(challenge, key.sign(challenge, algorithm), algorithm))
        } finally { backend.delete(alias) }
    }

    @Test
    fun credentialProtectedKeyReopensWithoutAnInteractionContext() = runTest {
        assumeTrue(Build.VERSION.SDK_INT >= 30)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val alias = "invalidation-locked-${Uuid.random()}"
        val backend = AndroidSignumKeyBackend(InstrumentationRegistry.getInstrumentation().targetContext)
        val policy = SignumKeyPolicy(authentication = SignumAuthenticationPolicy.UserPresence(
            biometric = false, deviceCredential = true, timeoutSeconds = 10))
        val spec = KeySpec.Ec(EcCurve.P256)
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        try {
            backend.create(alias, spec, usages, policy)
            assertNotNull(AndroidSignumKeyBackend(InstrumentationRegistry.getInstrumentation().targetContext).load(alias, spec, usages, policy))
        } finally { backend.delete(alias) }
    }

    @Test
    fun importedP256RetainsOriginalKeyAfterDeletionAndReimport() = runTest {
        exerciseNativePrivateImport(AndroidSignumKeyBackend(InstrumentationRegistry.getInstrumentation().targetContext), SignumKeyPolicy(hardware = HardwarePreference.PREFERRED,
            platform = PlatformKeyConfiguration.AndroidKeystore(strongBox = HardwarePreference.DISCOURAGED)))
    }

    @Test
    fun platformKeySurvivesProviderRestart() = runTest {
        exercisePlatformSignumBackend(AndroidSignumKeyBackend(InstrumentationRegistry.getInstrumentation().targetContext), AndroidSignumKeyBackend(InstrumentationRegistry.getInstrumentation().targetContext))
    }
}
