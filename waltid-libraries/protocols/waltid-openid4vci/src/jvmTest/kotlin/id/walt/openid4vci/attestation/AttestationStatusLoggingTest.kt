package id.walt.openid4vci.attestation

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.*
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.statuslist.codec.TokenStatusListCodec
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.webdatafetching.WebDataFetchingConfiguration
import io.github.oshai.kotlinlogging.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.serialization.json.*
import org.junit.jupiter.api.parallel.ResourceLock
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Capture actual log events through KotlinLogging's existing configurable appender. */
@ResourceLock("KotlinLoggingConfiguration")
class AttestationStatusLoggingTest {
    private val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val time = Instant.fromEpochSeconds(1_800_000_000)
    private val uri = "https://provider.example/status/1"
    private val events = mutableListOf<KLoggingEvent>()
    private lateinit var originalFactory: KLoggerFactory
    private lateinit var originalAppender: Appender
    private lateinit var originalLevel: Level

    @BeforeTest fun captureLogEvents() {
        originalFactory = KotlinLoggingConfiguration.loggerFactory
        originalAppender = KotlinLoggingConfiguration.direct.appender
        originalLevel = KotlinLoggingConfiguration.direct.logLevel
        KotlinLoggingConfiguration.loggerFactory = DirectLoggerFactory
        KotlinLoggingConfiguration.direct.logLevel = Level.TRACE
        KotlinLoggingConfiguration.direct.appender = object : Appender {
            override fun log(loggingEvent: KLoggingEvent) { events += loggingEvent }
        }
    }

    @AfterTest fun restoreLogging() {
        KotlinLoggingConfiguration.direct.appender = originalAppender
        KotlinLoggingConfiguration.direct.logLevel = originalLevel
        KotlinLoggingConfiguration.loggerFactory = originalFactory
    }

    private suspend fun key(id: String = "attester") = runtime.generateSoftwareKey(GenerateSoftwareKeyRequest(
        KeyId(id), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
    ))

    private suspend fun token(
        signer: Key, subject: String = uri, status: Int = 0,
        issuedAt: Long = time.epochSeconds - 10, expiresAt: Long = time.epochSeconds + 600,
        ttl: Long = 60, notBefore: Long? = null, type: String = "statuslist+jwt",
    ) = CompactJws.sign(buildJsonObject {
        put("sub", subject); put("iat", issuedAt); put("exp", expiresAt); put("ttl", ttl)
        notBefore?.let { put("nbf", it) }
        putJsonObject("status_list") { put("bits", 8); put("lst", TokenStatusListCodec.encode(byteArrayOf(status.toByte()))) }
    }.toString().encodeToByteArray(), signer, JwsAlgorithm.ES256, buildJsonObject { put("typ", type) })

    private fun evidence(publicJwk: JsonObject?, reference: String = uri, index: Int = 0, maximumAge: Long? = null) =
        VerifiedAttestationStatus(
            capturedAt = time.epochSeconds, policy = AttestationStatusPolicy(true, maximumAge),
            reference = AttestationStatusReference(reference, index), attesterPublicJwk = publicJwk,
        )

    private data class Captured<T>(val result: T, val events: List<KLoggingEvent>) {
        val output = events.joinToString("\n") { "${it.level} ${it.message}" }
        val warnings = events.filter { it.level == Level.WARN }.map { "${it.level} ${it.message}" }
    }

    private suspend fun <T> capture(action: suspend () -> T): Captured<T> {
        val start = events.size
        val result = action()
        val captured = events.drop(start).filter { it.loggerName.startsWith("id.walt.openid4vci.attestation.") }
        captured.forEach {
            assertNull(it.cause, "Status diagnostics must not attach an exception")
            assertNull(it.payload, "Status diagnostics must not attach evidence payloads")
        }
        return Captured(result, captured)
    }

    private fun assertFailure(captured: Captured<AttestationStatusResult>, reason: AttestationStatusFailureReason, host: String = "provider.example") {
        assertEquals(AttestationStatusOutcome.UNVERIFIABLE, captured.result.outcome)
        assertFalse(captured.result.permanentlyDenied)
        val warning = captured.warnings.single()
        assertContains(warning, "WARN")
        assertTrue(warning.endsWith("Attestation status validation failed reason=$reason host=$host"), warning)
    }

    @Test fun lookupFailureLogsOnlyCategoryAndHostWithoutItsExceptionOrEvidence() = runTest {
        val signer = key()
        val jwt = token(signer)
        val publicJwk = signer.exportPublicJwkObject()
        val reference = "https://private-user:private-password@provider.example/private-path?secret=private-query"
        val entry = evidence(publicJwk, reference, index = 1_234_567)
            .copy(attesterIssuer = "private-attester-identity")
        val validator = AttestationStatusValidator(AttestationStatusResolver {
            error("private-exception-message $reference $jwt $publicJwk index=1234567")
        }, now = { time })
        val captured = capture { validator.validate(listOf(entry)) }
        assertFailure(captured, AttestationStatusFailureReason.LOOKUP)
        for (privateValue in listOf(jwt, reference, "private-user", "private-password", "private-path", "private-query",
            "private-attester-identity", "private-exception-message", publicJwk.toString(),
            publicJwk.getValue("x").jsonPrimitive.content, "1234567", "IllegalStateException", "\tat ")) {
            assertFalse(privateValue in captured.output, "Sensitive value or exception appeared in logs")
        }
    }

    @Test fun invalidMediaTypeKeepsItsFormatCategoryThroughTheLookupBoundary() = runTest {
        val signer = key()
        val configuration = WebDataFetchingConfiguration(maxResponseBytes = HttpAttestationStatusResolver.MAXIMUM_RESPONSE_BYTES)
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { respond("private-response-body", headers = headersOf(HttpHeaders.ContentType, "application/json")) }
        })
        val client = HttpClient(engine) { configuration.applyConfigurationToHttpClient(this) }
        val fetcher = WebDataFetcher.wrapping(client, WebDataFetcherId.OPENID4VCI_ATTESTATION_STATUS.name, configuration)
        try {
            val entry = evidence(signer.exportPublicJwkObject())
            val validator = AttestationStatusValidator(HttpAttestationStatusResolver(fetcher), now = { time })
            val captured = capture { validator.validate(listOf(entry)) }
            assertFailure(captured, AttestationStatusFailureReason.FORMAT)
            assertFalse("private-response-body" in captured.output)
        } finally { fetcher.close() }
    }

    @Test fun missingAndUnusableTrustedSignerContextAreSignerFailures() = runTest {
        val signer = key()
        val jwt = token(signer)
        for (publicJwk in listOf(null, JsonObject(emptyMap()))) {
            var lookups = 0
            val validator = AttestationStatusValidator(AttestationStatusResolver { lookups++; jwt }, now = { time })
            assertFailure(capture { validator.validate(listOf(evidence(publicJwk))) }, AttestationStatusFailureReason.SIGNER)
            assertEquals(if (publicJwk == null) 0 else 1, lookups)
        }
    }

    @Test fun badSignaturesAreDistinguishedFromMissingSignerContext() = runTest {
        val accepted = key("accepted")
        val attacker = key("attacker")
        val jwt = token(attacker)
        val validator = AttestationStatusValidator(AttestationStatusResolver { jwt }, now = { time })
        val captured = capture { validator.validate(listOf(evidence(accepted.exportPublicJwkObject()))) }
        assertFailure(captured, AttestationStatusFailureReason.SIGNATURE)
        assertFalse(jwt in captured.output)
    }

    @Test fun malformedTokensTypesSubjectsIndicesAndUnknownStatusesAreFormatFailures() = runTest {
        val signer = key()
        val entry = evidence(signer.exportPublicJwkObject())
        val cases = listOf(
            "private-malformed-jwt" to entry,
            token(signer, type = "oauth-client-attestation+jwt") to entry,
            token(signer, subject = "$uri/other") to entry,
            token(signer) to entry.copy(reference = AttestationStatusReference(uri, 9)),
            token(signer, status = 255) to entry,
        )
        for ((jwt, accepted) in cases) {
            val validator = AttestationStatusValidator(AttestationStatusResolver { jwt }, now = { time })
            assertFailure(capture { validator.validate(listOf(accepted)) }, AttestationStatusFailureReason.FORMAT)
        }
    }

    @Test fun expiredFutureAndOverAgeListsAreFreshnessFailures() = runTest {
        val signer = key()
        val entry = evidence(signer.exportPublicJwkObject())
        val cases = listOf(
            token(signer, expiresAt = time.epochSeconds) to entry,
            token(signer, issuedAt = time.epochSeconds + 61) to entry,
            token(signer, notBefore = time.epochSeconds + 61) to entry,
            token(signer, issuedAt = time.epochSeconds - 61) to entry.copy(policy = AttestationStatusPolicy(true, 60)),
        )
        for ((jwt, accepted) in cases) {
            val validator = AttestationStatusValidator(AttestationStatusResolver { jwt }, now = { time })
            assertFailure(capture { validator.validate(listOf(accepted)) }, AttestationStatusFailureReason.FRESHNESS)
        }
    }

    @Test fun aListThatExpiresDuringLaterLookupsIsLoggedWithoutOverridingRevocation() = runTest {
        val signer = key()
        val publicJwk = signer.exportPublicJwkObject()
        val secondUri = "https://second.example/status/2"
        for (status in listOf(0, 1)) {
            var now = time
            val first = token(signer, ttl = 1)
            val second = token(signer, subject = secondUri, status = status)
            val validator = AttestationStatusValidator(AttestationStatusResolver { requested ->
                if (requested == secondUri) { now += 2.seconds; second } else first
            }, now = { now })
            val captured = capture { validator.validate(listOf(evidence(publicJwk), evidence(publicJwk, secondUri))) }
            assertEquals(if (status == 0) AttestationStatusOutcome.UNVERIFIABLE else AttestationStatusOutcome.INVALID,
                captured.result.outcome)
            assertContains(captured.warnings.single(), "reason=FRESHNESS host=provider.example")
        }
    }

    @Test fun warningsAreDeduplicatedPerListAndReasonWhileEachIndexAndAttesterIsChecked() = runTest {
        val signer = key()
        val otherSigner = key("other")
        val jwt = token(signer)
        val accepted = evidence(signer.exportPublicJwkObject())
        val other = evidence(otherSigner.exportPublicJwkObject())
        var lookups = 0
        val validator = AttestationStatusValidator(AttestationStatusResolver { lookups++; jwt }, now = { time })
        val captured = capture { validator.validate(listOf(accepted, other, other,
            accepted.copy(reference = AttestationStatusReference(uri, 9)),
            accepted.copy(reference = AttestationStatusReference(uri, 10)),
        )) }
        assertEquals(AttestationStatusOutcome.UNVERIFIABLE, captured.result.outcome)
        assertEquals(1, lookups)
        assertEquals(2, captured.warnings.size)
        assertEquals(1, captured.warnings.count { "reason=SIGNATURE " in it })
        assertEquals(1, captured.warnings.count { "reason=FORMAT " in it })
    }

    @Test fun diagnosticDeduplicationDoesNotHideFailuresAtTheNextCheckpoint() = runTest {
        val signer = key()
        val publicJwk = signer.exportPublicJwkObject()
        var lookups = 0
        val validator = AttestationStatusValidator(AttestationStatusResolver { lookups++; error("unavailable") }, now = { time })
        val captured = capture { List(2) { validator.validate(List(3) { index -> evidence(publicJwk, index = index) }) } }
        assertTrue(captured.result.all { it.outcome == AttestationStatusOutcome.UNVERIFIABLE })
        assertEquals(2, lookups)
        assertEquals(2, captured.warnings.size)
    }

    @Test fun malformedHostsCannotInjectDiagnosticFieldsOrLogLines() = runTest {
        val signer = key()
        val publicJwk = signer.exportPublicJwkObject()
        val validator = AttestationStatusValidator(AttestationStatusResolver { error("private-lookup-error") }, now = { time })
        for (reference in listOf("https://provider.example/path?secret=\nWARN private-forged-log",
            "https://provider.example%0aprivate-forged-log/status")) {
            val captured = capture { validator.validate(listOf(evidence(publicJwk, reference))) }
            assertFailure(captured, AttestationStatusFailureReason.LOOKUP, host = "unavailable")
            assertFalse("private-forged-log" in captured.output)
            assertFalse("private-lookup-error" in captured.output)
        }
    }

    @Test fun cancellationPropagatesWithoutBeingWrappedOrLogged() = runTest {
        val signer = key()
        val entry = evidence(signer.exportPublicJwkObject())
        val cancelled = CancellationException("private-cancellation-message")
        val validator = AttestationStatusValidator(AttestationStatusResolver { throw cancelled }, now = { time })
        val captured = capture { assertFailsWith<CancellationException> { validator.validate(listOf(entry)) } }
        assertSame(cancelled, captured.result)
        assertTrue(captured.warnings.isEmpty())
        assertFalse("private-cancellation-message" in captured.output)
    }

    @Test fun validInvalidAndSuspendedStatusesDoNotProduceTechnicalFailureWarnings() = runTest {
        val signer = key()
        val entry = evidence(signer.exportPublicJwkObject())
        for ((status, outcome) in listOf(0 to AttestationStatusOutcome.VALID,
            1 to AttestationStatusOutcome.INVALID, 2 to AttestationStatusOutcome.SUSPENDED)) {
            val jwt = token(signer, status = status)
            val validator = AttestationStatusValidator(AttestationStatusResolver { jwt }, now = { time })
            val captured = capture { validator.validate(listOf(entry)) }
            assertEquals(outcome, captured.result.outcome)
            assertTrue(captured.warnings.isEmpty())
        }
    }
}
