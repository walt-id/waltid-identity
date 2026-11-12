package id.walt.trust.decision

import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.FreshnessState
import id.walt.trust.model.SourceAssurance
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntity
import id.walt.trust.model.TrustedEntityType
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class TrustRequirementTest {

    private val request = TrustDecisionRequest(
        purpose = TrustPurpose.ISSUER_WUA_SIGNER,
        subject = TrustSubject.CertificateChain(listOf(TestCertificates.LEAF_DER_BASE64)),
        expectedEntityTypes = setOf(TrustedEntityType.WALLET_PROVIDER),
    )

    private fun registryDecision(
        code: TrustDecisionCode = TrustDecisionCode.TRUSTED,
        freshness: FreshnessState = FreshnessState.FRESH,
        authenticity: AuthenticityState = AuthenticityState.INTEGRITY_VERIFIED,
        accepted: Boolean = true,
        entityType: TrustedEntityType = TrustedEntityType.WALLET_PROVIDER,
    ) = TrustDecision(
        decision = code,
        sourceFreshness = freshness,
        sourceAssurance = SourceAssurance(authenticityState = authenticity, accepted = accepted),
        matchedEntity = TrustedEntity("entity", "source", entityType, "Wallet Provider"),
    )

    private suspend fun checkWith(decision: TrustDecision, requirement: TrustRequirement) =
        TrustDecisionEvaluator { decision }.check(request, requirement)

    @Test
    fun notRequiredSkipsWithoutCallingTheEvaluator() = runTest {
        val evaluator = TrustDecisionEvaluator { error("must not be called") }

        assertEquals(TrustCheck.Skipped, evaluator.check(request, TrustRequirement.NotRequired))
    }

    @Test
    fun missingEvaluatorIsRejected() = runTest {
        val missing: TrustDecisionEvaluator? = null

        assertIs<TrustCheck.Rejected>(missing.check(request, TrustRequirement.Trusted))
    }

    @Test
    fun evaluatorFailuresAndCancellationPropagate() = runTest {
        assertFailsWith<IllegalStateException> {
            TrustDecisionEvaluator { error("store unavailable") }.check(request, TrustRequirement.Trusted)
        }
        assertFailsWith<CancellationException> {
            TrustDecisionEvaluator { throw CancellationException("cancelled") }.check(request, TrustRequirement.Trusted)
        }
    }

    @Test
    fun trustedRejectsDenialsFailedAuthenticityAndExpiredSources() = runTest {
        assertIs<TrustCheck.Accepted>(checkWith(registryDecision(freshness = FreshnessState.STALE), TrustRequirement.Trusted))
        assertIs<TrustCheck.Rejected>(checkWith(registryDecision(code = TrustDecisionCode.UNKNOWN), TrustRequirement.Trusted))
        assertIs<TrustCheck.Rejected>(checkWith(registryDecision(authenticity = AuthenticityState.FAILED), TrustRequirement.Trusted))
        assertIs<TrustCheck.Rejected>(checkWith(registryDecision(freshness = FreshnessState.EXPIRED), TrustRequirement.Trusted))
    }

    @Test
    fun trustedWithExpectedTypesNeedsAMatchedEntityOfOneOfThem() = runTest {
        val noEntity = registryDecision().copy(matchedEntity = null)

        assertIs<TrustCheck.Rejected>(checkWith(noEntity, TrustRequirement.Trusted))
        assertIs<TrustCheck.Rejected>(checkWith(registryDecision(entityType = TrustedEntityType.PID_PROVIDER), TrustRequirement.Trusted))
        assertIs<TrustCheck.Accepted>(
            TrustDecisionEvaluator { noEntity }.check(request.copy(expectedEntityTypes = emptySet()), TrustRequirement.Trusted)
        )
    }

    @Test
    fun insecureAllowAllMeetsTrustedWithExpectedTypes() = runTest {
        assertIs<TrustCheck.Accepted>(InsecureAllowAllTrustDecisionEvaluator.check(request, TrustRequirement.Trusted))
    }

    @Test
    fun registryFreshAcceptsOnlyFreshAcceptedRegistryMatches() = runTest {
        val fresh = TrustRequirement.RegistryFresh()

        assertIs<TrustCheck.Accepted>(checkWith(registryDecision(), fresh))
        assertIs<TrustCheck.Rejected>(checkWith(registryDecision(freshness = FreshnessState.STALE), fresh))
        assertIs<TrustCheck.Rejected>(checkWith(registryDecision(accepted = false), fresh))
        assertIs<TrustCheck.Rejected>(checkWith(registryDecision(entityType = TrustedEntityType.PID_PROVIDER), fresh))
        assertIs<TrustCheck.Rejected>(checkWith(registryDecision().copy(matchedEntity = null), fresh))
        assertIs<TrustCheck.Rejected>(InsecureAllowAllTrustDecisionEvaluator.check(request, fresh))
    }

    @Test
    fun registryFreshCanRequireAuthenticatedSources() = runTest {
        val authenticated = TrustRequirement.RegistryFresh(requireAuthenticated = true)

        assertIs<TrustCheck.Rejected>(checkWith(registryDecision(), authenticated))
        assertIs<TrustCheck.Accepted>(checkWith(registryDecision(authenticity = AuthenticityState.AUTHENTICATED), authenticated))
    }

    @Test
    fun registryFreshWithoutExpectedTypesIsRejectedBeforeEvaluation() = runTest {
        val evaluator = TrustDecisionEvaluator { error("must not be called") }

        val check = evaluator.check(request.copy(expectedEntityTypes = emptySet()), TrustRequirement.RegistryFresh())

        assertIs<TrustCheck.Rejected>(check)
    }
}
