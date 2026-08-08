package id.walt.rpcert.wallet

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.rpcert.issuance.RelyingPartyRegistrationCertificateIssuer
import id.walt.rpcert.models.RelyingPartyRegistrationCertificate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

class RelyingPartyRegistrationCertificateVerifierTest {

    @Test
    fun decodeRejectsNonJwtString() {
        val exception = assertFailsWith<RegistrationCertificateVerificationException> {
            RelyingPartyRegistrationCertificateVerifier.decode("not-a-jwt")
        }
        assertTrue(exception.message!!.contains("valid JWT"))
    }

    @Test
    fun decodeRejectsWrongTypHeader() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val x5c = RpCertTestFixtures.selfSignedLeafCertificateX5c(key)
        val jwt = signRawJwt(key, x5c, typ = "wrong-type")

        val exception = assertFailsWith<RegistrationCertificateVerificationException> {
            RelyingPartyRegistrationCertificateVerifier.decode(jwt)
        }
        assertTrue(exception.message!!.contains("typ"))
    }

    @Test
    fun decodeRejectsMissingX5cHeader() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val jwt = signRawJwt(key, x5c = null)

        val exception = assertFailsWith<RegistrationCertificateVerificationException> {
            RelyingPartyRegistrationCertificateVerifier.decode(jwt)
        }
        assertTrue(exception.message!!.contains("x5c"))
    }

    @Test
    fun decodeRejectsMalformedPayload() = runTest {
        val key = JWKKey.generate(KeyType.secp256r1)
        val x5c = RpCertTestFixtures.selfSignedLeafCertificateX5c(key)
        val jwt = key.signJws(
            "{}".encodeToByteArray(),
            mapOf(
                "typ" to JsonPrimitive(RelyingPartyRegistrationCertificateIssuer.JWT_TYPE),
                "x5c" to JsonArray(x5c.map { JsonPrimitive(it) }),
            ),
        )

        val exception = assertFailsWith<RegistrationCertificateVerificationException> {
            RelyingPartyRegistrationCertificateVerifier.decode(jwt)
        }
        assertTrue(exception.message!!.contains("Wallet-Relying Party Registration Certificate"))
    }

    @Test
    fun verifySucceedsWhenSelfSignedCertIsProvidedAsTrustAnchor() = runTest {
        val jwt = RpCertTestFixtures.signedCertificateJwt()

        val result = RelyingPartyRegistrationCertificateVerifier.verify(jwt, trustAnchors = selfTrustAnchor(jwt))

        assertTrue(result.isSuccess)
        assertEquals("Example Relying Party", result.getOrThrow().certificate.name)
    }

    @Test
    fun verifyFailsWhenSelfSignedRootIsNotTrusted() = runTest {
        val jwt = RpCertTestFixtures.signedCertificateJwt()

        val result = RelyingPartyRegistrationCertificateVerifier.verify(jwt, allowTrustedChainRoot = false)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RegistrationCertificateVerificationException)
    }

    @Test
    fun verifyFailsForTamperedSignature() = runTest {
        val jwt = RpCertTestFixtures.signedCertificateJwt()
        val signedByAnotherKey = RpCertTestFixtures.signedCertificateJwt()
        val tampered = jwt.substringBeforeLast(".") + "." + signedByAnotherKey.substringAfterLast(".")

        val result = RelyingPartyRegistrationCertificateVerifier.verify(tampered, trustAnchors = selfTrustAnchor(jwt))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is RegistrationCertificateVerificationException)
    }

    @Test
    fun verifyFailsWhenIatIsInTheFutureBeyondClockSkew() = runTest {
        val now = Clock.System.now()
        val payload = RpCertTestFixtures.sampleCertificate(iat = (now + 10.minutes).epochSeconds)
        val jwt = RpCertTestFixtures.signedCertificateJwt(payload = payload)

        val result = RelyingPartyRegistrationCertificateVerifier.verify(jwt, trustAnchors = selfTrustAnchor(jwt), now = now)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("future"))
    }

    @Test
    fun verifySucceedsWhenIatIsWithinClockSkewTolerance() = runTest {
        val now = Clock.System.now()
        val payload = RpCertTestFixtures.sampleCertificate(iat = (now + 4.minutes).epochSeconds)
        val jwt = RpCertTestFixtures.signedCertificateJwt(payload = payload)

        val result = RelyingPartyRegistrationCertificateVerifier.verify(jwt, trustAnchors = selfTrustAnchor(jwt), now = now)

        assertTrue(result.isSuccess)
    }

    @Test
    fun verifyFailsWhenExpired() = runTest {
        val now = Clock.System.now()
        val payload = RpCertTestFixtures.sampleCertificate(
            iat = (now - 20.minutes).epochSeconds,
            exp = (now - 10.minutes).epochSeconds,
        )
        val jwt = RpCertTestFixtures.signedCertificateJwt(payload = payload)

        val result = RelyingPartyRegistrationCertificateVerifier.verify(jwt, trustAnchors = selfTrustAnchor(jwt), now = now)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("expired"))
    }

    @Test
    fun verifySucceedsWhenNotYetExpired() = runTest {
        val now = Clock.System.now()
        val payload = RpCertTestFixtures.sampleCertificate(
            iat = (now - 10.minutes).epochSeconds,
            exp = (now + 10.minutes).epochSeconds,
        )
        val jwt = RpCertTestFixtures.signedCertificateJwt(payload = payload)

        val result = RelyingPartyRegistrationCertificateVerifier.verify(jwt, trustAnchors = selfTrustAnchor(jwt), now = now)

        assertTrue(result.isSuccess)
    }

    // The x5c chain is a single self-signed leaf with no separate root cert, so it must be
    // trusted explicitly (allowTrustedChainRoot alone only covers a root distinct from the leaf).
    private fun selfTrustAnchor(jwt: String) = RelyingPartyRegistrationCertificateVerifier.decode(jwt).certificateChain

    private suspend fun signRawJwt(
        key: JWKKey,
        x5c: List<String>?,
        typ: String = RelyingPartyRegistrationCertificateIssuer.JWT_TYPE,
    ): String {
        val payloadBytes = Json.encodeToString(
            RelyingPartyRegistrationCertificate.serializer(),
            RpCertTestFixtures.sampleCertificate(),
        ).encodeToByteArray()
        val headers = buildMap {
            put("typ", JsonPrimitive(typ))
            x5c?.let { put("x5c", JsonArray(it.map { cert -> JsonPrimitive(cert) })) }
        }
        return key.signJws(payloadBytes, headers)
    }
}
