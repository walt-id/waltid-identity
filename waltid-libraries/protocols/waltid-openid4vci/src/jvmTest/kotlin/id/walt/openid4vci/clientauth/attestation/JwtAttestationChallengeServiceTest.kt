@file:Suppress("DEPRECATION")

package id.walt.openid4vci.clientauth.attestation

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.clientauth.attestation.challenge.JwtAttestationChallengeService
import id.walt.openid4vci.proofs.*
import id.walt.openid4vci.tokens.jwt.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class JwtAttestationChallengeServiceTest {
    private val issuer = "https://issuer.example"
    private val now = Instant.fromEpochSeconds(1_800_000_000)

    private fun service(key: JWKKey, time: Instant = now) = JwtAttestationChallengeService(
        JwtSigningKeyResolver { key }, JwtVerificationKeyResolver { key.getPublicKey() },
        now = { time },
    )

    @Test
    fun `signed challenges are unique reusable and expire at the boundary`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val challenge = service(key).issue(issuer)
        assertNotEquals(challenge, service(key).issue(issuer))
        repeat(2) { assertTrue(service(key).validate(challenge, issuer)) }
        assertTrue(service(key, now + 299.seconds).validate(challenge, issuer))
        assertFalse(service(key, now + 300.seconds).validate(challenge, issuer))
        assertFalse(service(key).validate(challenge, "https://another.example"))
        val parts = challenge.split('.')
        val tampered = "${parts[0]}.${parts[1]}.${if (parts[2][0] == 'A') 'B' else 'A'}${parts[2].drop(1)}"
        assertFalse(service(key).validate(tampered, issuer))
        assertFalse(service(JWKKey.generate(KeyType.secp256r1)).validate(challenge, issuer))
        for (invalid in listOf("", "garbage", "a.b.c")) assertFalse(service(key).validate(invalid, issuer))
    }

    @Test
    fun `credential nonces and other signed tokens cannot be substituted for challenges`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val challengeService = service(key)
        val nonceService = JwtCredentialNonceService(
            JwtSigningKeyResolver { key }, JwtVerificationKeyResolver { key.getPublicKey() }, now = { now },
        )
        val challenge = challengeService.issue(issuer)
        assertFailsWith<IllegalArgumentException> {
            id.walt.openid4vci.tokens.jwt.access.JwtAccessTokenVerifier(JwtVerificationKeyResolver { key })
                .verify(challenge, issuer, issuer)
        }
        assertFailsWith<IllegalArgumentException> {
            id.walt.openid4vci.tokens.jwt.refresh.JwtRefreshTokenVerifier(JwtVerificationKeyResolver { key })
                .verify(challenge, issuer, null)
        }
        val binding = CredentialNonceBinding(issuer, "$issuer/credential", "$issuer/nonce")
        assertFalse(challengeService.validate(nonceService.issue(binding).nonce, issuer))
        assertEquals(CredentialNonceValidationResult.INVALID, nonceService.validate(challengeService.issue(issuer), binding))
        val claims = Json.parseToJsonElement(
            kotlin.io.encoding.Base64.UrlSafe.withPadding(kotlin.io.encoding.Base64.PaddingOption.ABSENT)
                .decode(challengeService.issue(issuer).split('.')[1]).decodeToString()
        ).jsonObject
        for (replacement in listOf(
            claims - "purpose", claims + ("purpose" to JsonPrimitive("other")),
            claims - "jti", claims + ("jti" to JsonPrimitive("short")),
            claims + ("exp" to JsonPrimitive("1800000300")),
        )) {
            val token = key.signJws(JsonObject(replacement).toString().encodeToByteArray())
            assertFalse(challengeService.validate(token, issuer))
        }
    }

    @Test
    fun `challenge lifetime must be positive`() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        for (seconds in listOf(0, -1)) assertFailsWith<IllegalArgumentException> {
            JwtAttestationChallengeService(
                JwtSigningKeyResolver { key }, JwtVerificationKeyResolver { key }, challengeLifetime = seconds.seconds,
            )
        }
    }
}
