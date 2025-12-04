@file:OptIn(ExperimentalTime::class)

package id.walt.openid4vp.verifier.data

import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.presentations.formats.VerifiablePresentation
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.openid4vp.verifier.handlers.sessioncreation.VerificationSessionCreator.VerificationSessionCreationResponse
import id.walt.openid4vp.verifier.verification2.Verifier2PolicyResults
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import io.ktor.http.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.SerialName
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
    val bootstrapAuthorizationRequest: AuthorizationRequest? = null,
    val bootstrapAuthorizationRequestUrl: Url? = null,

    /**
     * OpenID4VP Authorization Request used for this Verification Session
     */
    val authorizationRequest: AuthorizationRequest,
    val authorizationRequestUrl: Url,

    val signedAuthorizationRequestJwt: String? = null,
    val ephemeralDecryptionKey: DirectSerializedKey? = null,
    /** JWK Thumbprint for [ephemeralDecryptionKey] */
    val jwkThumbprint: String? = null,

    /**
     * OpenID4VP configuration for this Verification Session
     */
    val requestMode: RequestMode,

    /**
     * Policies
     */
    val policies: DefinedVerificationPolicies = DefinedVerificationPolicies(),
    var policyResults: Verifier2PolicyResults? = null,

    val redirects: VerificationSessionRedirects? = null,

    /**
     * Presented data
     */
    var presentedRawData: PresentedRawData? = null,
    var presentedPresentations: Map<String, VerifiablePresentation>? = null,
    var presentedCredentials: Map<String, List<DigitalCredential>>? = null,
    var statusReason: String? = null,
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
        val state: String?
    )

    @Serializable
    data class DefinedVerificationPolicies(
        /** Policies to run on the presentations (Policies from: waltid-verification-policies2-vp) */
        @SerialName("vp_policies")
        val vp_policies: VPPolicyList? = null,

        /** Policies to run on the credentials (Policies from: waltid-verification-policies2) */
        @SerialName("vc_policies")
        val vc_policies: VCPolicyList? = null,

        /** Policies to run on specific credential ids (Policies from: waltid-verification-policies2) */
        @SerialName("specific_vc_policies")
        val specific_vc_policies: Map<String, VCPolicyList>? = null
    )

    @Serializable
    data class VerificationSessionRedirects(
        @SerialName("success_redirect_uri")
        val successRedirectUri: String? = null,
        @SerialName("error_redirect_uri")
        val errorRedirectUri: String? = null
    )


    enum class VerificationSessionStatus(val successful: Boolean? = null) {
        /** Session ended up in unknown flow (should be avoided if possible) */
        UNKNOWN,

        /** Session was created and is active (and can be used) */
        ACTIVE,

        /** Session was not used yet, but is not yet expired (and can be used) */
        UNUSED,

        /** Session is in use
         * (AuthorizationRequest was requested)
         */
        IN_USE,

        /** Checking if received presentation will be processed (validated etc) */
        VALIDATING_RECEIVED_REQUEST,

        /** Received presentation is being processed (presentation validation + verification policies) */
        PROCESSING_FLOW,

        /** Verification request expired without being utilized */
        EXPIRED(false),

        /** Verification request was completed fully successfully (all validation & verification policies passed) */
        SUCCESSFUL(true),

        /** Verification request was unsuccessful (presentation validation or verification requests failed) */
        FAILED(false),
        UNSUCCESSFUL(false),
    }

    fun toSessionCreationResponse(): VerificationSessionCreationResponse {
        return VerificationSessionCreationResponse(
            sessionId = id,
            bootstrapAuthorizationRequestUrl = bootstrapAuthorizationRequestUrl,
            fullAuthorizationRequestUrl = authorizationRequestUrl,
            creationTarget = null
        )
    }

    enum class RequestMode {
        /**
         * All parameters in the query string, unsigned.
         * Use only for simple, same-device flows (or not at all)
         */
        URL_ENCODED,

        /**
         * Signed Request by Value (can Authenticate the Verifier).
         */
        URL_ENCODED_SIGNED,

        /**
         * Unsigned Request by Reference
         * Always use for cross-device flows (QR codes), large requests
         */
        REQUEST_URI,

        /**
         * Signed Request by Reference
         * Always use for cross-device flows (QR codes), large requests
         */
        REQUEST_URI_SIGNED,

        /**
         * Data object passed to a platform API (optionally signed)
         */
        DC_API
    }
}
