package id.waltid.openid4vci.wallet.proof

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.*

class Crypto2JwtProofBuilderTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val builder = JwtProofBuilder()

    @Test
    fun `P256 proof embeds public JWK and verifies`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256), "proof-key")
        val proof = builder.buildProof(
            key = key,
            algorithm = JwsAlgorithm.ES256,
            audience = "https://issuer.example",
            nonce = "nonce",
            binding = ProofKeyBinding.Jwk,
        )
        val token = assertNotNull(proof.jwt).single()
        val verified = CompactJws.verify(token, key, JwsAlgorithm.ES256)
        val payload = Json.parseToJsonElement(verified.payload.decodeToString()) as JsonObject

        assertEquals("openid4vci-proof+jwt", verified.protectedHeader["typ"]?.jsonPrimitive?.content)
        assertTrue(verified.protectedHeader["jwk"] is JsonObject)
        assertEquals("https://issuer.example", payload["aud"]?.jsonPrimitive?.content)
        assertEquals("nonce", payload["nonce"]?.jsonPrimitive?.content)
    }

    @Test
    fun `explicit DID kid and thumbprint binding are preserved`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256), "proof-key")
        val didToken = assertNotNull(
            builder.buildProof(
                key = key,
                algorithm = JwsAlgorithm.ES256,
                audience = "https://issuer.example",
                nonce = "nonce",
                binding = ProofKeyBinding.KeyId("did:example:holder#key-1"),
            ).jwt
        ).single()
        assertEquals(
            "did:example:holder#key-1",
            CompactJws.decodeUnverified(didToken).protectedHeader["kid"]?.jsonPrimitive?.content,
        )

        val publicJwk = assertNotNull(key.capabilities.publicKeyExporter).exportPublicKey() as EncodedKey.Jwk
        val thumbprintToken = assertNotNull(
            builder.buildProof(
                key = key,
                algorithm = JwsAlgorithm.ES256,
                audience = "https://issuer.example",
                nonce = "nonce",
                binding = ProofKeyBinding.JwkThumbprint,
            ).jwt
        ).single()
        assertEquals(
            Jwk.sha256Thumbprint(publicJwk),
            CompactJws.decodeUnverified(thumbprintToken).protectedHeader["kid"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `absent nonce omits the claim and blank nonce is rejected`() = runTest {
        // Issuers without a Nonce Endpoint (OpenID4VCI 1.0 §7.2.1.1) produce no c_nonce.
        val key = generate(KeySpec.Ec(EcCurve.P256), "no-nonce-key")
        val token = assertNotNull(
            builder.buildProof(
                key = key,
                algorithm = JwsAlgorithm.ES256,
                audience = "https://issuer.example",
                nonce = null,
                binding = ProofKeyBinding.Jwk,
            ).jwt
        ).single()
        val payload = Json.parseToJsonElement(
            CompactJws.verify(token, key, JwsAlgorithm.ES256).payload.decodeToString()
        ) as JsonObject

        assertNull(payload["nonce"])
        assertEquals("https://issuer.example", payload["aud"]?.jsonPrimitive?.content)

        assertFailsWith<IllegalArgumentException> {
            builder.buildProof(key, JwsAlgorithm.ES256, "https://issuer.example", "  ", ProofKeyBinding.Jwk)
        }
    }

    @Test
    fun `incompatible explicit algorithm is rejected`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P384), "p384")
        assertFailsWith<IllegalArgumentException> {
            builder.buildProof(key, JwsAlgorithm.ES256, "https://issuer.example", "nonce", ProofKeyBinding.Jwk)
        }
    }

    private suspend fun generate(spec: KeySpec, id: String): Key = runtime.generateSoftwareKey(
        GenerateSoftwareKeyRequest(
            id = KeyId(id),
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
        )
    )
}
