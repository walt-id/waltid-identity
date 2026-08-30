@file:OptIn(
    ExperimentalUnsignedTypes::class,
    kotlinx.serialization.ExperimentalSerializationApi::class,
)

package id.walt.mdoc.proximity

import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

class RicalTest {
    private val authority = RicalCertificateInfo(
        certificateDer = ImmutableBytes.of(byteArrayOf(1)),
        serialNumber = ImmutableBytes.of(byteArrayOf(2)),
        subjectKeyIdentifier = ImmutableBytes.of(byteArrayOf(3)),
        isTrustAnchor = true,
        name = "Example reader authority",
    )
    private val evidence = ReaderAuthenticationEvidence(
        ReaderAuthenticationScope.WHOLE_REQUEST,
        certificateChainDer = listOf(ImmutableBytes.of(byteArrayOf(4))),
    )

    private fun signed(rical: Rical): SignedRical {
        val protected = coseCompliantCbor.encodeToByteArray(
            CoseHeaders.serializer(),
            CoseHeaders(
                algorithm = Cose.Algorithm.ES256,
                x5chain = listOf(CoseCertificate(byteArrayOf(6))),
            ),
        )
        return SignedRical.fromCoseSign1(
            CoseSign1(
                protected = protected,
                unprotected = CoseHeaders(),
                payload = coseCompliantCbor.encodeToByteArray(Rical.serializer(), rical),
                signature = byteArrayOf(7),
            )
        )
    }

    @Test
    fun `valid RICAL evidence only establishes trust when profile policy permits it`() = runTest {
        val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
        val signed = signed(Rical("1.0", "provider", issuedAt, certificateInfos = listOf(authority), type = "reader"))
        suspend fun evaluate(establishTrust: Boolean) = RicalReaderTrustEvaluator(
            provider = RicalProvider { RicalProviderResult.Available(signed) },
            policy = RicalPolicy(
                "provider", setOf("reader"), listOf(ImmutableBytes.of(byteArrayOf(8))), establishTrust
            ),
            signatureValidator = RicalSignatureValidator { _, _ -> true },
            constraintEvaluator = RicalConstraintEvaluator { _, _ -> true },
            now = { Instant.parse("2026-01-02T00:00:00Z") },
            pathValidator = RicalReaderPathValidator { _, _ -> RicalReaderPathResult.Valid(authority) },
        ).evaluate(evidence)

        assertEquals(ReaderTrustState.VALID_BUT_UNTRUSTED, evaluate(false).state)
        assertEquals(ReaderTrustState.TRUSTED, evaluate(true).state)
    }

    @Test
    fun `RICAL failures remain explicit and nextUpdate is only a refresh hint`() = runTest {
        val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
        val baseRical = Rical(
            "1.0",
            "provider",
            issuedAt,
            nextUpdate = Instant.parse("2026-01-02T00:00:00Z"),
            notAfter = Instant.parse("2026-02-01T00:00:00Z"),
            certificateInfos = listOf(authority),
            type = "reader",
        )
        val baseSigned = signed(baseRical)
        suspend fun evaluate(
            providerResult: RicalProviderResult = RicalProviderResult.Available(baseSigned),
            signatureValid: Boolean = true,
            path: RicalReaderPathResult = RicalReaderPathResult.Valid(authority),
            constraintsValid: Boolean = true,
            now: Instant = Instant.parse("2026-01-03T00:00:00Z"),
        ) = RicalReaderTrustEvaluator(
            provider = RicalProvider { providerResult },
            policy = RicalPolicy(
                "provider",
                setOf("reader"),
                listOf(ImmutableBytes.of(byteArrayOf(8))),
                establishReaderTrust = true,
            ),
            signatureValidator = RicalSignatureValidator { _, _ -> signatureValid },
            constraintEvaluator = RicalConstraintEvaluator { _, _ -> constraintsValid },
            now = { now },
            pathValidator = RicalReaderPathValidator { _, _ -> path },
        ).evaluateDetailed(evidence)

        assertEquals(
            RicalEvaluationState.UNAVAILABLE,
            evaluate(RicalProviderResult.Unavailable("offline")).state,
        )
        assertEquals(
            RicalEvaluationState.INVALID,
            evaluate(RicalProviderResult.Conflict("two current versions")).state,
        )
        assertEquals(
            RicalEvaluationState.INVALID,
            evaluate(RicalProviderResult.Available(signed(baseRical.copy(provider = "other")))).state,
        )
        assertEquals(RicalEvaluationState.INVALID, evaluate(signatureValid = false).state)
        assertEquals(RicalEvaluationState.INVALID, evaluate(path = RicalReaderPathResult.Invalid).state)
        assertEquals(
            RicalEvaluationState.INVALID,
            evaluate(path = RicalReaderPathResult.Valid(authority.copy(name = "Not in active RICAL"))).state,
        )
        assertEquals(RicalEvaluationState.NO_MATCHING_AUTHORITY, evaluate(constraintsValid = false).state)
        assertEquals(
            RicalEvaluationState.INVALID,
            evaluate(now = Instant.parse("2026-02-01T00:00:00Z")).state,
        )
        assertEquals(
            RicalEvaluationState.INVALID,
            evaluate(
                RicalProviderResult.Available(
                    signed(baseRical.copy(date = Instant.parse("2026-01-04T00:00:00Z"), nextUpdate = null))
                )
            ).state,
        )
        val revoked = evaluate(path = RicalReaderPathResult.Revoked)
        assertEquals(RicalEvaluationState.MATCHED, revoked.state)
        assertEquals(ReaderTrustState.REVOKED, revoked.decision.state)

        val overdueUpdate = evaluate()
        assertEquals(RicalEvaluationState.MATCHED, overdueUpdate.state)
        assertEquals(ReaderTrustState.TRUSTED, overdueUpdate.decision.state)
        assertTrue(baseRical.nextUpdate!! < Instant.parse("2026-01-03T00:00:00Z"))
    }

    @Test
    fun `signed RICAL is bound to its exact attached untagged COSE payload`() {
        val rical = Rical(
            "1.0",
            "provider",
            Instant.parse("2026-01-01T00:00:00Z"),
            certificateInfos = listOf(authority),
            type = "reader",
        )
        val signed = signed(rical)

        assertEquals(rical, SignedRical.decode(signed.exactMessage.copy()).rical)
        assertFailsWith<IllegalArgumentException> { SignedRical.decode(signed.coseSign1.toTagged()) }
        assertFailsWith<IllegalArgumentException> {
            SignedRical.fromCoseSign1(signed.coseSign1.copy(payload = null))
        }
        assertFailsWith<IllegalArgumentException> {
            SignedRical.fromCoseSign1(
                signed.coseSign1.copy(
                    protected = coseCompliantCbor.encodeToByteArray(
                        CoseHeaders.serializer(),
                        CoseHeaders(algorithm = Cose.Algorithm.ES256),
                    ),
                    unprotected = CoseHeaders(x5chain = listOf(CoseCertificate(byteArrayOf(6)))),
                )
            )
        }
    }

    @Test
    fun `RICAL wire encoding preserves DIS tags spelling extensions and reserved fields`() {
        val constrainedAuthority = authority.copy(
            trustConstraints = listOf(
                RicalTrustConstraint(mapOf("documentType" to CborString("org.iso.18013.5.1.mDL")))
            ),
            extensions = mapOf("certificateExtension" to CborString("extension")),
            reserved = mapOf("futureCertificateField" to CborString("future")),
        )
        val rical = Rical(
            version = "1.0",
            provider = "provider",
            date = Instant.parse("2026-01-01T00:00:00Z"),
            certificateInfos = listOf(constrainedAuthority),
            type = "reader",
            extensions = mapOf("ricalExtension" to CborString("extension")),
            reserved = mapOf("futureRicalField" to CborString("future")),
        )

        val encoded = coseCompliantCbor.encodeToByteArray(Rical.serializer(), rical)
        val top = coseCompliantCbor.decodeFromByteArray<CborMap>(encoded)
        val certificate = assertIs<CborMap>(
            assertIs<CborArray>(top[CborString("certificateInfos")]).single()
        )
        val serial = assertIs<CborByteString>(certificate[CborString("serialNumber")])
        val date = assertIs<CborString>(top[CborString("date")])

        assertTrue(2uL in serial.tags)
        assertTrue(0uL in date.tags)
        assertTrue(CborString("trustContraints") in certificate)
        assertFalse(CborString("trustConstraints") in certificate)
        assertEquals(rical, coseCompliantCbor.decodeFromByteArray<Rical>(encoded))
    }

    @Test
    fun `RICAL updates distinguish unchanged stale conflict and accepted candidates`() {
        fun candidate(id: ULong?, date: String, name: String = "Example reader authority") = signed(
            Rical(
                "1.0",
                "provider",
                Instant.parse(date),
                certificateInfos = listOf(
                    RicalCertificateInfo(
                        certificateDer = ImmutableBytes.of(byteArrayOf(1)),
                        serialNumber = ImmutableBytes.of(byteArrayOf(2)),
                        subjectKeyIdentifier = ImmutableBytes.of(byteArrayOf(3)),
                        isTrustAnchor = true,
                        name = name,
                    )
                ),
                id = id,
                type = "reader",
            )
        )

        val current = candidate(2u, "2026-01-02T00:00:00Z")
        assertEquals(RicalUpdateDecision.Initial, validateRicalUpdate(null, current))
        assertEquals(RicalUpdateDecision.Unchanged, validateRicalUpdate(current, current))
        assertTrue(validateRicalUpdate(current, candidate(1u, "2026-01-01T00:00:00Z")) is RicalUpdateDecision.Stale)
        assertTrue(
            validateRicalUpdate(current, candidate(2u, "2026-01-02T00:00:00Z", "Other"))
                is RicalUpdateDecision.Conflict
        )
        assertEquals(
            RicalUpdateDecision.Accepted,
            validateRicalUpdate(current, candidate(3u, "2026-01-03T00:00:00Z")),
        )
    }
}
