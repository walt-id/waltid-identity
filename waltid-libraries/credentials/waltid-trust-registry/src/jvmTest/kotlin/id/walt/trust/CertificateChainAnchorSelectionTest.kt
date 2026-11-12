package id.walt.trust

import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.ServiceIdentity
import id.walt.trust.model.SourceAssurance
import id.walt.trust.model.SourceFamily
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustSource
import id.walt.trust.model.TrustStatus
import id.walt.trust.model.TrustedEntity
import id.walt.trust.model.TrustedEntityType
import id.walt.trust.model.TrustedService
import id.walt.trust.service.DefaultTrustRegistryService
import id.walt.trust.store.InMemoryTrustStore
import kotlinx.coroutines.test.runTest
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

/** Resolve selects anchors by subject before building a path; none of these valid paths may be lost. */
class CertificateChainAnchorSelectionTest {

    @Test
    fun intermediatePresentedAndRegistryRootOmitted() = runTest {
        val chain = TestCertificates.createChainWithIntermediate("Selection")

        val decision = resolve(chain.leaf, chain.intermediate, anchors = mapOf("root" to der(chain.root)))

        assertTrusted("root", decision)
    }

    @Test
    fun registryPinsTheIntermediateThatIssuedTheLeaf() = runTest {
        val chain = TestCertificates.createChainWithIntermediate("Selection")

        val decision = resolve(chain.leaf, anchors = mapOf("intermediate" to der(chain.intermediate)))

        assertTrusted("intermediate", decision)
    }

    @Test
    fun registryPinsTheLeafItself() = runTest {
        val chain = TestCertificates.createChain("Selection")

        val decision = resolve(chain.leaf, anchors = mapOf("leaf" to der(chain.leaf)))

        assertTrusted("leaf", decision)
    }

    @Test
    fun onlyTheIssuingRootOfTwoSameNamedRootsMatches() = runTest {
        val other = TestCertificates.createChain("Twin")
        val issuing = TestCertificates.createChain("Twin")
        check(other.root.subjectX500Principal == issuing.root.subjectX500Principal)

        val decision = resolve(issuing.leaf, anchors = mapOf("other" to der(other.root), "issuing" to der(issuing.root)))

        assertTrusted("issuing", decision)
    }

    @Test
    fun unparseableStoredCertificateDoesNotHideAValidAnchor() = runTest {
        val chain = TestCertificates.createChain("Selection")

        val decision = resolve(chain.leaf, anchors = mapOf("broken" to "not-a-certificate", "root" to der(chain.root)))

        assertTrusted("root", decision)
    }

    private fun der(certificate: X509Certificate) = TestCertificates.derBase64(certificate)

    /** One accepted, fresh LoTE with one wallet-provider entity per anchor, keyed by entity id. */
    private suspend fun resolve(vararg presented: X509Certificate, anchors: Map<String, String>): TrustDecision {
        val store = InMemoryTrustStore()
        store.replaceSourceData(
            TrustSource(
                SOURCE_ID, SourceFamily.LOTE, "Selection", nextUpdate = Clock.System.now() + 30.days,
                assurance = SourceAssurance(authenticityState = AuthenticityState.AUTHENTICATED, accepted = true),
            ),
            anchors.keys.map { TrustedEntity(it, SOURCE_ID, TrustedEntityType.WALLET_PROVIDER, it) },
            anchors.keys.map { TrustedService("$it-service", SOURCE_ID, it, "wallet-provider", TrustStatus.GRANTED) },
            anchors.map { (entityId, derBase64) ->
                ServiceIdentity("$entityId-identity", SOURCE_ID, entityId, "$entityId-service", certificateDerBase64 = derBase64)
            },
        )
        return DefaultTrustRegistryService(store).resolveCertificateChain(
            presented.map(::der), Clock.System.now(), TrustedEntityType.WALLET_PROVIDER, null
        )
    }

    private fun assertTrusted(entityId: String, decision: TrustDecision) {
        assertEquals(TrustDecisionCode.TRUSTED, decision.decision, decision.toString())
        assertEquals(entityId, decision.matchedEntity?.entityId)
    }

    private companion object {
        const val SOURCE_ID = "selection"
    }
}
