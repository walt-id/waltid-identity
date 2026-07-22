package id.walt.openid4vci.dpop

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.ShaUtils
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class DefaultDPoPProofVerifierTest {

    @Test
    fun `verifies a token endpoint proof and returns its JWK thumbprint`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val proof = createProof(key = key, targetUri = TOKEN_ENDPOINT_URI)

        val verified = verifier.verify(
            DPoPProofVerificationRequest(
                proofJwt = proof,
                method = "POST",
                targetUri = TOKEN_ENDPOINT_URI,
            ),
        )

        assertEquals(key.getPublicKey().getThumbprint(), verified.jwkThumbprint)
    }

    @Test
    fun `verifies ath when an access token is presented`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val proof = createProof(
            key = key,
            targetUri = CREDENTIAL_ENDPOINT_URI,
            accessToken = ACCESS_TOKEN,
        )

        verifier.verify(
            DPoPProofVerificationRequest(
                proofJwt = proof,
                method = "POST",
                targetUri = CREDENTIAL_ENDPOINT_URI,
                accessToken = ACCESS_TOKEN,
            ),
        )
    }

    @Test
    fun `rejects a proof created for another access token`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val proof = createProof(
            key = key,
            targetUri = CREDENTIAL_ENDPOINT_URI,
            accessToken = "another-access-token",
        )

        assertFailsWith<IllegalArgumentException> {
            verifier.verify(
                DPoPProofVerificationRequest(
                    proofJwt = proof,
                    method = "POST",
                    targetUri = CREDENTIAL_ENDPOINT_URI,
                    accessToken = ACCESS_TOKEN,
                ),
            )
        }
    }

    @Test
    fun `rejects a proof created for another endpoint`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val proof = createProof(key = key, targetUri = TOKEN_ENDPOINT_URI)

        assertFailsWith<IllegalArgumentException> {
            verifier.verify(
                DPoPProofVerificationRequest(
                    proofJwt = proof,
                    method = "POST",
                    targetUri = CREDENTIAL_ENDPOINT_URI,
                ),
            )
        }
    }

    @Test
    fun `rejects non-string DPoP claims`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val proof = createProof(
            key = key,
            targetUri = TOKEN_ENDPOINT_URI,
            method = JsonPrimitive(123),
        )

        assertFailsWith<IllegalArgumentException> {
            verifier.verify(
                DPoPProofVerificationRequest(
                    proofJwt = proof,
                    method = "POST",
                    targetUri = TOKEN_ENDPOINT_URI,
                ),
            )
        }
    }

    @Test
    fun `rejects non-string optional JWK members`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val malformedJwk = buildJsonObject {
            key.getPublicKey().exportJWKObject().forEach { (name, value) -> put(name, value) }
            put(JwtHeaderParams.ALGORITHM, 123)
        }
        val proof = createProofWithJwkHeader(key, malformedJwk)

        assertFailsWith<IllegalArgumentException> {
            verifier.verify(
                DPoPProofVerificationRequest(
                    proofJwt = proof,
                    method = "POST",
                    targetUri = TOKEN_ENDPOINT_URI,
                ),
            )
        }
    }

    @Test
    fun `rejects non-array JWK key operations`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val malformedJwk = buildJsonObject {
            key.getPublicKey().exportJWKObject().forEach { (name, value) -> put(name, value) }
            put(JWK_KEY_OPERATIONS, "verify")
        }
        val proof = createProofWithJwkHeader(key, malformedJwk)

        assertFailsWith<IllegalArgumentException> {
            verifier.verify(
                DPoPProofVerificationRequest(
                    proofJwt = proof,
                    method = "POST",
                    targetUri = TOKEN_ENDPOINT_URI,
                ),
            )
        }
    }

    private suspend fun createProof(
        key: JWKKey,
        targetUri: String,
        accessToken: String? = null,
        method: JsonPrimitive = JsonPrimitive("POST"),
    ): String = key.signJws(
        buildJsonObject {
            put(JwtPayloadClaims.JWT_ID, "proof-id")
            put(DPoPConstants.HTTP_METHOD_CLAIM, method)
            put(DPoPConstants.HTTP_URI_CLAIM, targetUri)
            put(JwtPayloadClaims.ISSUED_AT, NOW.epochSeconds)
            accessToken?.let {
                put(DPoPConstants.ACCESS_TOKEN_HASH_CLAIM, ShaUtils.calculateSha256Base64Url(it))
            }
        }.toString().encodeToByteArray(),
        headers = mapOf(
            JwtHeaderParams.TYPE to JsonPrimitive(DPoPConstants.JWT_TYPE),
            JwtHeaderParams.JSON_WEB_KEY to key.getPublicKey().exportJWKObject(),
        ),
    )

    private suspend fun createProofWithJwkHeader(key: JWKKey, jwk: JsonObject): String {
        val validProof = createProof(key, TOKEN_ENDPOINT_URI)
        val parts = validProof.split('.')
        check(parts.size == 3) { "Expected a compact JWS" }
        val header = buildJsonObject {
            put(JwtHeaderParams.ALGORITHM, DPoPConstants.ES256)
            put(JwtHeaderParams.TYPE, DPoPConstants.JWT_TYPE)
            put(JwtHeaderParams.JSON_WEB_KEY, jwk)
        }.toString().encodeToByteArray().encodeToBase64Url()
        return "$header.${parts[1]}.${parts[2]}"
    }

    private companion object {
        val NOW = Instant.fromEpochSeconds(1_800_000_000)
        val verifier = DefaultDPoPProofVerifier(now = { NOW })
        const val TOKEN_ENDPOINT_URI = "https://issuer.example/token"
        const val CREDENTIAL_ENDPOINT_URI = "https://issuer.example/credential"
        const val ACCESS_TOKEN = "client-two-access-token"
        const val JWK_KEY_OPERATIONS = "key_ops"
    }
}
