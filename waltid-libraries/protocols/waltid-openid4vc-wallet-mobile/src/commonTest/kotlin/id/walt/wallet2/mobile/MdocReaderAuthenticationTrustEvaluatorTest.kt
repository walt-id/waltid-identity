package id.walt.wallet2.mobile

import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.readertrust.ReaderAuthenticationEvidence
import id.walt.mdoc.readertrust.ReaderAuthenticationScope
import id.walt.mdoc.readertrust.ReaderTrustDecision
import id.walt.mdoc.readertrust.ReaderTrustState
import id.walt.mdoc.readertrust.MdocReaderAuthenticationTrustEvaluator
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class MdocReaderAuthenticationTrustEvaluatorTest {

    private fun evidence(vararg chain: ByteString) =
        ReaderAuthenticationEvidence(ReaderAuthenticationScope.WholeRequest, certificateChainDer = chain.toList())

    private fun assertNoPolicy(decision: ReaderTrustDecision) {
        assertEquals(ReaderTrustState.VALID_BUT_UNTRUSTED, decision.state)
        assertTrue(decision.reason.orEmpty().contains("no reader trust policy is configured"), "${decision.reason}")
    }

    private fun assertCertificateInvalid(decision: ReaderTrustDecision) {
        assertEquals(ReaderTrustState.VALID_BUT_UNTRUSTED, decision.state)
        assertTrue(
            decision.reason.orEmpty().startsWith("Reader authentication certificate is not valid"),
            "${decision.reason}"
        )
    }

    @Test
    fun `empty chain is untrusted because no policy is configured`() = runTest {
        assertNoPolicy(MdocReaderAuthenticationTrustEvaluator.evaluate(evidence()))
    }

    @Test
    fun `profile conforming reader certificate is untrusted because no policy is configured`() = runTest {
        val fixture = ReaderCertificateProfileFixture.create(CryptoRuntime(defaultSoftwareKeyProviders()))

        assertNoPolicy(MdocReaderAuthenticationTrustEvaluator.evaluate(evidence(fixture.leaf.encodedDer)))
        assertNoPolicy(
            MdocReaderAuthenticationTrustEvaluator.evaluate(evidence(fixture.leaf.encodedDer, fixture.root.encodedDer))
        )
    }

    @Test
    fun `reader certificate violating the profile is reported`() = runTest {
        val fixture = ReaderCertificateProfileFixture.create(CryptoRuntime(defaultSoftwareKeyProviders()))
        // Reader-authentication extended key usage is absent.
        val wrongEku = ByteString(fixture.modified("16"))

        assertCertificateInvalid(MdocReaderAuthenticationTrustEvaluator.evaluate(evidence(wrongEku)))
    }

    @Test
    fun `expired reader certificate is reported`() = runTest {
        val fixture = ReaderCertificateProfileFixture.create(CryptoRuntime(defaultSoftwareKeyProviders()))
        val evaluator = MdocReaderAuthenticationTrustEvaluator(OffsetClock((-10_000).days))

        assertCertificateInvalid(evaluator.evaluate(evidence(fixture.leaf.encodedDer)))
    }

    @Test
    fun `unparsable certificate is reported`() = runTest {
        val decision = MdocReaderAuthenticationTrustEvaluator.evaluate(evidence(ByteString(byteArrayOf(1, 2, 3))))

        assertCertificateInvalid(decision)
        assertTrue(decision.reason.orEmpty().contains("Failed to parse certificate"), "${decision.reason}")
    }

    @Test
    fun `empty trust store behaves like no trust store`() = runTest {
        val fixture = ReaderCertificateProfileFixture.create(CryptoRuntime(defaultSoftwareKeyProviders()))
        val evaluator = MdocReaderAuthenticationTrustEvaluator(trustStore = InMemoryTrustStore())

        assertNoPolicy(evaluator.evaluate(evidence(fixture.leaf.encodedDer)))
    }

    @Test
    fun `reader chaining to a trust store anchor is trusted`() = runTest {
        val fixture = ReaderCertificateProfileFixture.create(CryptoRuntime(defaultSoftwareKeyProviders()))
        val evaluator = MdocReaderAuthenticationTrustEvaluator(trustStore = InMemoryTrustStore(listOf(fixture.root)))

        assertEquals(ReaderTrustState.TRUSTED, evaluator.evaluate(evidence(fixture.leaf.encodedDer)).state)
        assertEquals(
            ReaderTrustState.TRUSTED,
            evaluator.evaluate(evidence(fixture.leaf.encodedDer, fixture.root.encodedDer)).state,
        )
    }

    @Test
    fun `reader chaining to an unrelated anchor is untrusted`() = runTest {
        val runtime = CryptoRuntime(defaultSoftwareKeyProviders())
        val fixture = ReaderCertificateProfileFixture.create(runtime)
        val unrelated = ReaderCertificateProfileFixture.create(runtime)
        val evaluator = MdocReaderAuthenticationTrustEvaluator(trustStore = InMemoryTrustStore(listOf(unrelated.root)))

        val decision = evaluator.evaluate(evidence(fixture.leaf.encodedDer, fixture.root.encodedDer))
        assertEquals(ReaderTrustState.VALID_BUT_UNTRUSTED, decision.state)
        assertTrue(decision.reason.orEmpty().contains("configured trust anchor"), "${decision.reason}")
    }

    @Test
    fun `reader end entity in the trust store is not its own anchor`() = runTest {
        val fixture = ReaderCertificateProfileFixture.create(CryptoRuntime(defaultSoftwareKeyProviders()))
        val evaluator = MdocReaderAuthenticationTrustEvaluator(trustStore = InMemoryTrustStore(listOf(fixture.leaf)))

        assertEquals(
            ReaderTrustState.VALID_BUT_UNTRUSTED,
            evaluator.evaluate(evidence(fixture.leaf.encodedDer)).state,
        )
    }

    @Test
    fun `profile violation is reported even when the reader chains to an anchor`() = runTest {
        val fixture = ReaderCertificateProfileFixture.create(CryptoRuntime(defaultSoftwareKeyProviders()))
        val evaluator = MdocReaderAuthenticationTrustEvaluator(trustStore = InMemoryTrustStore(listOf(fixture.root)))

        assertCertificateInvalid(evaluator.evaluate(evidence(ByteString(fixture.modified("16")))))
    }
}
