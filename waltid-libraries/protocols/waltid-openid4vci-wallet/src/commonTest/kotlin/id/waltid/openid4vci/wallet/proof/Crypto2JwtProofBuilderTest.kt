package id.waltid.openid4vci.wallet.proof

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class Crypto2JwtProofBuilderTest {
    private val runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
    private val builder = JwtProofBuilder()

    @Test
    fun `P256 proof embeds public JWK and verifies`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P256), "proof-key")
        val proof = builder.buildProof(
            key = key,
            algorithm = JwsAlgorithm.ES256,
            audience = "https://issuer.example",
            nonce = "nonce",
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
            builder.buildJwtProof(
                key = key,
                algorithm = JwsAlgorithm.ES256,
                audience = "https://issuer.example",
                nonce = "nonce",
                keyId = "did:example:holder#key-1",
            ).jwt
        ).single()
        assertEquals(
            "did:example:holder#key-1",
            CompactJws.decodeUnverified(didToken).protectedHeader["kid"]?.jsonPrimitive?.content,
        )

        val publicJwk = assertNotNull(key.capabilities.publicKeyExporter).exportPublicKey() as EncodedKey.Jwk
        val thumbprintToken = assertNotNull(
            builder.buildJwtProof(
                key = key,
                algorithm = JwsAlgorithm.ES256,
                audience = "https://issuer.example",
                nonce = "nonce",
            ).jwt
        ).single()
        assertEquals(
            Jwk.sha256Thumbprint(publicJwk),
            CompactJws.decodeUnverified(thumbprintToken).protectedHeader["kid"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `incompatible explicit algorithm is rejected`() = runTest {
        val key = generate(KeySpec.Ec(EcCurve.P384), "p384")
        assertFailsWith<IllegalArgumentException> {
            builder.buildProof(key, JwsAlgorithm.ES256, "https://issuer.example", "nonce")
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
