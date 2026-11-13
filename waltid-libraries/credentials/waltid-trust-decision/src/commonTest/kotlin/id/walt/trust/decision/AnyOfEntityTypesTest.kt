package id.walt.trust.decision

import id.walt.trust.model.AuthenticityState
import id.walt.trust.model.FreshnessState
import id.walt.trust.model.SourceAssurance
import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AnyOfEntityTypesTest {

    private fun request(vararg types: TrustedEntityType) = TrustDecisionRequest(
        purpose = TrustPurpose.WALLET_PRESENTATION_AUTH_REQUEST,
        subject = TrustSubject.CertificateChain(listOf(TestCertificates.LEAF_DER_BASE64)),
        expectedEntityTypes = types.toSet(),
    )

    @Test
    fun noExpectedTypeAsksOnceWithoutFilter() = runTest {
        val asked = mutableListOf<TrustedEntityType?>()

        request().anyOfEntityTypes { type -> asked += type; TrustDecision(TrustDecisionCode.NOT_TRUSTED) }

        assertEquals(listOf<TrustedEntityType?>(null), asked)
    }

    @Test
    fun mostInformativeDenialWins() = runTest {
        val answers = mapOf(
            TrustedEntityType.ACCESS_CERTIFICATE_PROVIDER to TrustDecisionCode.NOT_TRUSTED,
            TrustedEntityType.RELYING_PARTY_PROVIDER to TrustDecisionCode.UNKNOWN,
            TrustedEntityType.OTHER to TrustDecisionCode.STALE_SOURCE,
        )

        val decision = request(*answers.keys.toTypedArray()).anyOfEntityTypes { type ->
            TrustDecision(answers.getValue(checkNotNull(type)))
        }

        assertEquals(TrustDecisionCode.STALE_SOURCE, decision.decision)
    }

    @Test
    fun unavailableRanksBeforeNotTrusted() = runTest {
        val decision = request(TrustedEntityType.ACCESS_CERTIFICATE_PROVIDER, TrustedEntityType.RELYING_PARTY_PROVIDER)
            .anyOfEntityTypes { type ->
                TrustDecision(if (type == TrustedEntityType.RELYING_PARTY_PROVIDER) TrustDecisionCode.UNKNOWN else TrustDecisionCode.NOT_TRUSTED)
            }

        assertEquals(TrustDecisionCode.UNKNOWN, decision.decision)
    }

    @Test
    fun stopsAtFirstTrustedDecisionThatSatisfiesEveryRequirement() = runTest {
        val asked = mutableListOf<TrustedEntityType?>()
        val types = arrayOf(
            TrustedEntityType.ACCESS_CERTIFICATE_PROVIDER,
            TrustedEntityType.RELYING_PARTY_PROVIDER,
            TrustedEntityType.OTHER,
        )

        val decision = request(*types).anyOfEntityTypes { type ->
            asked += type
            if (type == TrustedEntityType.RELYING_PARTY_PROVIDER) trusted(FreshnessState.FRESH)
            else TrustDecision(TrustDecisionCode.NOT_TRUSTED)
        }

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertEquals(listOf<TrustedEntityType?>(types[0], types[1]), asked)
    }

    @Test
    fun freshTrustedDecisionWinsOverEarlierStaleOne() = runTest {
        // PID_PROVIDER is asked before WALLET_PROVIDER
        val decision = request(TrustedEntityType.PID_PROVIDER, TrustedEntityType.WALLET_PROVIDER).anyOfEntityTypes { type ->
            trusted(if (type == TrustedEntityType.PID_PROVIDER) FreshnessState.STALE else FreshnessState.FRESH)
        }

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertEquals(FreshnessState.FRESH, decision.sourceFreshness)
    }

    @Test
    fun authenticatedSourceWinsOverEarlierUnverifiedOne() = runTest {
        val decision = request(TrustedEntityType.PID_PROVIDER, TrustedEntityType.WALLET_PROVIDER).anyOfEntityTypes { type ->
            trusted(
                FreshnessState.FRESH,
                if (type == TrustedEntityType.PID_PROVIDER) AuthenticityState.UNVERIFIED else AuthenticityState.AUTHENTICATED,
            )
        }

        assertEquals(AuthenticityState.AUTHENTICATED, decision.authenticity)
    }

    @Test
    fun staleTrustedDecisionIsKeptWhenNothingBetterFollows() = runTest {
        val decision = request(TrustedEntityType.PID_PROVIDER, TrustedEntityType.WALLET_PROVIDER).anyOfEntityTypes { type ->
            if (type == TrustedEntityType.PID_PROVIDER) trusted(FreshnessState.STALE)
            else TrustDecision(TrustDecisionCode.STALE_SOURCE, sourceFreshness = FreshnessState.EXPIRED)
        }

        assertEquals(TrustDecisionCode.TRUSTED, decision.decision)
        assertEquals(FreshnessState.STALE, decision.sourceFreshness)
    }

    private fun trusted(freshness: FreshnessState, authenticity: AuthenticityState = AuthenticityState.AUTHENTICATED) =
        TrustDecision(
            TrustDecisionCode.TRUSTED,
            sourceFreshness = freshness,
            sourceAssurance = SourceAssurance(authenticityState = authenticity, accepted = true),
        )
}
