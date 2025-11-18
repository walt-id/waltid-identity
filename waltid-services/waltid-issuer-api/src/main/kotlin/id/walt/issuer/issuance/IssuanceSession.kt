@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.issuance

import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.TxCode
import id.walt.oid4vc.providers.AuthorizationSession
import id.walt.oid4vc.requests.AuthorizationRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
enum class EnterpriseIssuanceStatus {
    // Success path
    created,
    offer_sent,
    credential_requested,
    credential_issued,
    completed,

    // Unhappy paths (terminal)
    exchange_unsuccessful,
    credential_rejected_by_user,
    issuer_error,
    wallet_error
}

@Serializable
data class IssuanceFailureDetail(
    val reason: String,
    val description: String? = null,
)

@Serializable
data class IssuanceStatusTransition(
    val from: String?,
    val to: String,
    val timestamp: Instant,
    val failure: IssuanceFailureDetail? = null,
)

@Serializable
data class EnterpriseIssuanceStatusState(
    val status: EnterpriseIssuanceStatus = EnterpriseIssuanceStatus.created,
    val transitions: List<IssuanceStatusTransition> = emptyList(),
    val failure: IssuanceFailureDetail? = null,
)

@Serializable
data class IssuanceSession(
    override val id: String,
    override val authorizationRequest: AuthorizationRequest?,
    override val expirationTimestamp: Instant,
    val issuanceRequests: List<IssuanceRequest>,
    val txCode: TxCode? = null,
    val txCodeValue: String? = null,
    override val authServerState: String? = null, //the state used for additional authentication with pwd, id_token or vp_token.
    val credentialOffer: CredentialOffer? = null,
    val cNonce: String? = null,
    val callbackUrl: String? = null,
    val customParameters: Map<String, JsonElement>? = null,
    // Enterprise issuance status tracking
    val enterpriseStatusState: EnterpriseIssuanceStatusState = EnterpriseIssuanceStatusState(
        transitions = listOf(
            IssuanceStatusTransition(
                from = null,
                to = EnterpriseIssuanceStatus.created.name,
                timestamp = Clock.System.now() // use actual now at instantiation
            )
        )
    ),
) : AuthorizationSession() {
    val enterpriseStatus: EnterpriseIssuanceStatus get() = enterpriseStatusState.status

    fun isEnterpriseTerminal(status: EnterpriseIssuanceStatus = enterpriseStatus): Boolean = status in setOf(
        EnterpriseIssuanceStatus.credential_issued,
        EnterpriseIssuanceStatus.completed,
        EnterpriseIssuanceStatus.exchange_unsuccessful,
        EnterpriseIssuanceStatus.credential_rejected_by_user
    )

    fun nextEnterpriseStatus(
        newStatus: EnterpriseIssuanceStatus,
        reason: String? = null,
        description: String? = null,
    ): IssuanceSession {
        if (isEnterpriseTerminal()) return this
        val failure = if (newStatus in setOf(
                EnterpriseIssuanceStatus.exchange_unsuccessful,
                EnterpriseIssuanceStatus.credential_rejected_by_user,
                EnterpriseIssuanceStatus.issuer_error,
                EnterpriseIssuanceStatus.wallet_error
            ) && reason != null
        ) IssuanceFailureDetail(reason, description) else null
        val newTransitions = enterpriseStatusState.transitions + IssuanceStatusTransition(
            from = enterpriseStatusState.status.name,
            to = newStatus.name,
            timestamp = Clock.System.now(),
            failure = failure
        )
        return this.copy(
            enterpriseStatusState = enterpriseStatusState.copy(
                status = newStatus,
                transitions = newTransitions,
                failure = failure
            )
        )
    }
}
