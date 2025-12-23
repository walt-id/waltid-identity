package id.walt.openid4vp.verifier.handlers.sessioncreation

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vp.verifier.data.*
import id.walt.openid4vp.verifier.data.Verification2Session.DefinedVerificationPolicies
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.policies2.vp.policies.VPVerificationPolicyManager
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class, ExperimentalSerializationApi::class)
object VerificationSessionCreator {

    private val log = KotlinLogging.logger { }

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

        /** Is used to build request URL and response URL */
        urlPrefix: String?,

        /**
         * Is used to build bootstrap- & authorizationRequestUrl
         * for DC API: origin
         * */
        urlHost: String,

        // Both are required for signed requests:
        key: Key? = null,
        x5c: List<String>? = null,
    ): Verification2Session {
        val sessionId = setup.core.sessionId ?: Uuid.random().toString()

        val isSignedRequest = setup.core.signedRequest
        val isEncryptedResponse = setup.core.encryptedResponse
        val isCrossDevice = setup is CrossDeviceFlowSetup
        val isDcApi = setup is DcApiFlowSetup
        val isDcApiHaip = isDcApi && setup.haip

        var ephemeralKey: JWKKey? = null

        if (isDcApi) {
            require(urlPrefix == null) { "URL prefix is not used for DC API" }
            require(!urlHost.startsWith("openid4vp://authorize")) { "URL Host has to be set to the DC API origin" }
        }

        val effectiveClientMetadata = if (isDcApi && isEncryptedResponse) {

            val keyType = KeyType.secp256r1

            if (isDcApiHaip) {
                // HAIP mandates P-256 (secp256r1)
                require(keyType == KeyType.secp256r1) { "HAIP profile requires P-256 keys" }
            }

            // Generate P-256 Ephemeral Key
            ephemeralKey = JWKKey.generate(keyType)

            // Construct JWKS
            val jwks = ClientMetadata.Jwks(
                listOf(
                    JsonObject(
                        ephemeralKey.getPublicKey().exportJWKObject()
                            .toMutableMap().apply {
                                set("alg", JsonPrimitive("ECDH-ES"))
                                set("use", JsonPrimitive("enc"))
                            }
                    )
                )
            )
            // TODO: check if jwks contains `alg` by default (should be "alg": "ECDH-ES")

            // Merge into clientMetadata
            val baseMetadata = clientMetadata ?: ClientMetadata()
            baseMetadata.copy(
                jwks = jwks,
                // Ensure vp_formats_supported includes mso_mdoc for HAIP
                vpFormatsSupported = baseMetadata.vpFormatsSupported ?: mapOf(
                    "mso_mdoc" to JsonObject(
                        if (isDcApiHaip) mapOf(
                            "alg_values_supported" to JsonArray(
                                listOf(JsonPrimitive("ES256"))
                            )
                        ) else emptyMap()
                    )
                ),
                encryptedResponseEncValuesSupported = listOf("A128GCM")
            )
        } else {
            clientMetadata
        }



        require(isCrossDevice || isDcApi) { "No flow is selected" } // list all flows here
        val nonce = Uuid.random().toString()
        val state = if (!isDcApi) Uuid.random().toString() else null

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

            // For Unsigned DC API, client_id MUST be omitted.
            // For Signed DC API, it MUST be present.
            clientId = if (isDcApi && !isSignedRequest) null else clientId,
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
                isDcApi && isEncryptedResponse -> OpenID4VPResponseMode.DC_API_JWT // HAIP requires dc_api.jwt (encrypted)
                isDcApi -> OpenID4VPResponseMode.DC_API
                isCrossDevice && isEncryptedResponse -> OpenID4VPResponseMode.DIRECT_POST_JWT
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
            dcqlQuery = setup.core.dcqlQuery, // REQUIRED (unless 'scope' parameter represents a DCQL Query).
            clientMetadata = effectiveClientMetadata,


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
            expectedOrigins = if (isDcApi) setup.expectedOrigins else null,
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

        val effectivePolicies = DefinedVerificationPolicies(
            vp_policies = setup.core.policies.vp_policies ?: VPPolicyList(
                jwtVcJson = VPVerificationPolicyManager.defaultJwtVcJsonPolicies,
                dcSdJwt = VPVerificationPolicyManager.defaultDcSdJwtPolicies,
                msoMdoc = VPVerificationPolicyManager.defaultMsoMdocPolicies
            ),
            vc_policies = setup.core.policies.vc_policies ?: VCPolicyList(
                policies = listOf(CredentialSignaturePolicy())
            ),
            specific_vc_policies = setup.core.policies.specific_vc_policies
        )

        @Suppress("SENSELESS_COMPARISON") // TODO
        val newSession = Verification2Session(
            id = sessionId,

            creationDate = now,
            expirationDate = expiration,
            retentionDate = retentionDate,

            status = if (expiration != null) Verification2Session.VerificationSessionStatus.UNUSED else Verification2Session.VerificationSessionStatus.ACTIVE,

            bootstrapAuthorizationRequest = bootstrapAuthorizationRequest,
            bootstrapAuthorizationRequestUrl = bootstrapAuthorizationRequestUrl,

            authorizationRequest = authorizationRequest,
            authorizationRequestUrl = authorizationRequestUrl,
            signedAuthorizationRequestJwt = signedAuthorizationRequest,
            ephemeralDecryptionKey = ephemeralKey?.let { DirectSerializedKey(it) },
            jwkThumbprint = ephemeralKey?.getPublicKey()?.getThumbprint(),

            requestMode = if (isSignedRequest) Verification2Session.RequestMode.REQUEST_URI_SIGNED else Verification2Session.RequestMode.REQUEST_URI,

            policies = effectivePolicies,
            notifications = setup.core.notifications,
            redirects = when (setup) {
                is SameDeviceFlowSetup -> setup.redirects
                is CrossDeviceFlowSetup -> setup.redirects
                else -> null
            }
        )
        log.trace { "New Verification2Session: $newSession" }

        return newSession
    }

}
