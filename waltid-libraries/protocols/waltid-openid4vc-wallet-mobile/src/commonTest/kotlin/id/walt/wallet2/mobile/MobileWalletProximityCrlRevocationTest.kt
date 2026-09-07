package id.walt.wallet2.mobile

import id.walt.certificate.x509.revocation.CrlTestFixtures
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Instant

class MobileWalletProximityCrlRevocationTest {
    @Test
    fun completeCrlChecksLeafAndConfiguredAuthorityWithOneFetchPerUrl() = runTest {
        val calls = mutableListOf<String>()
        val evaluator = evaluator("EC256_GOOD") { url, limit ->
            calls += url
            assertEquals(2_097_152, limit)
        }
        assertEquals(MobileWalletProximityCertificateRevocationResult.Good, evaluator.evaluate(evidence()))
        assertEquals(listOf("https://crl.example.test/EC256.crl"), calls)
        // There is no SDK cache across evaluations; freshness and transport policy are reconsidered.
        assertEquals(MobileWalletProximityCertificateRevocationResult.Good, evaluator.evaluate(evidence()))
        assertEquals(2, calls.size)
    }

    @Test
    fun revokedAuthorityIsDetectedEvenWhenTheReaderCertificateIsNotListed() = runTest {
        val revoked = evaluator("CA_ONLY_REVOKED").evaluate(evidence())
        assertEquals(MobileWalletProximityCertificateRevocationResult.Revoked("Reader certificate authority is revoked"), revoked)
        val leafOnly = evaluator("CA_ONLY_REVOKED", MobileWalletProximityCrlScope.ReaderCertificate).evaluate(evidence())
        assertEquals(MobileWalletProximityCertificateRevocationResult.Good, leafOnly)
    }

    @Test
    fun invalidUnsupportedAndUnavailableCrlsRemainIndeterminate() = runTest {
        for (fixture in listOf("UNKNOWN_CRITICAL", "PARTITIONED", "DELTA", "WRONG_KEY")) {
            assertIs<MobileWalletProximityCertificateRevocationResult.Indeterminate>(evaluator(fixture).evaluate(evidence()), fixture)
        }
        val unavailable = newEvaluator(MobileWalletProximityCrlFetcher { _, _ -> MobileWalletProximityCrlFetchResult.Unavailable })
        assertIs<MobileWalletProximityCertificateRevocationResult.Indeterminate>(unavailable.evaluate(evidence()))
        for (invalid in listOf("***", "A".repeat(2_800_000))) {
            val malformed = newEvaluator(MobileWalletProximityCrlFetcher { _, _ -> MobileWalletProximityCrlFetchResult.Available(invalid) })
            assertIs<MobileWalletProximityCertificateRevocationResult.Indeterminate>(malformed.evaluate(evidence()))
        }
        val expired = newEvaluator(MobileWalletProximityCrlFetcher { _, _ -> available("EC256_GOOD") }, at = Instant.parse("2026-09-08T04:00:00Z"))
        assertIs<MobileWalletProximityCertificateRevocationResult.Indeterminate>(expired.evaluate(evidence()))
    }

    @Test
    fun configuredIssuerSnapshotDoesNotTrustUnrelatedCertificateInput() = runTest {
        val configured = mutableListOf(encoded("EC256_CA"))
        val evaluator = newEvaluator(MobileWalletProximityCrlFetcher { _, _ -> available("EC256_GOOD") }, issuers = configured)
        configured.clear()
        configured += encoded("EC384_CA")
        assertEquals(MobileWalletProximityCertificateRevocationResult.Good, evaluator.evaluate(evidence()))
        var fetches = 0
        val unknown = newEvaluator(MobileWalletProximityCrlFetcher { _, _ ->
            fetches++
            available("EC384_GOOD")
        }, issuers = listOf(encoded("EC384_CA")))
        assertIs<MobileWalletProximityCertificateRevocationResult.Indeterminate>(unknown.evaluate(evidence()))
        assertEquals(0, fetches)
    }

    @Test
    fun fetchCancellationIsNotConvertedToAnIndeterminateStatus() = runTest {
        val evaluator = newEvaluator(MobileWalletProximityCrlFetcher { _, _ -> throw CancellationException("Cancelled CRL fetch") })
        assertFailsWith<CancellationException> { evaluator.evaluate(evidence()) }
    }

    @Test
    fun ambiguousIssuerCertificatesDoNotSelectAnArbitraryPath() = runTest {
        var fetches = 0
        val evaluator = newEvaluator(MobileWalletProximityCrlFetcher { _, _ ->
            fetches++
            available("EC256_GOOD")
        }, issuers = listOf(encoded("EC256_CA"), encoded("ISSUER_EXPIRED")))
        assertIs<MobileWalletProximityCertificateRevocationResult.Indeterminate>(evaluator.evaluate(evidence()))
        assertEquals(0, fetches)
    }

    @Test
    fun invalidOrExcessiveIssuerInputIsRejectedAtConstruction() {
        for (issuers in listOf(emptyList(), List(11) { encoded("EC256_CA") }, listOf("***"), listOf("A".repeat(90_000)))) {
            assertFailsWith<IllegalArgumentException> {
                newEvaluator(MobileWalletProximityCrlFetcher { _, _ -> error("Must not fetch") }, issuers = issuers)
            }
        }
    }

    private fun evaluator(
        fixture: String,
        scope: MobileWalletProximityCrlScope = MobileWalletProximityCrlScope.ReaderCertificateAndIssuingAuthorities,
        onFetch: (String, Int) -> Unit = { _, _ -> },
    ): MobileWalletProximityCrlRevocationEvaluator = newEvaluator(MobileWalletProximityCrlFetcher { url, limit ->
        onFetch(url, limit)
        available(fixture)
    }, scope)

    private fun newEvaluator(
        fetcher: MobileWalletProximityCrlFetcher,
        scope: MobileWalletProximityCrlScope = MobileWalletProximityCrlScope.ReaderCertificateAndIssuingAuthorities,
        issuers: List<String> = listOf(encoded("EC256_CA")),
        at: Instant = Instant.parse("2026-09-07T21:00:00Z"),
    ): MobileWalletProximityCrlRevocationEvaluator = MobileWalletProximityCrlRevocationEvaluator(issuers, scope, fetcher, { at })

    private fun available(name: String): MobileWalletProximityCrlFetchResult = MobileWalletProximityCrlFetchResult.Available(encoded(name))
    private fun encoded(name: String): String = CrlTestFixtures.der(name).encodeToBase64Url()
    private fun evidence(): MobileWalletProximityReaderEvidence = MobileWalletProximityReaderEvidence(
        scope = MobileWalletProximityReaderAuthenticationScope.WholeRequest,
        certificateChainDerBase64Url = listOf(encoded("EC256_LEAF")),
    )
}
