package id.walt.crypto2.signum

import id.walt.crypto2.keys.PlatformKeyConfiguration
import id.walt.crypto2.keys.HardwarePreference
import id.walt.crypto2.keys.KeyProtectionLevel
import id.walt.crypto2.keys.KeyOrigin
import id.walt.crypto2.keys.KeySecurityLevel

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import androidx.test.platform.app.InstrumentationRegistry
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toSpkiDer
import kotlinx.coroutines.test.runTest
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** Run on API 31+ targets with StrongBox, TEE only, and software-backed Keystore. */
@RunWith(Parameterized::class)
class AndroidStrongBoxPreferenceTest(
    private val strongBox: HardwarePreference,
    private val hardware: HardwarePreference,
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "strongBox={0},hardware={1}")
        fun policies(): List<Array<HardwarePreference>> = HardwarePreference.entries.flatMap { strongBox ->
            listOf(HardwarePreference.PREFERRED, HardwarePreference.REQUIRED).map { hardware ->
                arrayOf(strongBox, hardware)
            }
        }
    }

    @Test fun generatedKeyHonorsPreferences() = runTest { checkPreferences(imported = false) }

    @Test fun importedKeyHonorsPreferences() = runTest { checkPreferences(imported = true) }

    private suspend fun checkPreferences(imported: Boolean) {
        assumeTrue("Native security-level readback requires API 31", Build.VERSION.SDK_INT >= 31)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val hasStrongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
        val fallbackLevel = probeWithoutStrongBox()
        val expectedLevel = if (strongBox != HardwarePreference.DISCOURAGED && hasStrongBox)
            KeyProperties.SECURITY_LEVEL_STRONGBOX else fallbackLevel
        val requiresMissingStrongBox = strongBox == HardwarePreference.REQUIRED && !hasStrongBox
        val requiresMissingHardware = hardware == HardwarePreference.REQUIRED &&
            expectedLevel == KeyProperties.SECURITY_LEVEL_SOFTWARE
        val alias = "strongbox-preference-${Uuid.random()}"
        val backend = AndroidSignumKeyBackend(context)
        val spec = KeySpec.Ec(EcCurve.P256)
        val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
        val policy = SignumKeyPolicy(hardware = hardware,
            platform = PlatformKeyConfiguration.AndroidKeystore(strongBox = strongBox))
        try {
            val result = runCatching {
                if (imported) backend.importPrivateKey(alias, nativeImportTestMaterial, spec, usages, policy)
                else backend.create(alias, spec, usages, policy)
            }
            if (requiresMissingStrongBox || requiresMissingHardware) {
                val failure = assertNotNull(result.exceptionOrNull(), "An unavailable REQUIRED policy must fail")
                assertTrue(generateSequence(failure) { it.cause }.any {
                    if (requiresMissingStrongBox) it is StrongBoxUnavailableException
                    else it is SignumKeyPolicyMismatchException
                }, "Unexpected rejection: $failure")
                assertFalse(androidKeyStore().containsAlias(alias), "Rejected creation/import must leave no key")
                return
            }
            val key = result.getOrThrow()
            val originalPublicKey = if (imported) nativeImportTestMaterial.toSpkiDer(spec) else key.publicKey
            val reopened = assertNotNull(AndroidSignumKeyBackend(context).load(alias, spec, usages, policy))
            for (handle in listOf(key, reopened)) {
                assertEquals(originalPublicKey, handle.publicKey)
                assertEquals(if (imported) KeyOrigin.IMPORTED else KeyOrigin.GENERATED, handle.origin)
                assertEquals(expectedLevel, nativeKeyInfo(alias).securityLevel)
                assertEquals(when (expectedLevel) {
                    KeyProperties.SECURITY_LEVEL_STRONGBOX -> KeySecurityLevel.STRONGBOX
                    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> KeySecurityLevel.TRUSTED_ENVIRONMENT
                    KeyProperties.SECURITY_LEVEL_SOFTWARE -> KeySecurityLevel.SOFTWARE
                    else -> error("Unqualified native security level: $expectedLevel")
                }, handle.securityLevel)
                assertEquals(if (expectedLevel == KeyProperties.SECURITY_LEVEL_SOFTWARE)
                    KeyProtectionLevel.SOFTWARE else KeyProtectionLevel.HARDWARE, handle.protectionLevel)
                assertNull(handle.privateKeyExporter)
                val challenge = Uuid.random().toString().encodeToByteArray()
                val signature = handle.sign(challenge, SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256))
                val publicKey = KeyFactory.getInstance("EC").generatePublic(
                    X509EncodedKeySpec(originalPublicKey.data.toByteArray()))
                fun verifies(data: ByteArray) = Signature.getInstance("SHA256withECDSA").run {
                    initVerify(publicKey)
                    update(data)
                    verify(EcdsaSignatureCodec.p1363ToDer(signature, 32))
                }
                assertTrue(verifies(challenge))
                assertFalse(verifies(challenge + byteArrayOf(1)))
            }
        } finally {
            backend.delete(alias)
            assertFalse(androidKeyStore().containsAlias(alias), "Temporary key cleanup failed")
        }
    }

    private fun nativeKeyInfo(alias: String): KeyInfo = KeyFactory.getInstance("EC", "AndroidKeyStore")
        .getKeySpec(androidKeyStore().getKey(alias, null) as PrivateKey, KeyInfo::class.java)

    /** Independent native probe: hardware preference must never determine the expected observation. */
    private fun probeWithoutStrongBox(): Int {
        val alias = "strongbox-fallback-probe-${Uuid.random()}"
        try {
            KeyPairGenerator.getInstance("EC", "AndroidKeyStore").apply {
                initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setIsStrongBoxBacked(false).build())
            }.generateKeyPair()
            return nativeKeyInfo(alias).securityLevel
        } finally {
            androidKeyStore().deleteEntry(alias)
            assertFalse(androidKeyStore().containsAlias(alias))
        }
    }
}
