package id.walt.verifier2.handlers.sessioncreation

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RequestObjectKidTest {

    @Test
    fun `legacy did key request kid uses the resolved verification method`() = runTest {
        DidService.minimalInit()
        val signingKey = JWKKey.generate(KeyType.Ed25519)
        val did = DidService.registerByKey("key", signingKey).did
        val expectedMethodId = DidService.resolveToCrypto2Keys(did).getOrThrow().single().id.value

        assertEquals(
            expectedMethodId,
            requestObjectKid("decentralized_identifier:$did", signingKey),
        )
    }

    @Test
    fun `crypto2 did jwk request kid uses the resolved verification method`() = runTest {
        DidService.minimalInit()
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val signingKey = runtime.generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("verifier-signing-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        )
        val did = DidService.registerByKey("jwk", signingKey).did
        val expectedMethodId = DidService.resolveToCrypto2Keys(did).getOrThrow().single().id.value

        assertEquals(
            expectedMethodId,
            requestObjectKid("decentralized_identifier:$did", signingKey),
        )
    }

    @Test
    fun `DID request kid rejects a signing key not represented by the DID`() = runTest {
        DidService.minimalInit()
        val didKey = JWKKey.generate(KeyType.secp256r1)
        val otherSigningKey = JWKKey.generate(KeyType.secp256r1)
        val did = DidService.registerByKey("jwk", didKey).did

        assertFailsWith<IllegalArgumentException> {
            requestObjectKid("decentralized_identifier:$did", otherSigningKey)
        }
    }

    @Test
    fun `decentralized identifier client id must contain a DID rather than a DID URL`() = runTest {
        DidService.minimalInit()
        val signingKey = JWKKey.generate(KeyType.secp256r1)
        val did = DidService.registerByKey("jwk", signingKey).did

        assertFailsWith<IllegalArgumentException> {
            requestObjectKid("decentralized_identifier:$did#unexpected", signingKey)
        }
    }

    @Test
    fun `non DID client identifiers preserve the signing key id`() = runTest {
        val signingKey = JWKKey.generate(KeyType.secp256r1)
        val keyId = signingKey.getKeyId()

        assertEquals(keyId, requestObjectKid("https://verifier.example", signingKey))
        assertEquals(keyId, requestObjectKid(null, signingKey))
    }
}
