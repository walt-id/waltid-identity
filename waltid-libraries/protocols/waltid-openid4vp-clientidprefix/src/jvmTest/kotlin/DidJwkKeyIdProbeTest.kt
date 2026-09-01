import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Pins the `kid` a verifier must use for a `decentralized_identifier` Client Identifier.
 *
 * `DecentralizedIdentifier` selects the verification key with
 * `resolveToCrypto2Keys(did).find { it.id.value == kid }`, and for a `did:jwk` that identifier is the
 * DID URL of the verification method (`did:jwk:...#0`) - which is exactly what OID4VP 1.0 Section
 * 5.9.3 requires, so the prefix is interoperable. It is *not* the key's RFC 7638 thumbprint, which
 * `resolveToKeys(...).getKeyId()` returns; signing with that is rejected as
 * "Key ID '...' from JWS not found in DID document".
 */
class DidJwkKeyIdProbeTest {
    @Test
    fun `a did jwk verification method is identified by its DID URL, not its thumbprint`() = runTest {
        DidService.minimalInit()
        val key = JWKKey.generate(KeyType.secp256r1)
        val did = "did:jwk:" + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(key.getPublicKey().exportJWK().encodeToByteArray())
        val verificationMethodId = DidService.resolveToCrypto2Keys(did).getOrThrow().single().id.value
        assertEquals("$did#0", verificationMethodId)
        assertNotEquals(
            DidService.resolveToKeys(did).getOrThrow().single().getKeyId(),
            verificationMethodId,
            "the thumbprint must not be mistaken for the verification method id",
        )
    }
}
