package id.walt.trust.decision

import id.walt.certificate.x509.X509CertificateUtil
import id.walt.certificate.x509.truststore.InMemoryTrustStore
import id.walt.trust.model.FreshnessState
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType
import kotlinx.coroutines.test.runTest
import kotlinx.io.bytestring.ByteString
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class StaticTrustDecisionEvaluatorTest {

    private fun anchors(pem: String) = InMemoryTrustStore(listOf(X509CertificateUtil.parseCertificatePem(pem)))

    private fun leafCertificate() =
        X509CertificateUtil.parseCertificateDerEncoded(ByteString(Base64.decode(TestCertificates.LEAF_DER_BASE64)))

    private val evaluator = StaticTrustDecisionEvaluator(
        mapOf(TrustPurpose.ISSUER_WUA_SIGNER to anchors(TestCertificates.TRUSTED_ROOT_PEM))
    )

    private fun request(chain: List<String>, purpose: TrustPurpose = TrustPurpose.ISSUER_WUA_SIGNER) =
        TrustDecisionRequest(purpose, TrustSubject.CertificateChain(chain), setOf(TrustedEntityType.WALLET_PROVIDER))

    @Test
    fun chainUnderConfiguredAnchorIsTrustedWithoutFreshness() = runTest {
        val decision = evaluator.evaluate(request(listOf(TestCertificates.LEAF_DER_BASE64, TestCertificates.TRUSTED_ROOT_PEM)))

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertEquals(FreshnessState.UNKNOWN, decision.sourceFreshness)
        assertEquals(StaticTrustDecisionEvaluator.EVIDENCE_TYPE, decision.evidence.single().type)
    }

    @Test
    fun chainUnderOtherAnchorIsNotTrusted() = runTest {
        val other = StaticTrustDecisionEvaluator(
            mapOf(TrustPurpose.ISSUER_WUA_SIGNER to anchors(TestCertificates.UNTRUSTED_ROOT_PEM))
        )

        val decision = other.evaluate(request(listOf(TestCertificates.LEAF_DER_BASE64)))

        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
    }

    @Test
    fun pinnedLeafIsTrustedAloneAndWithItsIssuer() = runTest {
        val pinned = StaticTrustDecisionEvaluator(
            mapOf(TrustPurpose.ISSUER_WUA_SIGNER to InMemoryTrustStore(listOf(leafCertificate())))
        )

        assertEquals(TrustDecisionCode.TRUSTED, pinned.evaluate(request(listOf(TestCertificates.LEAF_DER_BASE64))).decision)
        assertEquals(
            TrustDecisionCode.TRUSTED,
            pinned.evaluate(request(listOf(TestCertificates.LEAF_DER_BASE64, TestCertificates.TRUSTED_ROOT_PEM))).decision,
        )
    }

    @Test
    fun rootWithTheAnchorsNameButAnotherKeyIsNotTrusted() = runTest {
        val decision = evaluator.evaluate(
            request(listOf(TestCertificates.ATTACKER_LEAF_DER_BASE64, TestCertificates.ATTACKER_ROOT_PEM))
        )

        assertEquals(TrustDecisionCode.NOT_TRUSTED, decision.decision)
    }

    @Test
    fun purposeWithoutAnchorsIsUnsupported() = runTest {
        val decision = evaluator.evaluate(
            request(listOf(TestCertificates.LEAF_DER_BASE64), TrustPurpose.WALLET_PRESENTATION_AUTH_REQUEST)
        )

        assertEquals(TrustDecisionCode.UNSUPPORTED_SOURCE, decision.decision)
    }

    @Test
    fun unparseableChainIsProcessingError() = runTest {
        val decision = evaluator.evaluate(request(listOf("not a certificate")))

        assertEquals(TrustDecisionCode.PROCESSING_ERROR, decision.decision)
    }

    @Test
    fun staticTrustMeetsTrustedButNotRegistryFresh() = runTest {
        val trustedChain = request(listOf(TestCertificates.LEAF_DER_BASE64))

        assertIs<TrustCheck.Accepted>(evaluator.check(trustedChain, TrustRequirement.Trusted))
        assertIs<TrustCheck.Rejected>(evaluator.check(trustedChain, TrustRequirement.RegistryFresh()))
    }
}
