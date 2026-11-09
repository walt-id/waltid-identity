package id.walt.trust.decision

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
}
