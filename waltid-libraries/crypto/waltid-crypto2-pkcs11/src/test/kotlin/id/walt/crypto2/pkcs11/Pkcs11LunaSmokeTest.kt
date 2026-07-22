package id.walt.crypto2.pkcs11

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Pkcs11LunaSmokeTest {
    @Test
    fun `Luna generic PKCS11 sign restore and delete`() = runTest {
        val library = System.getenv("WALTID_LUNA_PKCS11_LIBRARY")
        val pin = System.getenv("WALTID_LUNA_PKCS11_PIN")
        val slot = System.getenv("WALTID_LUNA_PKCS11_SLOT")?.toIntOrNull()
        assumeTrue(library != null && pin != null && slot != null, "Luna PKCS11 environment is not configured")
        val provider = Pkcs11KeyProvider(Pkcs11PinResolver { Pkcs11Pin(requireNotNull(pin).toCharArray()) })
        val runtime = CryptoRuntime(emptyList(), listOf(provider))
        val alias = "waltid-luna-smoke-${UUID.randomUUID()}"
        val generated = runtime.generateManagedKey(
            Pkcs11KeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId(alias),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = Pkcs11Options(
                    libraryPath = requireNotNull(library),
                    slotListIndex = requireNotNull(slot),
                    pinReference = "luna-smoke-pin",
                    alias = alias,
                ).encode(),
            ),
        )
        val restored = runtime.restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(generated.storedKey))
        )
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        val signature = assertNotNull(restored.capabilities.signer).sign("luna-smoke".encodeToByteArray(), algorithm)
        assertTrue(assertNotNull(restored.capabilities.verifier).verify("luna-smoke".encodeToByteArray(), signature, algorithm))
        assertEquals(KeyDeletionResult.Deleted, assertNotNull(restored.capabilities.deleter).delete())
    }
}
