package id.walt.openid4vci.proofs

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.tokens.jwt.JwtPayloadClaims
import id.walt.openid4vci.tokens.jwt.JwtSigningKeyResolver
import id.walt.openid4vci.tokens.jwt.JwtVerificationKeyResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class JwtCredentialNonceServiceTest {

    @Test
    fun `issues a signed nonce that remains valid until expiry`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val service = service(key, NOW)

        val first = service.issue(BINDING)
        val second = service.issue(BINDING)

        assertNotEquals(first.nonce, second.nonce)
        assertEquals(CredentialNonceValidationResult.VALID, service.validate(first.nonce, BINDING))
        assertEquals(CredentialNonceValidationResult.VALID, service.validate(first.nonce, BINDING))
    }

    @Test
    fun `rejects a nonce with a tampered signature`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val service = service(key, NOW)
        val nonce = service.issue(BINDING).nonce

        assertEquals(CredentialNonceValidationResult.INVALID, service.validate(tamperSignature(nonce), BINDING))
    }

    @Test
    fun `rejects nonce bindings that do not match`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val service = service(key, NOW)
        val nonce = service.issue(BINDING).nonce

        assertEquals(
            CredentialNonceValidationResult.INVALID,
            service.validate(nonce, BINDING.copy(credentialEndpoint = "https://issuer.example/other")),
        )
        assertEquals(
            CredentialNonceValidationResult.INVALID,
            service.validate(nonce, BINDING.copy(credentialIssuer = "https://other-issuer.example")),
        )
        assertEquals(
            CredentialNonceValidationResult.INVALID,
            service.validate(nonce, BINDING.copy(nonceEndpoint = "https://issuer.example/other-nonce")),
        )
    }

    @Test
    fun `rejects expired nonce`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val nonce = service(key, NOW).issue(BINDING).nonce

        assertEquals(
            CredentialNonceValidationResult.INVALID,
            service(key, NOW + 301.seconds).validate(nonce, BINDING),
        )
    }

    @Test
    fun `rejects nonce without a random JWT ID`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val nonce = key.signJws(
            plaintext = buildJsonObject {
                put(JwtPayloadClaims.ISSUER, BINDING.credentialIssuer)
                put(JwtPayloadClaims.AUDIENCE, BINDING.credentialEndpoint)
                put(JwtPayloadClaims.ISSUED_AT, NOW.epochSeconds)
                put(JwtPayloadClaims.EXPIRATION, (NOW + 300.seconds).epochSeconds)
                put("source_endpoint", BINDING.nonceEndpoint)
            }.toString().encodeToByteArray(),
        )

        assertEquals(CredentialNonceValidationResult.INVALID, service(key, NOW).validate(nonce, BINDING))
    }

    private fun service(key: JWKKey, currentTime: Instant) = JwtCredentialNonceService(
        signingKeyResolver = JwtSigningKeyResolver { key },
        verificationKeyResolver = JwtVerificationKeyResolver { key.getPublicKey() },
        nonceLifetime = 300.seconds,
        now = { currentTime },
    )

    private fun tamperSignature(jwt: String): String {
        val parts = jwt.split(".")
        check(parts.size == 3)
        val replacement = if (parts[2].first() == 'A') 'B' else 'A'
        return "${parts[0]}.${parts[1]}.$replacement${parts[2].drop(1)}"
    }

    private companion object {
        val BINDING = CredentialNonceBinding(
            credentialIssuer = "https://issuer.example",
            credentialEndpoint = "https://issuer.example/credential",
            nonceEndpoint = "https://issuer.example/nonce",
        )
        val NOW = Instant.fromEpochSeconds(1_800_000_000)
    }
}
