@file:OptIn(ExperimentalUuidApi::class)

package id.walt.verifier

import com.nimbusds.jose.JWSAlgorithm
import id.walt.commons.config.ConfigManager
import id.walt.credentials.utils.VCFormat
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.oid4vc.data.OpenId4VPProfile
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.data.dif.*
import id.walt.policies.PolicyManager
import id.walt.sdjwt.SimpleJWTCryptoProvider
import id.walt.verifier.config.OIDCVerifierServiceConfig
import id.walt.verifier.oidc.RequestSigningCryptoProvider
import id.walt.verifier.oidc.SwaggerPresentationSessionInfo
import id.walt.verifier.oidc.VerificationUseCase
import id.walt.verifier.oidc.VerificationUseCase.FailedVerificationException
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
import io.klogging.logger
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val SERVER_URL by lazy {
    runBlocking {
        ConfigManager.loadConfigs(arrayOf())
        ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl
    }
}

@Serializable
data class DescriptorMappingFormParam(val id: String, val format: VCFormat, val path: String)

@Serializable
data class PresentationSubmissionFormParam(
    val id: String, val definition_id: String, val descriptor_map: List<DescriptorMappingFormParam>,
)

@Serializable
data class TokenResponseFormParam(
    val vp_token: JsonElement?,
    val presentation_submission: PresentationSubmissionFormParam?,
    val response: String?,
)

@Serializable
data class CredentialVerificationRequest(
    @SerialName("vp_policies")
    val vpPolicies: List<JsonElement>,

    @SerialName("vc_policies")
    val vcPolicies: List<JsonElement>,

    @SerialName("request_credentials")
    val requestCredentials: List<JsonElement>,
)

const val defaultAuthorizeBaseUrl = "openid4vp://authorize"

private val logger = logger("Verifier API")

private val prettyJson = Json { prettyPrint = true }
private val httpClient = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(Logging) {
        logger = Logger.SIMPLE
        level = LogLevel.ALL
    }
}

val verifiableIdPresentationDefinitionExample = JsonObject(
    mapOf(
        "policies" to JsonArray(listOf(JsonPrimitive("signature"))),
        "presentation_definition" to
                PresentationDefinition(
                    "<automatically assigned>", listOf(
                        InputDescriptor(
                            "VerifiableId",
                            format = mapOf(VCFormat.jwt_vc_json to VCFormatDefinition(alg = setOf("EdDSA"))),
                            constraints = InputDescriptorConstraints(
                                fields = listOf(InputDescriptorField(path = listOf("$.type"), filter = buildJsonObject {
                                    put("type", JsonPrimitive("string"))
                                    put("pattern", JsonPrimitive("VerifiableId"))
                                }))
                            )
                        )
                    )
                ).toJSON(),
    )
).let { prettyJson.encodeToString(it) }


private const val fixedPresentationDefinitionForEbsiConformanceTest =
    "{\"id\":\"any\",\"format\":{\"jwt_vp\":{\"alg\":[\"ES256\"]}},\"input_descriptors\":[{\"id\":\"any\",\"format\":{\"jwt_vc\":{\"alg\":[\"ES256\"]}},\"constraints\":{\"fields\":[{\"path\":[\"$.vc.type\"],\"filter\":{\"type\":\"array\",\"contains\":{\"const\":\"VerifiableAttestation\"}}}]}},{\"id\":\"any\",\"format\":{\"jwt_vc\":{\"alg\":[\"ES256\"]}},\"constraints\":{\"fields\":[{\"path\":[\"$.vc.type\"],\"filter\":{\"type\":\"array\",\"contains\":{\"const\":\"VerifiableAttestation\"}}}]}},{\"id\":\"any\",\"format\":{\"jwt_vc\":{\"alg\":[\"ES256\"]}},\"constraints\":{\"fields\":[{\"path\":[\"$.vc.type\"],\"filter\":{\"type\":\"array\",\"contains\":{\"const\":\"VerifiableAttestation\"}}}]}}]}"

private val verificationUseCase =
    VerificationUseCase(httpClient, SimpleJWTCryptoProvider(JWSAlgorithm.EdDSA, null, null))

fun Application.verifierApi() {
    routing {

        route("openid4vc", {
        }) {
            post("verify", {
                tags = listOf("Credential Verification")
                summary = "Initialize OIDC presentation session"
                description =
                    "Initializes an OIDC presentation session, with the given presentation definition and parameters. The URL returned can be rendered as QR code for the holder wallet to scan, or called directly on the holder if the wallet base URL is given."
                request {
                    headerParameter<String>("authorizeBaseUrl") {
                        description = "Base URL of wallet authorize endpoint, defaults to: $defaultAuthorizeBaseUrl"
                        example("default authorize base url") {
                            value = defaultAuthorizeBaseUrl
                        }
                        required = false
                    }
                    headerParameter<ResponseMode>("responseMode") {
                        description = "Response mode, for vp_token response, defaults to ${ResponseMode.direct_post}"
                        example("direct post") {
                            value = ResponseMode.direct_post
                        }
                        required = false
                    }
                    headerParameter<String>("successRedirectUri") {
                        description =
                            "Redirect URI to return when all policies passed. \"\$id\" will be replaced with the session id."
                        // example = ""
                        required = false
                    }
                    headerParameter<String>("errorRedirectUri") {
                        description =
                            "Redirect URI to return when a policy failed. \"\$id\" will be replaced with the session id."
                        // example = ""
                        required = false
                    }
                    headerParameter<String>("statusCallbackUri") {
                        description = "Callback to push state changes of the presentation process to"
                        // example = ""
                        required = false
                    }
                    headerParameter<String>("statusCallbackApiKey") {
                        description = ""
                        // example = ""
                        required = false
                    }
                    headerParameter<String>("stateId") {
                        description = ""
                        // example = ""
                        required = false
                    }
                    headerParameter<String?>("openId4VPProfile") {
                        description =
                            "Optional header to set the profile of the VP request, available profiles: ${OpenId4VPProfile.entries.joinToString()}"
                        // example = ""
                        required = false
                    }
                    headerParameter<String?>("sessionTtl") {
                        description =
                            "Optional header to set the sessionTtl of the VP request, in seconds"
                        // example = ""
                        required = false
                    }
                    body<JsonObject> {
                        description =
                            "Presentation definition, describing the presentation requirement for this verification session. ID of the presentation definition is automatically assigned randomly."
                        //example("Verifiable ID example", verifiableIdPresentationDefinitionExample)
                        example("Minimal example", VerifierApiExamples.minimal)
                        example("Example with VP policies", VerifierApiExamples.vpPolicies)
                        example("Example with VP & global VC policies", VerifierApiExamples.vpGlobalVcPolicies)
                        example(
                            "Example with VP, VC & specific credential policies",
                            VerifierApiExamples.vcVpIndividualPolicies
                        )
                        example(
                            "Example with Dynamic Policy applied to credential Data",
                            VerifierApiExamples.dynamicPolicy
                        )
                        example(
                            "Example with VP, VC & specific policies, and explicit input_descriptor(s)  (maximum example)",
                            VerifierApiExamples.maxExample
                        )
                        example(
                            "Example with presentation definition policy",
                            VerifierApiExamples.presentationDefinitionPolicy
                        )
                        example(
                            "Example with EBSI PDA1 Presentation Definition",
                            VerifierApiExamples.EbsiVerifiablePDA1
                        )
                        example("MDoc verification example", VerifierApiExamples.lspPotentialMdocExample)
                        example("SD-JWT-VC verification example", VerifierApiExamples.lspPotentialSDJwtVCExample)
                        example(
                            "SD-JWT-VC verification example with mandatory fields",
                            VerifierApiExamples.sdJwtVCExampleWithRequiredFields
                        )
                    }
                }

            }) {

                val authorizeBaseUrl = call.request.header("authorizeBaseUrl") ?: defaultAuthorizeBaseUrl
                val responseMode =
                    call.request.header("responseMode")?.let { ResponseMode.fromString(it) }
                        ?: ResponseMode.direct_post
                val successRedirectUri = call.request.header("successRedirectUri")
                val errorRedirectUri = call.request.header("errorRedirectUri")
                val statusCallbackUri = call.request.header("statusCallbackUri")
                val statusCallbackApiKey = call.request.header("statusCallbackApiKey")
                val stateId = call.request.header("stateId")
                val openId4VPProfile = call.request.header("openId4VPProfile")
                // Parse session TTL from header if provided
                val sessionTtl = call.request.header("sessionTtl")?.toLongOrNull()?.let { it.seconds }

                val body = call.receive<JsonObject>()

                val session = verificationUseCase.createSession(
                    vpPoliciesJson = body["vp_policies"],
                    vcPoliciesJson = body["vc_policies"],
                    requestCredentialsJson = body["request_credentials"]
                        ?: throw BadRequestException("Field request_credentials is required"),
                    responseMode = responseMode,
                    successRedirectUri = successRedirectUri,
                    errorRedirectUri = errorRedirectUri,
                    statusCallbackUri = statusCallbackUri,
                    statusCallbackApiKey = statusCallbackApiKey,
                    stateId = stateId,
                    openId4VPProfile = (body["openid_profile"]?.jsonPrimitive?.contentOrNull
                        ?: openId4VPProfile)?.let { OpenId4VPProfile.valueOf(it.uppercase()) }
                        ?: OpenId4VPProfile.fromAuthorizeBaseURL(authorizeBaseUrl),
                    trustedRootCAs = body["trusted_root_cas"]?.jsonArray,
                    sessionTtl = sessionTtl
                )

                call.respond(
                    authorizeBaseUrl.plus("?").plus(
                        when (session.openId4VPProfile) {
                            OpenId4VPProfile.ISO_18013_7_MDOC -> session.authorizationRequest!!.toRequestObjectByReferenceHttpQueryString(
                                ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl.let { "$it/openid4vc/request/${session.id}" })

                            OpenId4VPProfile.EBSIV3 -> session.authorizationRequest!!.toEbsiRequestObjectByReferenceHttpQueryString(
                                SERVER_URL.let { "$it/openid4vc/request/${session.id}" })

                            else -> session.authorizationRequest!!.toHttpQueryString()
                        }
                    )
                )
            }



            post("/verify/{state}", {
                tags = listOf("OIDC")
                summary = "Verify vp_token response, for a verification request identified by the state"
                description =
                    "Called in direct_post response mode by the SIOP provider (holder wallet) with the verifiable presentation in the vp_token and the presentation_submission parameter, describing the submitted presentation. The presentation session is identified by the given state parameter."
                request {
                    pathParameter<String>("state") {
                        description =
                            "State, i.e. session ID, identifying the presentation session, this response belongs to."
                        required = true
                    }
                    body<TokenResponseFormParam> {
                        mediaTypes = listOf(ContentType.Application.FormUrlEncoded)
                        example("simple vp_token response") {
                            value = TokenResponseFormParam(
                                JsonPrimitive("abc.def.ghi"), PresentationSubmissionFormParam(
                                    "1", "1", listOf(
                                        DescriptorMappingFormParam("1", VCFormat.jwt_vc_json, "$.vc.type")
                                    )
                                ), null
                            )
                        }
                        example("direct_post.jwt response") {
                            value = TokenResponseFormParam(null, null, "ey...")
                        }
                    }
                }
            }) {

                logger.info { "POST verify/state" }
                val sessionId = call.parameters.getOrFail("state")
                logger.info { "State: $sessionId" }
                verificationUseCase.verify(sessionId, call.request.call.receiveParameters().toMap())
                    .onSuccess {
                        val session = verificationUseCase.getSession(sessionId!!)
                        if (session.walletInitiatedAuthState != null) {
                            val state = session.walletInitiatedAuthState
                            val code = Uuid.random().toString()
                            call.respondRedirect("openid://?code=$code&state=$state")
                        } else {
                            call.respond(HttpStatusCode.OK, it)
                        }
                    }.onFailure {
                        runBlocking { logger.debug(it) { "Verification failed ($it)" } }
                        val errorDescription = it.message ?: "Verification failed"
                        runBlocking { logger.error { "Error: $errorDescription" } }
                        if (sessionId != null) {
                            val session = verificationUseCase.getSession(sessionId)
                            if (session.walletInitiatedAuthState != null) {
                                val state = session.walletInitiatedAuthState
                                runBlocking {
                                    this@post.call.respondRedirect(
                                        "openid://?state=$state&error=invalid_request&error_description=${getErrorDescription(it)}"
                                    )
                                }
                            } else if (it is FailedVerificationException && it.redirectUrl != null) {
                                runBlocking {
                                    this@post.call.respond(HttpStatusCode.BadRequest, it.redirectUrl)
                                }

                            } else {
                                throw it
                            }
                        } else {
                            runBlocking {
                                logger.error(it) { "/verify error: $errorDescription" }
                                this@post.call.respond(HttpStatusCode.BadRequest, errorDescription)
                            }
                        }
                    }.also {
                        verificationUseCase.notifySubscribers(sessionId)
                    }

            }

            get("/session/{id}", {
                tags = listOf("Credential Verification")
                summary = "Get info about OIDC presentation session, that was previously initialized"
                description =
                    "Session info, containing current state and result information about an ongoing OIDC presentation session"
                request {
                    pathParameter<String>("id") {
                        description = "Session ID"
                        required = true
                    }
                }
                response {
                    HttpStatusCode.OK to {
                        // body<PresentationSessionInfo> { // cannot encode duration
                        body<SwaggerPresentationSessionInfo> {
                            description = "Session info"
                        }
                    }
                    HttpStatusCode.NotFound to {
                        body<String> {
                            description = "Session not found or invalid"
                            example("Session not found") {
                                value = "Invalid id provided (expired?): 123"
                            }
                        }

                    }

                }
            }) {
                val id = call.parameters.getOrFail("id")
                verificationUseCase.getResult(id).getOrThrow().let {
                    call.respond(HttpStatusCode.OK, it)
                }
            }

            get("/pd/{id}", {
                tags = listOf("OIDC")
                summary = "Get presentation definition object by ID"
                description =
                    "Gets a presentation definition object, previously uploaded during initialization of OIDC presentation session."
                request {
                    pathParameter<String>("id") {
                        description = "ID of presentation definition object to retrieve"
                        required = true
                    }
                }
                response {
                    HttpStatusCode.OK to { body<JsonObject>() }
                    HttpStatusCode.NotFound to { body<String>() }
                }
            }) {
                val id = call.parameters["id"]

                verificationUseCase.getPresentationDefinition(id ?: "").onSuccess {
                    call.respond(it.toJSON())
                }.onFailure {
                    throw NotFoundException("Presentation definition not found for id: $id")
                }
            }
            get("policy-list", {
                tags = listOf("Credential Verification")
                summary = "List registered policies"
                response { HttpStatusCode.OK to { body<Map<String, String?>>() } }
            }) {
                call.respond(PolicyManager.listPolicyDescriptions())
            }
            get("/request/{id}", {
                tags = listOf("OIDC")
                summary = "Get request object for session by session id"
                description = "Gets the signed request object for the session given by the session id parameter"
                request {
                    pathParameter<String>("id") {
                        description = "ID of the presentation session"
                        required = true
                    }
                }
            }) {
                val id = call.parameters.getOrFail("id")
                verificationUseCase.getSignedAuthorizationRequestObject(id).onSuccess {
                    call.respondText(it, ContentType.parse("application/oauth-authz-req+jwt"), HttpStatusCode.OK)
                }.onFailure {
                    logger.debug(it) { "Cannot view request session ($it)" }
                    throw NotFoundException(it.message)
                }
            }
        }

        get("/.well-known/openid-configuration", { tags = listOf("Ebsi") }) {
            val metadata = buildJsonObject {
                put("authorization_endpoint", "$SERVER_URL/authorize")
                put("token_endpoint", "$SERVER_URL/token")
                put("issuer", SERVER_URL)
                put("jwks_uri", "$SERVER_URL/jwks")
                put("response_types_supported", buildJsonArray {
                    add(ResponseType.Code.name)
                    add(ResponseType.IdToken.name)
                    add(ResponseType.VpToken.name)
                })
                put("subject_types_supported", buildJsonArray { add("public") })
                put("id_token_signing_alg_values_supported", buildJsonArray { add("ES256") })
            }
            call.respond(metadata)
        }

        get("/jwks", { tags = listOf("Ebsi") }) {
            val jwks = buildJsonObject {
                put("keys", buildJsonArray {
                    val jwkWithKid = buildJsonObject {
                        RequestSigningCryptoProvider.signingKey.toPublicJWK().toJSONObject().forEach {
                            put(it.key, it.value.toJsonElement())
                        }
                        put("kid", RequestSigningCryptoProvider.signingKey.keyID)
                    }
                    add(jwkWithKid)
                })
            }

            call.respond(HttpStatusCode.OK, jwks)
        }

        get("authorize", {
            tags = listOf("Ebsi")
            description =
                "Authorize endpoint of OAuth Server as defined in EBSI Conformance Testing specifications. \nResponse is a 302 redirect with VP_TOKEN or ID_TOKEN request. \n" +
                        "Use the /oidc4vp/verify endpoint using the header openId4VPProfile to get an EBSI-compliant VP_TOKEN request without redirects."
        })
        {
            val params = call.parameters.toMap().toJsonObject()

            val walletInitiatedAuthState = params["state"]?.jsonArray?.get(0)?.jsonPrimitive?.content
            val scope = params["scope"]?.jsonArray.toString().replace("\"", "").replace("[", "").replace("]", "")

            val stateId = Uuid.random().toString()
            // Parse session TTL from query parameter if provided
            val sessionTtl = params["sessionTtl"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull?.toLongOrNull()?.let { it.seconds }
            
            val session = verificationUseCase.createSession(
                vpPoliciesJson = null,
                vcPoliciesJson = buildJsonArray {
                    add("signature")
                    add("expired")
                    add("not-before")
                    add("revoked-status-list")
                },
                presentationDefinitionJson = when (scope.contains("openid ver_test:vp_token")) {
                    true -> Json.parseToJsonElement(fixedPresentationDefinitionForEbsiConformanceTest).jsonObject
                    else -> null
                } ?: throw IllegalArgumentException(""),
                responseMode = ResponseMode.direct_post,
                successRedirectUri = null,
                errorRedirectUri = null,
                statusCallbackUri = null,
                statusCallbackApiKey = null,
                stateId = stateId,
                walletInitiatedAuthState = walletInitiatedAuthState,
                responseType = when (scope.contains("openid ver_test:id_token")) {
                    true -> ResponseType.IdToken
                    else -> ResponseType.VpToken
                },
                openId4VPProfile = OpenId4VPProfile.EBSIV3,
                sessionTtl = sessionTtl
            )
            call.respondRedirect(
                "openid://?${
                    session.authorizationRequest!!.toEbsiRequestObjectByReferenceHttpQueryString(
                        SERVER_URL.let { "$it/openid4vc/request/${session.id}" })
                }"
            )
        }
    }
}

private fun getErrorDescription(it: Throwable): String? = when (it.message) {
    "Verification policies did not succeed: expired" ->
        "<\$presentation_submission.descriptor_map[x].id> is expired"

    "Verification policies did not succeed: not-before" ->
        "<\$presentation_submission.descriptor_map[x].id> is not yet valid"

    "Verification policies did not succeed: revoked-status-list" ->
        "<\$presentation_submission.descriptor_map[x].id> is revoked"

    else -> null
}
