@file:OptIn(ExperimentalTime::class)

package id.walt.openid4vp.verifier

import id.walt.credentials.formats.DigitalCredential
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionCreationResponse
import id.walt.policies2.PolicyList
import id.walt.policies2.PolicyResults
import id.walt.policies2.policies.CredentialSignaturePolicy
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import io.ktor.http.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


@OptIn(ExperimentalUuidApi::class)
@Serializable
data class Verification2Session(
    /**
     * Verification Session ID (does not have to equal any OpenID4VP nonce or state)
     */
    val id: String = Uuid.random().toString(),

    val creationDate: Instant = Clock.System.now(),

    /**
     * (Optional) Expiration time
     * Verification Session will expire if it is left unused (no presentation is pushed to the session)
     *
     * **The Session is no longer eligible for expiry if it was used and presentation data was made available**
     * (no matter if successful or failed)
     */
    val expirationDate: Instant? = Clock.System.now().plus(10, DateTimeUnit.MINUTE, TimeZone.UTC),

    /**
     * (Optional) Retention date.
     * Date until when this Verification Session is kept (if it hasn't expired before that time already).
     *
     * After this date, it will be deleted with all associated verification information.
     * If you would like to keep data of successful/failed Verification Sessions indefinitely, set the retentionDate to null.
     */
    val retentionDate: Instant = Clock.System.now().plus(10, DateTimeUnit.YEAR, TimeZone.UTC),

    /**
     * Current status for this Verification Session
     */
    var status: VerificationSessionStatus,
    var attempted: Boolean = false,

    val notifications: KtorSessionNotifications? = null,

    val reattemptable: Boolean = true,

    /**
     * Minimal authorization request if request is provided by URL
     */
    val bootstrapAuthorizationRequest: AuthorizationRequest,
    val bootstrapAuthorizationRequestUrl: Url,

    /**
     * OpenID4VP Authorization Request used for this Verification Session
     */
    val authorizationRequest: AuthorizationRequest,
    val authorizationRequestUrl: Url,

    val policies: DefinedVerificationPolicies = DefinedVerificationPolicies(),
    var policyResults: PolicyResults? = null,

    val redirects: VerificationSessionRedirects? = null,

    var presentedRawData: PresentedRawData? = null,
    var presentedCredentials: Map<String, List<DigitalCredential>>? = null
) {

    @Serializable
    data class VerificationSessionNotifications(
        val webhook: VerificationSessionWebhookNotification? = null
    ) {
        @Serializable
        data class VerificationSessionWebhookNotification(
            val url: String,
            val basicAuthUser: String? = null,
            val basicAuthPass: String? = null,
            val bearerToken: String? = null
        )
    }

    @Serializable
    data class PresentedRawData(
        val vpToken: Map<String, List<String>>,
        val state: String,
    )

    @Serializable
    data class DefinedVerificationPolicies(
        // val vpPolicies: PolicyList = PolicyList(listOf(CredentialSignaturePolicy())), // TODO: vpPolicies
        val vcPolicies: PolicyList = PolicyList(listOf(CredentialSignaturePolicy())),
        val specificVcPolicies: Map<String, PolicyList> = emptyMap(),
    )

    @Serializable
    data class VerificationSessionRedirects(
        val successRedirectUri: String,
        val errorRedirectUri: String
    )


    enum class VerificationSessionStatus(val successful: Boolean? = null) {
        UNKNOWN,

        ACTIVE,
        UNUSED,

        IN_USE,
        PROCESSING_FLOW,

        EXPIRED(false),

        SUCCESSFUL(true),
        FAILED(false),
    }

    fun toSessionCreationResponse(): VerificationSessionCreationResponse {
        return VerificationSessionCreationResponse(
            sessionId = id,
            bootstrapAuthorizationRequestUrl = bootstrapAuthorizationRequest.toHttpUrl().toString(),
            fullAuthorizationRequestUrl = authorizationRequest.toHttpUrl().toString(),
            creationTarget = null
        )
    }
}
