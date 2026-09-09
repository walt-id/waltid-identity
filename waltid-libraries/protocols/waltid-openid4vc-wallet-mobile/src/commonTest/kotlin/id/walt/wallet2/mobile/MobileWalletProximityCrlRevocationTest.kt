package id.walt.wallet2.mobile

import id.walt.certificate.x509.revocation.CrlTestFixtures
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ProximityCrlRevocationTest {
    @Test
    fun completeCrlChecksLeafAndConfiguredAuthorityWithOneFetchPerUrl() = runTest {
        val calls = mutableListOf<String>()
        val evaluator = evaluator("EC256_GOOD") { url, limit ->
            calls += url
            assertEquals(2_097_152, limit)
        }
        assertEquals(ProximityCertificateRevocationResult.Good, evaluator.evaluate(evidence()))
        assertEquals(listOf("https://crl.example.test/EC256.crl"), calls)
        // There is no SDK cache across evaluations; freshness and transport policy are reconsidered.
        assertEquals(ProximityCertificateRevocationResult.Good, evaluator.evaluate(evidence()))
        assertEquals(2, calls.size)
    }

    @Test
    fun revokedAuthorityIsDetectedEvenWhenTheReaderCertificateIsNotListed() = runTest {
        val revoked = evaluator("CA_ONLY_REVOKED").evaluate(evidence())
        assertEquals(ProximityCertificateRevocationResult.Revoked("Reader certificate authority is revoked"), revoked)
        val leafOnly = evaluator("CA_ONLY_REVOKED", ProximityCrlScope.ReaderCertificate).evaluate(evidence())
        assertEquals(ProximityCertificateRevocationResult.Good, leafOnly)
    }

    @Test
    fun readerStatusMustStillBeCurrentAfterEveryRequiredFetchCompletes() = runTest {
        val fixture = ReaderCertificateProfileFixture.create(CryptoRuntime(defaultSoftwareKeyProviders()))
        val start = Instant.fromEpochSeconds(Clock.System.now().epochSeconds) + 1.seconds
        val expiry = start + 60.seconds
        val readerCrl = fixture.crl(thisUpdate = start - 1.seconds, nextUpdate = expiry).encodeToBase64Url()
        val authorityCrl = fixture.crl(thisUpdate = start - 1.seconds, nextUpdate = start + 3600.seconds).encodeToBase64Url()
        val evidence = ProximityReaderEvidence(
            scope = ProximityReaderAuthenticationScope.WholeRequest,
            certificateChainDerBase64Url = listOf(fixture.leaf.encodedDer.toByteArray().encodeToBase64Url()),
        )
        for ((delayedUrl, completesAt) in listOf(
            "https://reader.example/crl" to expiry,
            "https://reader.example/ca-crl" to expiry,
            "https://reader.example/ca-crl" to expiry - 1.seconds,
        )) {
            var currentTime = start
            val calls = mutableListOf<String>()
            val evaluator = ProximityCrlRevocationEvaluator(
                listOf(fixture.root.encodedDer.toByteArray().encodeToBase64Url()),
                ProximityCrlScope.ReaderCertificateAndIssuingAuthorities,
                ProximityCrlFetcher { url, _ ->
                    calls += url
                    if (url == delayedUrl) currentTime = completesAt
                    ProximityCrlFetchResult.Available(
                        if (url.endsWith("/ca-crl")) authorityCrl else readerCrl,
                    )
                },
                { currentTime },
            )
            val result = evaluator.evaluate(evidence)
            if (completesAt == expiry) assertIs<ProximityCertificateRevocationResult.Indeterminate>(result, delayedUrl)
            else assertEquals(ProximityCertificateRevocationResult.Good, result)
            assertEquals(listOf("https://reader.example/crl", "https://reader.example/ca-crl"), calls)
        }
    }

    @Test
    fun invalidUnsupportedAndUnavailableCrlsRemainIndeterminate() = runTest {
        for (fixture in listOf("UNKNOWN_CRITICAL", "PARTITIONED", "DELTA", "WRONG_KEY")) {
            assertIs<ProximityCertificateRevocationResult.Indeterminate>(evaluator(fixture).evaluate(evidence()), fixture)
        }
        val unavailable = newEvaluator(ProximityCrlFetcher { _, _ -> ProximityCrlFetchResult.Unavailable })
        assertIs<ProximityCertificateRevocationResult.Indeterminate>(unavailable.evaluate(evidence()))
        for (invalid in listOf("***", "A".repeat(2_800_000))) {
            val malformed = newEvaluator(ProximityCrlFetcher { _, _ -> ProximityCrlFetchResult.Available(invalid) })
            assertIs<ProximityCertificateRevocationResult.Indeterminate>(malformed.evaluate(evidence()))
        }
        val expired = newEvaluator(ProximityCrlFetcher { _, _ -> available("EC256_GOOD") }, at = Instant.parse("2026-09-08T04:00:00Z"))
        assertIs<ProximityCertificateRevocationResult.Indeterminate>(expired.evaluate(evidence()))
    }

    @Test
    fun configuredIssuerSnapshotDoesNotTrustUnrelatedCertificateInput() = runTest {
        val configured = mutableListOf(encoded("EC256_CA"))
        val evaluator = newEvaluator(ProximityCrlFetcher { _, _ -> available("EC256_GOOD") }, issuers = configured)
        configured.clear()
        configured += encoded("EC384_CA")
        assertEquals(ProximityCertificateRevocationResult.Good, evaluator.evaluate(evidence()))
        var fetches = 0
        val unknown = newEvaluator(ProximityCrlFetcher { _, _ ->
            fetches++
            available("EC384_GOOD")
        }, issuers = listOf(encoded("EC384_CA")))
        assertIs<ProximityCertificateRevocationResult.Indeterminate>(unknown.evaluate(evidence()))
        assertEquals(0, fetches)
    }

    @Test
    fun fetchCancellationIsNotConvertedToAnIndeterminateStatus() = runTest {
        val evaluator = newEvaluator(ProximityCrlFetcher { _, _ -> throw CancellationException("Cancelled CRL fetch") })
        assertFailsWith<CancellationException> { evaluator.evaluate(evidence()) }
    }

    @Test
    fun ambiguousIssuerCertificatesDoNotSelectAnArbitraryPath() = runTest {
        var fetches = 0
        val evaluator = newEvaluator(ProximityCrlFetcher { _, _ ->
            fetches++
            available("EC256_GOOD")
        }, issuers = listOf(encoded("EC256_CA"), encoded("ISSUER_EXPIRED")))
        assertIs<ProximityCertificateRevocationResult.Indeterminate>(evaluator.evaluate(evidence()))
        assertEquals(0, fetches)
    }

    @Test
    fun invalidOrExcessiveIssuerInputIsRejectedAtConstruction() {
        for (issuers in listOf(emptyList(), List(11) { encoded("EC256_CA") }, listOf("***"), listOf("MAA"), listOf("A".repeat(90_000)))) {
            assertFailsWith<IllegalArgumentException> {
                newEvaluator(ProximityCrlFetcher { _, _ -> error("Must not fetch") }, issuers = issuers)
            }
        }
    }

    private fun evaluator(
        fixture: String,
        scope: ProximityCrlScope = ProximityCrlScope.ReaderCertificateAndIssuingAuthorities,
        onFetch: (String, Int) -> Unit = { _, _ -> },
    ): ProximityCrlRevocationEvaluator = newEvaluator(ProximityCrlFetcher { url, limit ->
        onFetch(url, limit)
        available(fixture)
    }, scope)

    private fun newEvaluator(
        fetcher: ProximityCrlFetcher,
        scope: ProximityCrlScope = ProximityCrlScope.ReaderCertificateAndIssuingAuthorities,
        issuers: List<String> = listOf(encoded("EC256_CA")),
        at: Instant = Instant.parse("2026-09-07T21:00:00Z"),
    ): ProximityCrlRevocationEvaluator = ProximityCrlRevocationEvaluator(issuers, scope, fetcher, { at })

    private fun available(name: String): ProximityCrlFetchResult = ProximityCrlFetchResult.Available(encoded(name))
    private fun encoded(name: String): String = CrlTestFixtures.der(name).encodeToBase64Url()
    private fun evidence(): ProximityReaderEvidence = ProximityReaderEvidence(
        scope = ProximityReaderAuthenticationScope.WholeRequest,
        certificateChainDerBase64Url = listOf(encoded("EC256_LEAF")),
    )
}
