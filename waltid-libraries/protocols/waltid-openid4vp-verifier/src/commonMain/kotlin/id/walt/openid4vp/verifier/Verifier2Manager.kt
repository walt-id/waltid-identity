@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package id.walt.openid4vp.verifier

import id.walt.dcql.models.DcqlQuery
import id.walt.openid4vp.verifier.Verification2Session.DefinedVerificationPolicies
import id.walt.openid4vp.verifier.Verification2Session.VerificationSessionStatus
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object Verifier2Manager {

    private val log = KotlinLogging.logger("Verifier2Service")

    @Serializable
    data class VerificationSessionSetup(
        val preset: VerificationSessionSetupPreset? = null,
        @SerialName("dcql_query")

        val dcqlQuery: DcqlQuery,

        val policies: DefinedVerificationPolicies = DefinedVerificationPolicies()
    ) {
        @Serializable
        enum class VerificationSessionSetupPreset {
            @SerialName("cross_device_flow")
            CROSS_DEVICE_FLOW,

            @SerialName("same_device_flow")
            SAME_DEVICE_FLOW
        }
    }

    @Serializable
    data class VerificationSessionCreationResponse(
        val sessionId: String,
        val bootstrapAuthorizationRequestUrl: String,
        val fullAuthorizationRequestUrl: String,
        val creationTarget: String? = null
    )

    suspend fun createVerificationSession(
        setup: VerificationSessionSetup,

        clientId: String,
        clientMetadata: ClientMetadata? = null,
        uriPrefix: String,
    ): Verification2Session {
        setup.dcqlQuery.precheck()

        val sessionId = Uuid.random().toString()
        val nonce = Uuid.random().toString()
        val state = Uuid.random().toString()

//        ClientMetadata(
//            clientName = "Badge Verifier",
//            logoUri = "https://xyz.example/logo.png",
//            vpFormatsSupported = mapOf(
//                "jwt_vc_json" to JsonObject(
//                    mapOf("alg_values" to JsonArray(listOf("RSA", "ES256", "ES256K", "EdDSA").map { JsonPrimitive(it) }))
//                )
//            )
//        )


        // TODO: Build AuthorizationRequest based on preset

        val bootstrapAuthorizationRequest = AuthorizationRequest(
            // TODO: url building (handle host alias)
            requestUri = "$uriPrefix/$sessionId/request",

            /*
             * OPTIONAL. A string determining the HTTP method to be used when 'request_uri' is present.
             * Valid values: "get", "post". Defaults to "get" if not present.
             * MUST NOT be present if 'request_uri' is not present.
             */
            //requestUriMethod = RequestUriHttpMethod.GET,

            clientId = clientId,

            nonce = null, // not required in the initial request yet
            responseType = null
        )

        val authorizationRequest = AuthorizationRequest(
            responseType = OpenID4VPResponseType.VP_TOKEN,
            clientId = clientId,
            redirectUri = null, // For Same-Device flow (fragment/query/after code exchange etc)
            // TODO: url building (handle host alias)
            responseUri = "$uriPrefix/$sessionId/response", // For Cross-Device flow (direct_post, direct_post.jwt)
            scope = null,//OPTIONAL. OAuth 2.0 Scope value. Can be used for pre-defined DCQL queries or OpenID Connect scopes (e.g., "openid").
            state = state, // Opaque value used by the Verifier to maintain state between the request and callback.
            nonce = nonce, // String value used to mitigate replay attacks. Also used to establish holder binding.
            responseMode = OpenID4VPResponseMode.DIRECT_POST,
            // JAR (RFC 9101) Parameters (Section 5)
            /*
             * OPTIONAL. The Authorization Request parameters are represented as a JWT [RFC7519].
             * If present, this JWT contains all other Authorization Request parameters as claims.
             */
            request = null, // This would be the compact JWT string

            // OpenID4VP New Parameters (Section 5.1)
            dcqlQuery = setup.dcqlQuery, // REQUIRED (unless 'scope' parameter represents a DCQL Query).
            clientMetadata = clientMetadata,


            /*
             * OPTIONAL. Array of strings, where each string is a base64url encoded JSON object
             * containing details about the transaction the Verifier is requesting the End-User to authorize.
             * The decoded JSON object structure is represented by [TransactionDataItem].
             */
            //val transactionData : List < String >? = null, // List of base64url encoded JSON strings

            /*
             * OPTIONAL. An array of attestations about the Verifier relevant to the Credential Request.
             * Each object structure is represented by [VerifierAttestationItem].
             */
            //val verifierAttestations: List<VerifierAttestationItem>? = null,

            // SIOPv2 specific parameters (if scope includes "openid") - common but technically from SIOPv2
            /*
             * OPTIONAL (but common with SIOPv2). Specifies the type of ID Token the RP wants.
             * E.g., "subject_signed", "attester_signed".
             */
            //val idTokenType: String? = null,

            // DC API specific parameter (Appendix A.2 of draft 28)
            /*
             * REQUIRED when signed requests (Appendix A.3.2) are used with the Digital Credentials API (DC API).
             * An array of strings, each string representing an Origin of the Verifier that is making the request.
             * Not for use in unsigned requests.
             */
            //val expectedOrigins: List<String>? = null,
        )

        val now = Clock.System.now()
        val expiration = now.plus(5, DateTimeUnit.MINUTE, TimeZone.UTC)
        val retentionDate = now.plus(10, DateTimeUnit.YEAR, TimeZone.UTC)

        val newSession = Verification2Session(
            id = sessionId,
            creationDate = now,
            expirationDate = expiration,
            retentionDate = retentionDate,
            status = if (expiration != null) VerificationSessionStatus.UNUSED else VerificationSessionStatus.ACTIVE,
            bootstrapAuthorizationRequest = bootstrapAuthorizationRequest,
            bootstrapAuthorizationRequestUrl = bootstrapAuthorizationRequest.toHttpUrl(),
            authorizationRequest = authorizationRequest,
            authorizationRequestUrl = authorizationRequest.toHttpUrl(),
            policies = setup.policies
        )

        return newSession
    }

}
