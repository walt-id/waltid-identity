@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.mdoc.proximity

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    @Test
    fun `valid RICAL evidence only establishes trust when profile policy permits it`() = runTest {
        val issuedAt = Instant.parse("2026-01-01T00:00:00Z")
        val signed = SignedRical(
            Rical("1.0", "provider", issuedAt, certificateInfos = listOf(authority), type = "reader"),
            ImmutableBytes.of(byteArrayOf(5)),
            listOf(ImmutableBytes.of(byteArrayOf(6))),
            ImmutableBytes.of(byteArrayOf(7)),
        )
        suspend fun evaluate(establishTrust: Boolean) = RicalReaderTrustEvaluator(
            provider = RicalProvider { signed },
            policy = RicalPolicy(
                "provider", setOf("reader"), listOf(ImmutableBytes.of(byteArrayOf(8))), establishTrust
            ),
            signatureValidator = RicalSignatureValidator { _, _ -> true },
            constraintEvaluator = RicalConstraintEvaluator { _, _ -> true },
            now = { Instant.parse("2026-01-02T00:00:00Z") },
            pathValidator = RicalReaderPathValidator { _, _, _ -> RicalReaderPathState.VALID },
        ).evaluate(evidence)

        assertEquals(ReaderTrustState.VALID_BUT_UNTRUSTED, evaluate(false).state)
        assertEquals(ReaderTrustState.TRUSTED, evaluate(true).state)
    }

    @Test
    fun `RICAL failures remain explicit and never establish trust`() = runTest {
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
        val baseSigned = SignedRical(
            baseRical,
            ImmutableBytes.of(byteArrayOf(5)),
            listOf(ImmutableBytes.of(byteArrayOf(6))),
            ImmutableBytes.of(byteArrayOf(7)),
        )
        suspend fun evaluate(
            signed: SignedRical? = baseSigned,
            signatureValid: Boolean = true,
            path: RicalReaderPathState = RicalReaderPathState.VALID,
            constraintsValid: Boolean = true,
            now: Instant = Instant.parse("2026-01-03T00:00:00Z"),
        ) = RicalReaderTrustEvaluator(
            provider = RicalProvider { signed },
            policy = RicalPolicy(
                "provider",
                setOf("reader"),
                listOf(ImmutableBytes.of(byteArrayOf(8))),
                establishReaderTrust = true,
            ),
            signatureValidator = RicalSignatureValidator { _, _ -> signatureValid },
            constraintEvaluator = RicalConstraintEvaluator { _, _ -> constraintsValid },
            now = { now },
            pathValidator = RicalReaderPathValidator { _, _, _ -> path },
        ).evaluate(evidence)

        assertEquals(ReaderTrustState.VALID_BUT_UNTRUSTED, evaluate(signed = null).state)
        assertEquals(
            ReaderTrustState.VALID_BUT_UNTRUSTED,
            evaluate(signed = baseSigned.copy(rical = baseRical.copy(provider = "other"))).state,
        )
        assertEquals(ReaderTrustState.VALID_BUT_UNTRUSTED, evaluate(signatureValid = false).state)
        assertEquals(ReaderTrustState.VALID_BUT_UNTRUSTED, evaluate(path = RicalReaderPathState.INVALID).state)
        assertEquals(ReaderTrustState.VALID_BUT_UNTRUSTED, evaluate(constraintsValid = false).state)
        assertEquals(
            ReaderTrustState.VALID_BUT_UNTRUSTED,
            evaluate(now = Instant.parse("2026-02-01T00:00:00Z")).state,
        )
        assertEquals(
            ReaderTrustState.VALID_BUT_UNTRUSTED,
            evaluate(
                signed = baseSigned.copy(
                    rical = baseRical.copy(
                        date = Instant.parse("2026-01-04T00:00:00Z"),
                        nextUpdate = null,
                    ),
                )
            ).state,
        )
        assertEquals(ReaderTrustState.REVOKED, evaluate(path = RicalReaderPathState.REVOKED).state)

        val overdueUpdate = evaluate()
        assertEquals(ReaderTrustState.TRUSTED, overdueUpdate.state)
        assertTrue(baseRical.nextUpdate!! < Instant.parse("2026-01-03T00:00:00Z"))
    }
}
