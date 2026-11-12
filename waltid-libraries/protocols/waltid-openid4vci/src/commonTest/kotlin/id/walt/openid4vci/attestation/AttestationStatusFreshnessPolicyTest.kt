package id.walt.openid4vci.attestation

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.statuslist.codec.TokenStatusListCodec
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@OptIn(ExperimentalSerializationApi::class)
class AttestationStatusFreshnessPolicyTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val now = Instant.fromEpochSeconds(1_800_000_000)
    private val uri = "https://attester.example/status/1"

    private suspend fun key() = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId("attester"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))

    private suspend fun token(
        signer: Key,
        ageSeconds: Long = 3600,
        expiresAt: Long = now.epochSeconds + 3600,
        ttl: Long = 60,
        status: Int = 0,
        subject: String = uri,
        notBefore: Long? = null,
    ) = CompactJws.sign(buildJsonObject {
        put("sub", subject); put("iat", now.epochSeconds - ageSeconds)
        put("exp", expiresAt); put("ttl", ttl)
        notBefore?.let { put("nbf", it) }
        putJsonObject("status_list") { put("bits", 8); put("lst", TokenStatusListCodec.encode(byteArrayOf(status.toByte()))) }
    }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", "statuslist+jwt") })

    private suspend fun capture(signer: Key, policy: AttestationStatusPolicy = AttestationStatusPolicy()) =
        captureAttestationStatus(buildJsonObject {
            putJsonObject("status") { putJsonObject("status_list") { put("idx", 0); put("uri", uri) } }
        }, signer.exportPublicJwkObject(), policy, now)

    private suspend fun validate(jwt: String, evidence: VerifiedAttestationStatus) =
        AttestationStatusValidator(AttestationStatusResolver { jwt }, now = { now })
            .validate(listOf(evidence))

    @Test fun omittedAndNullConfigurationLeaveTheAgeLimitOptional() {
        assertNull(AttestationStatusPolicy().maximumListAgeSeconds)
        assertNull(Json.decodeFromString<AttestationStatusPolicy>("{}").maximumListAgeSeconds)
        assertNull(Json.decodeFromString<AttestationStatusPolicy>("{\"maximumListAgeSeconds\":null}").maximumListAgeSeconds)
        assertEquals(900L, Json.decodeFromString<AttestationStatusPolicy>("{\"maximumListAgeSeconds\":900}").maximumListAgeSeconds)
        for (limit in listOf(0, -1)) {
            assertFailsWith<IllegalArgumentException> {
                Json.decodeFromString<AttestationStatusPolicy>("{\"maximumListAgeSeconds\":$limit}")
            }
        }
    }

    @Test fun freshlyRetrievedOlderListIsAcceptedByDefault() = runTest {
        val signer = key()
        val evidence = capture(signer)
        assertEquals(AttestationStatusOutcome.VALID, validate(token(signer), evidence).outcome)
    }

    @Test fun anExplicitLimitKeepsItsInclusiveBoundary() = runTest {
        val signer = key()
        val evidence = capture(signer, AttestationStatusPolicy(maximumListAgeSeconds = 300))
        assertEquals(AttestationStatusOutcome.VALID, validate(token(signer, ageSeconds = 300), evidence).outcome)
        val stale = validate(token(signer, ageSeconds = 301), evidence)
        assertEquals(AttestationStatusOutcome.UNVERIFIABLE, stale.outcome)
        assertFalse(stale.permanentlyDenied)
    }

    @Test fun optionalAgeSurvivesRestartWhenDefaultsAndNullsAreOmitted() = runTest {
        val signer = key()
        val json = Json { encodeDefaults = false; explicitNulls = false }
        val encoded = json.encodeToJsonElement(capture(signer)).jsonObject
        val recovered = json.decodeFromJsonElement<VerifiedAttestationStatus>(encoded)
        assertNull(recovered.policy.maximumListAgeSeconds)
        assertEquals(AttestationStatusOutcome.VALID, validate(token(signer), recovered).outcome)
    }

    @Test fun explicitAgeSurvivesRestart() = runTest {
        val signer = key()
        for (limit in listOf(300L, 900L)) {
            val accepted = capture(signer, AttestationStatusPolicy(maximumListAgeSeconds = limit))
            val recovered = Json.decodeFromString<VerifiedAttestationStatus>(Json.encodeToString(accepted))
            assertEquals(limit, recovered.policy.maximumListAgeSeconds)
            assertEquals(if (limit == 300L) AttestationStatusOutcome.UNVERIFIABLE else AttestationStatusOutcome.VALID,
                validate(token(signer, ageSeconds = 600), recovered).outcome)
        }
    }

    @Test fun signedExpiryAndExcessiveFutureIssuanceStillApplyWithoutAnAgeLimit() = runTest {
        val signer = key()
        val evidence = capture(signer)
        for (jwt in listOf(token(signer, expiresAt = now.epochSeconds), token(signer, ageSeconds = -61))) {
            val result = validate(jwt, evidence)
            assertEquals(AttestationStatusOutcome.UNVERIFIABLE, result.outcome)
            assertFalse(result.permanentlyDenied)
        }
    }

    @Test fun futureSignedTimesUseTheAttestationProfileSkew() = runTest {
        val signer = key()
        val evidence = capture(signer)
        for (claim in listOf("iat", "nbf")) for (ahead in listOf(1L, 60L, 61L)) {
            val jwt = token(signer,
                ageSeconds = if (claim == "iat") -ahead else 0,
                notBefore = if (claim == "nbf") now.epochSeconds + ahead else null,
            )
            val result = validate(jwt, evidence)
            assertEquals(if (ahead <= 60) AttestationStatusOutcome.VALID else AttestationStatusOutcome.UNVERIFIABLE,
                result.outcome, "$claim ahead by $ahead seconds")
            assertFalse(result.permanentlyDenied)
        }
    }

    @Test fun revocationAndSuspensionStillApplyWithinClockSkew() = runTest {
        val signer = key()
        val evidence = capture(signer)
        assertEquals(AttestationStatusOutcome.INVALID,
            validate(token(signer, ageSeconds = -1, status = 1, notBefore = now.epochSeconds + 1), evidence).outcome)
        assertEquals(AttestationStatusOutcome.SUSPENDED,
            validate(token(signer, ageSeconds = -1, status = 2, notBefore = now.epochSeconds + 1), evidence).outcome)
    }

    @Test fun signedTtlStillAppliesAfterAnotherLookupWithoutAnAgeLimit() = runTest {
        val signer = key()
        var current = now
        val first = token(signer, ttl = 1)
        val secondUri = "$uri/second"
        val second = token(signer, subject = secondUri)
        val firstEvidence = capture(signer)
        val secondEvidence = firstEvidence.copy(reference = AttestationStatusReference(secondUri, 0))
        val validator = AttestationStatusValidator(AttestationStatusResolver { requested ->
            if (requested == secondUri) { current += 2.seconds; second } else first
        }, now = { current })
        assertEquals(AttestationStatusOutcome.VALID, validator.validate(listOf(firstEvidence)).outcome)
        assertEquals(AttestationStatusOutcome.UNVERIFIABLE,
            validator.validate(listOf(firstEvidence, secondEvidence)).outcome)
    }

    @Test fun revocationAndSuspensionStillApplyWithoutAnAgeLimit() = runTest {
        val signer = key()
        val evidence = capture(signer)
        assertEquals(AttestationStatusOutcome.INVALID, validate(token(signer, status = 1), evidence).outcome)
        assertEquals(AttestationStatusOutcome.SUSPENDED, validate(token(signer, status = 2), evidence).outcome)
    }
}
