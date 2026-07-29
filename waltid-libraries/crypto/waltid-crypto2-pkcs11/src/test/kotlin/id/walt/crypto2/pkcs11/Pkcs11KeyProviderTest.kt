package id.walt.crypto2.pkcs11

import id.walt.cose.*
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.test.*

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
    fun `RSA key signs and verifies through JOSE and COSE`() = runTest {
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

        // Deliberately no "wrong PIN is rejected" assertion here. PKCS#11 login state is per process per slot, so
        // once this token is logged in, C_Login returns CKR_USER_ALREADY_LOGGED_IN and a wrong PIN is accepted -
        // verified against SoftHSM. A wrong PIN is only detected on the first login to a token within a process,
        // which cannot be asserted in a suite that shares the token. See Pkcs11Sessions.
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

    @Test
    fun `generated private keys are sensitive non-extractable and signing-only on the token`() = runTest {
        assumeTrue(library != null, "SoftHSM is not installed")
        val alias = "attrs-${UUID.randomUUID()}"
        val generated = runtime().generateManagedKey(
            Pkcs11KeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("attr-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options(alias).encode(),
            ),
        )

        // The single security property this module exists to provide, asserted against the token rather than assumed
        // from the config we wrote. A sensitive, non-extractable key must refuse to hand over its material.
        val session = Pkcs11Sessions.acquire(options(alias), pinResolver())
        val privateKey = assertNotNull(session.keyStore.getKey(alias, null))
        assertNull(privateKey.encoded, "a CKA_SENSITIVE/CKA_EXTRACTABLE=false key must not expose its encoding")

        // CKA_DECRYPT must be false, or the token key stays usable as an RSAES-PKCS1-v1_5 oracle by any other
        // PKCS#11 client holding the PIN - which is precisely what this provider refuses to expose itself.
        assertFails {
            javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding", session.provider)
                .init(javax.crypto.Cipher.DECRYPT_MODE, privateKey)
        }

        assertEquals(KeyDeletionResult.Deleted, assertNotNull(generated.capabilities.deleter).delete())
    }

    @Test
    fun `deleting a key removes every token object it created`() = runTest {
        assumeTrue(library != null, "SoftHSM is not installed")
        val alias = "cleanup-${UUID.randomUUID()}"
        val provider = Pkcs11KeyProvider(pinResolver())
        val session = Pkcs11Sessions.acquire(options(alias), pinResolver())
        val before = session.keyStore.aliases().toList().size

        val generated = CryptoRuntime(emptyList(), listOf(provider)).generateManagedKey(
            Pkcs11KeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("cleanup-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options(alias).encode(),
            ),
        )
        assertEquals(KeyDeletionResult.Deleted, assertNotNull(generated.capabilities.deleter).delete())

        // Public keys are generated as session objects precisely so that nothing is orphaned here. Previously the
        // config forced CKA_TOKEN=true on public keys, which P11KeyStore.deleteEntry does not destroy.
        val after = session.keyStore.aliases().toList().size
        assertEquals(before, after, "delete must not leave objects behind on the token")

        // Deleting an alias that does not exist must not report success.
        assertFails { assertNotNull(generated.capabilities.deleter).delete() }
        provider.close()
    }

    @Test
    fun `advertised algorithms come from the token and all of them work`() = runTest {
        assumeTrue(library != null, "SoftHSM is not installed")
        val alias = "probe-${UUID.randomUUID()}"
        val generated = runtime().generateManagedKey(
            Pkcs11KeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("probe-key"),
                spec = KeySpec.Rsa(2048),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options(alias).encode(),
            ),
        )

        // Same invariant as the software provider's algorithm matrix: whatever is advertised has to work, otherwise
        // callers select an algorithm and fail at runtime instead of at negotiation.
        val advertised = generated.capabilities.signatureAlgorithms
        assertTrue(advertised.isNotEmpty())
        advertised.forEach { algorithm ->
            val signature = assertNotNull(generated.capabilities.signer).sign("probe".encodeToByteArray(), algorithm)
            assertTrue(
                assertNotNull(generated.capabilities.verifier)
                    .verify("probe".encodeToByteArray(), signature, algorithm),
                "advertised algorithm did not work: $algorithm",
            )
            assertTrue(generated.capabilities.supportsSignatureAlgorithm(algorithm))
        }

        assertEquals(KeyDeletionResult.Deleted, assertNotNull(generated.capabilities.deleter).delete())
    }

    @Test
    fun `an existing token key can be attached by alias`() = runTest {
        assumeTrue(library != null, "SoftHSM is not installed")
        val alias = "attach-${UUID.randomUUID()}"
        val provider = Pkcs11KeyProvider(pinResolver())
        val generated = CryptoRuntime(emptyList(), listOf(provider)).generateManagedKey(
            Pkcs11KeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("attach-key"),
                spec = KeySpec.Ec(EcCurve.P384),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options(alias).encode(),
            ),
        )

        // The HSM workflow: the key already exists and only its alias is known. The specification is read from the
        // token, so it cannot be misdeclared by the caller.
        val attached = provider.storedKeyForExisting(
            id = KeyId("attached-key"),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            options = options(alias),
        )
        assertEquals(KeySpec.Ec(EcCurve.P384), attached.spec)
        assertEquals(generated.storedKey.publicKey, attached.publicKey)

        val restored = CryptoRuntime(emptyList(), listOf(provider)).restore(attached)
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_384, EcdsaSignatureEncoding.IEEE_P1363)
        val signature = assertNotNull(restored.capabilities.signer).sign("attached".encodeToByteArray(), algorithm)
        assertEquals(96, signature.size)
        assertTrue(assertNotNull(restored.capabilities.verifier).verify("attached".encodeToByteArray(), signature, algorithm))

        assertFails("an unknown alias must not be attachable") {
            provider.storedKeyForExisting(
                id = KeyId("missing"),
                usages = setOf(KeyUsage.SIGN),
                options = options("does-not-exist-${UUID.randomUUID()}"),
            )
        }

        assertEquals(KeyDeletionResult.Deleted, assertNotNull(restored.capabilities.deleter).delete())
        provider.close()
    }

    @Test
    fun `a malformed signature verifies as false instead of throwing`() = runTest {
        assumeTrue(library != null, "SoftHSM is not installed")
        val alias = "malformed-${UUID.randomUUID()}"
        val generated = runtime().generateManagedKey(
            Pkcs11KeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("malformed-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options(alias).encode(),
            ),
        )
        val verifier = assertNotNull(generated.capabilities.verifier)
        val p1363 = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.IEEE_P1363)

        // Signatures are untrusted input; a wrong length or undecodable DER is an invalid signature, not an error
        // every caller has to catch.
        assertFalse(verifier.verify("x".encodeToByteArray(), ByteArray(7), p1363))
        assertFalse(verifier.verify("x".encodeToByteArray(), ByteArray(64), p1363))
        assertFalse(
            verifier.verify(
                "x".encodeToByteArray(),
                byteArrayOf(0x30, 0x05, 0x02, 0x01, 0x01),
                SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER),
            )
        )

        assertEquals(KeyDeletionResult.Deleted, assertNotNull(generated.capabilities.deleter).delete())
    }

    @Test
    fun `exactly one token addressing form is required`() {
        val library = "/usr/lib/softhsm/libsofthsm2.so"
        // Neither: the token would be ambiguous. Both: the two could disagree.
        assertFails { Pkcs11Options(libraryPath = library, pinReference = "pin") }
        assertFails { Pkcs11Options(libraryPath = library, pinReference = "pin", slotId = 1, slotListIndex = 0) }

        // A slot ID is what an HSM operator has; a slot-list index suits a single-token library.
        assertEquals(4L, Pkcs11Options(libraryPath = library, pinReference = "pin", slotId = 4).slotId)
        assertEquals(2, Pkcs11Options(libraryPath = library, pinReference = "pin", slotListIndex = 2).slotListIndex)

        // Vendor escape-hatch lines must not be able to inject unrelated provider directives.
        assertFails {
            Pkcs11Options(
                libraryPath = library,
                pinReference = "pin",
                slotId = 0,
                providerConfigurationLines = listOf("attributes(*,*,*) = {\n  CKA_EXTRACTABLE = true\n}"),
            )
        }
    }

    @Test
    fun `a destroyed PIN cannot be used again`() {
        val pin = Pkcs11Pin("123456".toCharArray())
        assertFalse(pin.isDestroyed)
        pin.destroy()
        assertTrue(pin.isDestroyed)
        assertFails { pin.copy() }
        assertEquals("Pkcs11Pin(***)", pin.toString())
    }

    private fun pinResolver(pin: String = "123456") = Pkcs11PinResolver { reference ->
        assertEquals("softhsm-pin", reference)
        Pkcs11Pin(pin.toCharArray())
    }

    private fun options(alias: String) = Pkcs11Options(
        libraryPath = requireNotNull(library),
        pinReference = "softhsm-pin",
        slotListIndex = 0,
        alias = alias,
    )

    private fun runtime(pin: String = "123456") = CryptoRuntime(
        softwareProviders = emptyList(),
        managedProviders = listOf(Pkcs11KeyProvider(pinResolver(pin))),
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
