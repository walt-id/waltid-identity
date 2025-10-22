package id.walt.openid4vp.verifier

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.Key
import id.walt.dcql.models.DcqlQuery
import id.walt.ktornotifications.core.KtorSessionNotifications
import id.walt.openid4vp.verifier.Verification2Session.*
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class, ExperimentalSerializationApi::class)
object VerificationSessionCreator {

    private val log = KotlinLogging.logger { }

    /**
     * Defines all properties common to EVERY flow. Defined only ONCE.
     */
    @Serializable
    data class CommonConfig(
        @SerialName("dcql_query")
        val dcqlQuery: DcqlQuery,

        val signedRequest: Boolean = false,
        val encryptedResponse: Boolean = false,

        val notifications: KtorSessionNotifications? = null,

        val policies: DefinedVerificationPolicies = DefinedVerificationPolicies(),

        val clientMetadata: ClientMetadata? = null,
        val clientId: String? = null,

        val key: DirectSerializedKey? = null,
        val x5c: List<String>? = null
    )

    /**
     * Defines properties common to URL-based flows. Defined only ONCE.
     */
    @Serializable
    data class UrlConfig(
        val urlHost: String,
        val urlPrefix: String? = null
    )

    /**
     * The polymorphic base type. Implementations will be composed of the config blocks.
     */
    @Serializable
    @JsonClassDiscriminator("flowType")
    sealed interface BaseVerificationSessionSetup {
        // Expose the common config for easy, unified access from any flow type.
        val common: CommonConfig
    }

    @Serializable
    @SerialName("cross_device")
    data class CrossDeviceFlow(
        override val common: CommonConfig,
        val urlConfig: UrlConfig,

        // Properties unique to this flow
        val redirects: VerificationSessionRedirects? = null // Optional final redirect
    ) : BaseVerificationSessionSetup

    @Serializable
    @SerialName("same_device")
    data class SameDeviceFlow(
        override val common: CommonConfig,
        val urlConfig: UrlConfig,

        // Property unique to this flow
        val redirects: VerificationSessionRedirects // Required for final redirect
    ) : BaseVerificationSessionSetup

    @Serializable
    @SerialName("dc_api")
    data class DcApiFlow(
        override val common: CommonConfig,

        // Property unique to this flow
        val expectedOrigins: List<String>
    ) : BaseVerificationSessionSetup

    @Serializable
    data class VerificationSessionSetup(
        val preset: VerificationSessionSetupPreset? = null,

        @SerialName("dcql_query")
        val dcqlQuery: DcqlQuery,

        val signedRequest: Boolean = false,
        val encryptedResponse: Boolean = false,
        val notifications: KtorSessionNotifications? = null,
        val redirects: VerificationSessionRedirects? = null,

        val policies: DefinedVerificationPolicies = DefinedVerificationPolicies(),

        val clientMetadata: ClientMetadata? = null,
        val clientId: String? = null,
        val urlPrefix: String? = null,
        val urlHost: String? = null,
        val key: DirectSerializedKey? = null,
        val x5c: List<String>? = null
    ) {
        @Serializable
        enum class VerificationSessionSetupPreset {
            @SerialName("cross_device_flow")
            CROSS_DEVICE_FLOW,

            @SerialName("same_device_flow")
            SAME_DEVICE_FLOW,
        }
    }

    @Serializable
    data class VerificationSessionCreationResponse(
        val sessionId: String,
        val bootstrapAuthorizationRequestUrl: Url?,
        val fullAuthorizationRequestUrl: Url,
        val creationTarget: String? = null
    )

    suspend fun createVerificationSession(
        setup: VerificationSessionSetup,

        clientId: String,
        clientMetadata: ClientMetadata? = null,
        urlPrefix: String?,
        urlHost: String = "openid4vp://authorize",

        key: Key? = null,
        x5c: List<String>? = null,
    ): Verification2Session {
        setup.dcqlQuery.precheck()

        val isSignedRequest = setup.signedRequest
        if (isSignedRequest)
            requireNotNull(key) { "Requested signed request, but did not provide a key (to sign request with)!" }

        val isEncryptedResponse = setup.encryptedResponse
        if (isEncryptedResponse)
            requireNotNull(key) { "Requested encrypted response, but did not provide a key (to decrypt response with)!" }

        val isCrossDevice = true // TODO: `setup is CrossDeviceFlow`

        val isDcApi = false // TODO: `setup is DcApiFlow`
        if (isDcApi) {
            require(urlPrefix == null) { "URL prefix is not used for DC API" }
            require(!urlHost.startsWith("openid4vp://authorize")) { "URL Host has to be set to the DC API origin" }
            if (isSignedRequest) {
                //requireNotNull(setup.expectedOrigins)
            }
        }

        require(isCrossDevice || isDcApi) { "No flow is selected" } // list all flows here

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

        val bootstrapAuthorizationRequest = if (isDcApi) null
        else AuthorizationRequest(
            // TODO: url building (handle host alias)
            requestUri = "$urlPrefix/$sessionId/request",

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
            responseUri = when {
                isDcApi -> null
                isCrossDevice -> "$urlPrefix/$sessionId/response" // For Cross-Device flow (direct_post, direct_post.jwt)
                else -> throw IllegalStateException("No flow is selected")
            },
            scope = null,//OPTIONAL. OAuth 2.0 Scope value. Can be used for pre-defined DCQL queries or OpenID Connect scopes (e.g., "openid").
            state = state, // Opaque value used by the Verifier to maintain state between the request and callback.
            nonce = nonce, // String value used to mitigate replay attacks. Also used to establish holder binding.
            responseMode = when {
                isDcApi && setup.encryptedResponse -> OpenID4VPResponseMode.DC_API
                isDcApi -> OpenID4VPResponseMode.DC_API
                isCrossDevice && setup.encryptedResponse -> OpenID4VPResponseMode.DIRECT_POST_JWT
                isCrossDevice -> OpenID4VPResponseMode.DIRECT_POST
                else -> throw IllegalStateException("No flow is selected")
            },
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
            expectedOrigins = if (isDcApi && isSignedRequest) listOf("") /*setup.expectedOrigins*/ else null, // TODO: Fill in here
        )
        log.trace { "Constructed AuthorizationRequest: $authorizationRequest" }

        val authorizationRequestUrl = authorizationRequest.toHttpUrl(URLBuilder(urlHost))
        val bootstrapAuthorizationRequestUrl = bootstrapAuthorizationRequest?.toHttpUrl(URLBuilder(urlHost))

        val now = Clock.System.now()
        val expiration = now.plus(5, DateTimeUnit.MINUTE, TimeZone.UTC)
        val retentionDate = now.plus(10, DateTimeUnit.YEAR, TimeZone.UTC)

        val signedAuthorizationRequest = if (isSignedRequest) {
            requireNotNull(key)

            val headers = hashMapOf<String, JsonElement>("typ" to JsonPrimitive("oauth-authz-req+jwt"))
            if (x5c != null) headers["x5c"] = JsonArray(x5c.map { JsonPrimitive(it) })
            if (expiration != null) headers["exp"] = JsonPrimitive(expiration.epochSeconds)
            headers["iat"] = JsonPrimitive(now.epochSeconds)

            key.signJws(Json.encodeToString(authorizationRequest).encodeToByteArray(), headers)
        } else null

        val newSession = Verification2Session(
            id = sessionId,

            creationDate = now,
            expirationDate = expiration,
            retentionDate = retentionDate,

            status = if (expiration != null) VerificationSessionStatus.UNUSED else VerificationSessionStatus.ACTIVE,

            bootstrapAuthorizationRequest = bootstrapAuthorizationRequest,
            bootstrapAuthorizationRequestUrl = bootstrapAuthorizationRequestUrl,

            authorizationRequest = authorizationRequest,
            authorizationRequestUrl = authorizationRequestUrl,
            signedAuthorizationRequestJwt = signedAuthorizationRequest,

            requestMode = if (setup.signedRequest) RequestMode.REQUEST_URI_SIGNED else RequestMode.REQUEST_URI,

            policies = setup.policies,
            notifications = setup.notifications,
            redirects = setup.redirects
        )
        log.trace { "New Verification2Session: $newSession" }

        return newSession
    }

}
