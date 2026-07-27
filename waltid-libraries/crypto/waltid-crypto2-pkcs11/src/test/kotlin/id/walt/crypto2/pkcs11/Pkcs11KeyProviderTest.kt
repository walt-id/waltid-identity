package id.walt.crypto2.pkcs11

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.createAndSign
import id.walt.cose.verify
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Pkcs11KeyProviderTest {
    private val library = System.getProperty("waltid.test.softhsm.library")

    @Test
    fun `EC key signs verifies survives restart and deletes`() = runTest {
        assumeTrue(library != null, "SoftHSM is not installed")
        val options = options("ec-${UUID.randomUUID()}")
        val generated = runtime().generateManagedKey(
            Pkcs11KeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("ec-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options.encode(),
            ),
        )
        val providerData = generated.storedKey.providerData.toByteArray().decodeToString()
        assertTrue("softhsm-pin" in providerData)
        assertFalse("123456" in providerData)
        val stored = StoredKeyCodec.encodeToByteArray(generated.storedKey)
        val restored = runtime().restore(StoredKeyCodec.decodeFromByteArray(stored))
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.IEEE_P1363)
        val signature = assertNotNull(restored.capabilities.signer).sign("message".encodeToByteArray(), algorithm)
        assertEquals(64, signature.size)
        assertTrue(assertNotNull(restored.capabilities.verifier).verify("message".encodeToByteArray(), signature, algorithm))
        val jws = CompactJws.sign("jose".encodeToByteArray(), restored, JwsAlgorithm.ES256)
        assertEquals("jose", CompactJws.verify(jws, restored, JwsAlgorithm.ES256).payload.decodeToString())
        val cose = CoseSign1.createAndSign(
            CoseHeaders(algorithm = Cose.Algorithm.ES256),
            payload = "cose".encodeToByteArray(),
            key = restored,
        )
        assertTrue(cose.verify(restored, Cose.Algorithm.ES256))
        assertEquals(KeyDeletionResult.Deleted, assertNotNull(restored.capabilities.deleter).delete())
        assertFails { runtime().restore(StoredKeyCodec.decodeFromByteArray(stored)) }
    }

    @Test
    fun `RSA key signs verifies and rejects wrong PIN`() = runTest {
        assumeTrue(library != null, "SoftHSM is not installed")
        val generated = runtime().generateManagedKey(
            Pkcs11KeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("rsa-key"),
                spec = KeySpec.Rsa(2048),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options("rsa-${UUID.randomUUID()}").encode(),
            ),
        )
        val pss = SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_256, saltLengthBytes = 32)
        val signature = assertNotNull(generated.capabilities.signer).sign("message".encodeToByteArray(), pss)
        assertTrue(assertNotNull(generated.capabilities.verifier).verify("message".encodeToByteArray(), signature, pss))
        val jws = CompactJws.sign("jose-rsa".encodeToByteArray(), generated, JwsAlgorithm.PS256)
        assertEquals("jose-rsa", CompactJws.verify(jws, generated, JwsAlgorithm.PS256).payload.decodeToString())
        val cose = CoseSign1.createAndSign(
            CoseHeaders(algorithm = Cose.Algorithm.PS256),
            payload = "cose-rsa".encodeToByteArray(),
            key = generated,
        )
        assertTrue(cose.verify(generated, Cose.Algorithm.PS256))

        val stored = generated.storedKey
        assertFails {
            runtime(pin = "wrong-pin").restore(stored)
        }
        assertEquals(KeyDeletionResult.Deleted, assertNotNull(generated.capabilities.deleter).delete())
    }

    @Test
    fun `RSA key rejects encryption and wrapping usages`() = runTest {
        assumeTrue(library != null, "SoftHSM is not installed")
        // JCA exposes no RSA-OAEP for PKCS#11 tokens and RSAES-PKCS1-v1_5 decryption is a Bleichenbacher oracle,
        // so encryption and key wrapping must not be offered at all
        setOf(KeyUsage.ENCRYPT, KeyUsage.DECRYPT, KeyUsage.WRAP, KeyUsage.UNWRAP).forEach { usage ->
            assertFails {
                runtime().generateManagedKey(
                    Pkcs11KeyProvider.ID,
                    GenerateManagedKeyRequest(
                        id = KeyId("rsa-encrypt-key"),
                        spec = KeySpec.Rsa(2048),
                        usages = setOf(KeyUsage.SIGN, usage),
                        providerOptions = options("rsa-reject-${UUID.randomUUID()}").encode(),
                    ),
                )
            }
        }
    }

    private fun options(alias: String) = Pkcs11Options(
        libraryPath = requireNotNull(library),
        slotListIndex = 0,
        pinReference = "softhsm-pin",
        alias = alias,
    )

    private fun runtime(pin: String = "123456") = CryptoRuntime(
        softwareProviders = emptyList(),
        managedProviders = listOf(
            Pkcs11KeyProvider(Pkcs11PinResolver { reference ->
                assertEquals("softhsm-pin", reference)
                Pkcs11Pin(pin.toCharArray())
            })
        ),
    )

    companion object {
        @BeforeAll
        @JvmStatic
        fun prepareSoftHsm() {
            val executable = System.getProperty("waltid.test.softhsm.executable") ?: return
            val configPath = System.getProperty("waltid.test.softhsm.config") ?: return
            val config = Path.of(configPath)
            val root = requireNotNull(config.parent)
            val tokens = root.resolve("tokens")
            Files.createDirectories(tokens)
            Files.writeString(
                config,
                """
                directories.tokendir = $tokens
                objectstore.backend = file
                log.level = ERROR
                slots.removable = false
                """.trimIndent()
            )
            val marker = root.resolve("initialized")
            if (Files.exists(marker)) return
            val process = ProcessBuilder(
                executable,
                "--init-token",
                "--free",
                "--label",
                "crypto2-test",
                "--so-pin",
                "12345678",
                "--pin",
                "123456",
            ).apply {
                environment()["SOFTHSM2_CONF"] = config.toString()
                redirectErrorStream(true)
            }.start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            check(process.waitFor() == 0) { "SoftHSM initialization failed: $output" }
            Files.writeString(marker, "initialized")
        }
    }
}
