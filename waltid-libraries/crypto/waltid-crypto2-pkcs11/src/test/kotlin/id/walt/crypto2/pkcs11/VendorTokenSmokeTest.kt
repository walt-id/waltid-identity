package id.walt.crypto2.pkcs11

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.serialization.StoredKeyCodec
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Smoke test against a real vendor token, run manually. No vendor API is used: this exercises exactly the same code
 * path as SoftHSM, which is the point - it is how a portability claim gets checked rather than asserted.
 *
 * Configure with either of the two addressing forms PKCS#11 offers:
 * - `WALTID_PKCS11_SLOT_ID`  - the `CK_SLOT_ID`, what an HSM operator normally has (a Luna partition slot, a
 *   tpm2-pkcs11 slot). Stable across restarts and unaffected by other tokens appearing.
 * - `WALTID_PKCS11_SLOT_LIST_INDEX` - the position in the library's slot list. Convenient for a single-token
 *   library; it can move if the set of visible tokens changes.
 *
 * Example, Thales Luna:
 * ```
 * WALTID_PKCS11_LIBRARY=/usr/safenet/lunaclient/lib/libCryptoki2_64.so \
 * WALTID_PKCS11_SLOT_ID=0 WALTID_PKCS11_PIN=... \
 *   ./gradlew :waltid-libraries:crypto:waltid-crypto2-pkcs11:test --tests '*VendorTokenSmokeTest*'
 * ```
 * Example, TPM 2.0 via tpm2-pkcs11:
 * ```
 * WALTID_PKCS11_LIBRARY=/usr/lib/x86_64-linux-gnu/libtpm2_pkcs11.so \
 * WALTID_PKCS11_SLOT_ID=1 WALTID_PKCS11_PIN=... ./gradlew ...
 * ```
 *
 * The earlier variables named `WALTID_LUNA_PKCS11_SLOT` were read as a slot-*list index* while being named like a
 * slot ID, so a Luna operator supplying a real slot ID would silently have targeted a different token.
 */
class VendorTokenSmokeTest {
    @Test
    fun `vendor token generates signs restores and deletes`() = runTest {
        val library = System.getenv("WALTID_PKCS11_LIBRARY")
        val pin = System.getenv("WALTID_PKCS11_PIN")
        val slotId = System.getenv("WALTID_PKCS11_SLOT_ID")?.toLongOrNull()
        val slotListIndex = System.getenv("WALTID_PKCS11_SLOT_LIST_INDEX")?.toIntOrNull()
        assumeTrue(
            library != null && pin != null && (slotId != null || slotListIndex != null),
            "PKCS11 vendor token environment is not configured",
        )
        // Pkcs11Options enforces exactly one addressing form; prefer the slot ID when both are given.
        val effectiveSlotListIndex = slotListIndex.takeIf { slotId == null }

        val provider = Pkcs11KeyProvider(Pkcs11PinResolver { Pkcs11Pin(requireNotNull(pin).toCharArray()) })
        val runtime = CryptoRuntime(emptyList(), listOf(provider))
        val alias = "waltid-smoke-${UUID.randomUUID()}"
        val options = Pkcs11Options(
            libraryPath = requireNotNull(library),
            pinReference = "vendor-smoke-pin",
            slotId = slotId,
            slotListIndex = effectiveSlotListIndex,
            alias = alias,
        )

        val generated = runtime.generateManagedKey(
            Pkcs11KeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId(alias),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options.encode(),
            ),
        )
        // Whatever the token reports must be non-empty and must actually work; a vendor that lacks the combined
        // CKM_ECDSA_SHA256 mechanism exercises the raw CKM_ECDSA fallback here.
        assertTrue(generated.capabilities.signatureAlgorithms.isNotEmpty())

        val restored = runtime.restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(generated.storedKey))
        )
        generated.capabilities.signatureAlgorithms.forEach { algorithm ->
            val signature = assertNotNull(restored.capabilities.signer).sign("smoke".encodeToByteArray(), algorithm)
            assertTrue(
                assertNotNull(restored.capabilities.verifier).verify("smoke".encodeToByteArray(), signature, algorithm),
                "advertised algorithm did not work on this token: $algorithm",
            )
        }

        // Attaching the same key by alias must yield an equivalent descriptor - this is the workflow for a key an
        // operator provisioned rather than one generated here.
        val attached = provider.storedKeyForExisting(
            id = KeyId(alias),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            options = options,
        )
        assertEquals(generated.storedKey.spec, attached.spec)
        assertEquals(generated.storedKey.publicKey, attached.publicKey)

        assertEquals(KeyDeletionResult.Deleted, assertNotNull(restored.capabilities.deleter).delete())
        provider.close()
    }
}
