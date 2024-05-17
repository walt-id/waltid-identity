package id.walt.verifier

import id.walt.credentials.verification.PolicyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.dif.*
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.verifier.base.config.ConfigManager
import id.walt.verifier.base.config.OIDCVerifierServiceConfig
import id.walt.verifier.oidc.RequestSigningCryptoProvider
import id.walt.verifier.oidc.VerificationUseCase
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import java.net.URLEncoder


private val SERVER_URL by lazy {
    runBlocking {
        ConfigManager.loadConfigs(arrayOf())
        ConfigManager.getConfig<OIDCVerifierServiceConfig>().baseUrl
    }
}

private val SERVER_SIGNING_KEY by lazy { runBlocking { JWKKey.generate(KeyType.secp256r1) } }



@Serializable
data class DescriptorMappingFormParam(val id: String, val format: VCFormat, val path: String)

@Serializable
data class PresentationSubmissionFormParam(
    val id: String, val definition_id: String, val descriptor_map: List<DescriptorMappingFormParam>
)

@Serializable
data class TokenResponseFormParam(
    val vp_token: JsonElement,
    val presentation_submission: PresentationSubmissionFormParam
)

@Serializable
data class CredentialVerificationRequest(
    @SerialName("vp_policies")
    val vpPolicies: List<JsonElement>,

    @SerialName("vc_policies")
    val vcPolicies: List<JsonElement>,

    @SerialName("request_credentials")
    val requestCredentials: List<JsonElement>
)

const val defaultAuthorizeBaseUrl = "openid4vp://authorize"

private val prettyJson = Json { prettyPrint = true }
private val httpClient = HttpClient() {
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


private val fixedPresentationDefinitionForEbsiConformanceTest = "{\"id\":\"any\",\"format\":{\"jwt_vp\":{\"alg\":[\"ES256\"]}},\"input_descriptors\":[{\"id\":\"any\",\"format\":{\"jwt_vc\":{\"alg\":[\"ES256\"]}},\"constraints\":{\"fields\":[{\"path\":[\"$.vc.type\"],\"filter\":{\"type\":\"array\",\"contains\":{\"const\":\"VerifiableAttestation\"}}}]}},{\"id\":\"any\",\"format\":{\"jwt_vc\":{\"alg\":[\"ES256\"]}},\"constraints\":{\"fields\":[{\"path\":[\"$.vc.type\"],\"filter\":{\"type\":\"array\",\"contains\":{\"const\":\"VerifiableAttestation\"}}}]}},{\"id\":\"any\",\"format\":{\"jwt_vc\":{\"alg\":[\"ES256\"]}},\"constraints\":{\"fields\":[{\"path\":[\"$.vc.type\"],\"filter\":{\"type\":\"array\",\"contains\":{\"const\":\"VerifiableAttestation\"}}}]}}]}"

private val verificationUseCase = VerificationUseCase(httpClient)

private val logger = KotlinLogging.logger { }


fun Application.verfierApi() {
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
                        example = defaultAuthorizeBaseUrl
                        required = false
                    }
                    headerParameter<ResponseMode>("responseMode") {
                        description = "Response mode, for vp_token response, defaults to ${ResponseMode.direct_post}"
                        example = ResponseMode.direct_post.name
                        required = false
                    }
                    headerParameter<String>("successRedirectUri") {
                        description = "Redirect URI to return when all policies passed. \"\$id\" will be replaced with the session id."
                        example = ""
                        required = false
                    }
                    headerParameter<String>("errorRedirectUri") {
                        description = "Redirect URI to return when a policy failed. \"\$id\" will be replaced with the session id."
                        example = ""
                        required = false
                    }
                    headerParameter<String>("statusCallbackUri") {
                        description = "Callback to push state changes of the presentation process to"
                        example = ""
                        required = false
                    }
                    headerParameter<String>("statusCallbackApiKey") {
                        description = ""
                        example = ""
                        required = false
                    }
                    headerParameter<String>("stateId") {
                        description = ""
                        example = ""
                        required = false
                    }
                    headerParameter<Boolean?>("useEbsiCTv3.2") {
                        description = "Set to true to get EBSI CT v3.2 compliant VP_TOKEN request"
                        example = ""
                        required = false
                    }
                    body<JsonObject> {
                        description =
                            "Presentation definition, describing the presentation requirement for this verification session. ID of the presentation definition is automatically assigned randomly."
                        //example("Verifiable ID example", verifiableIdPresentationDefinitionExample)
                        example("Minimal example", VerifierApiExamples.minimal)
                        example("Example with VP policies", VerifierApiExamples.vpPolicies)
                        example("Example with VP & global VC policies", VerifierApiExamples.vpGlobalVcPolicies)
                        example("Example with VP, VC & specific credential policies", VerifierApiExamples.vcVpIndividualPolicies)
                        example(
                            "Example with VP, VC & specific policies, and explicit presentation_definition  (maximum example)",
                            VerifierApiExamples.maxExample
                        )
                        example("Example with presentation definition policy", VerifierApiExamples.presentationDefinitionPolicy)
                    }
                }
            }) {
                val authorizeBaseUrl = context.request.header("authorizeBaseUrl") ?: defaultAuthorizeBaseUrl
                val responseMode =
                    context.request.header("responseMode")?.let { ResponseMode.valueOf(it) } ?: ResponseMode.direct_post
                val successRedirectUri = context.request.header("successRedirectUri")
                val errorRedirectUri = context.request.header("errorRedirectUri")
                val statusCallbackUri = context.request.header("statusCallbackUri")
                val statusCallbackApiKey = context.request.header("statusCallbackApiKey")
                val stateId = context.request.header("stateId")
                val useEbsiCTv3 = context.request.header("useEbsiCTv3.2")?.toBoolean() ?: false
                val body = context.receive<JsonObject>()

                val session = verificationUseCase.createSession(
                    vpPoliciesJson = body["vp_policies"],
                    vcPoliciesJson = body["vc_policies"],
                    requestCredentialsJson = body["request_credentials"]!!,
                    presentationDefinitionJson = body["presentation_definition"],
                    responseMode = responseMode,
                    successRedirectUri = successRedirectUri,
                    errorRedirectUri = errorRedirectUri,
                    statusCallbackUri = statusCallbackUri,
                    statusCallbackApiKey = statusCallbackApiKey,
                    stateId = stateId,
                    useEbsiCTv3 = useEbsiCTv3
                )

                context.respond(authorizeBaseUrl.plus("?").plus(session.authorizationRequest!!.toHttpQueryString()))
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
                        mediaType(ContentType.Application.FormUrlEncoded)
                        example(
                            "simple vp_token response", TokenResponseFormParam(
                                JsonPrimitive("abc.def.ghi"), PresentationSubmissionFormParam(
                                    "1", "1", listOf(
                                        DescriptorMappingFormParam("1", VCFormat.jwt_vc_json, "$.type")
                                    )
                                )
                            )
                        )
                    }
                }
            }) {
                val sessionId = call.parameters["state"]
                verificationUseCase.verify(sessionId, context.request.call.receiveParameters().toMap())
                    .onSuccess {
                        val session = verificationUseCase.getSession(sessionId!!)
                        if (session.stateParamAuthorizeReqEbsi != null) {
                            val state = session.stateParamAuthorizeReqEbsi
                            val code = UUID().toString()
                            context.respondRedirect("openid://?code=$code&state=$state")
                        } else {
                            call.respond(HttpStatusCode.OK, it)
                        }
                    }.onFailure {
                        var errorDescription: String = it.localizedMessage

                        if (sessionId != null ) {
                            val session = verificationUseCase.getSession(sessionId)
                            if (session.stateParamAuthorizeReqEbsi != null) {
                                val state = session.stateParamAuthorizeReqEbsi
                                when (it.localizedMessage) {
                                    "Verification policies did not succeed: expired" -> errorDescription = "<\$presentation_submission.descriptor_map[x].id> is expired"
                                    "Verification policies did not succeed: not-before" -> errorDescription = "<\$presentation_submission.descriptor_map[x].id> is not yet valid"
                                    "Verification policies did not succeed: revoked" -> errorDescription = "<\$presentation_submission.descriptor_map[x].id> is revoked"
                                }
                                context.respondRedirect("openid://?state=$state&error=invalid_request&error_description=$errorDescription")
                            }
                        } else {
                                call.respond(HttpStatusCode.BadRequest, errorDescription)
                        }
                    }.also {
                        sessionId?.run { verificationUseCase.notifySubscribers(this) }
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
                        description = "Session info"
                    }
                }
            }) {
                val id = call.parameters.getOrFail("id")
                verificationUseCase.getResult(id).onSuccess {
                    call.respond(HttpStatusCode.OK, it)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
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
            }) {
                val id = call.parameters["id"]

                verificationUseCase.getPresentationDefinition(id ?: "").onSuccess {
                    call.respond(it.toJSON())
                }.onFailure {
                    call.respond(HttpStatusCode.NotFound)
                }
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
                    call.respond(HttpStatusCode.OK, it)
                }.onFailure {
                    call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
                }
            }
            get("policy-list", {
                tags = listOf("Credential Verification")
                summary = "List registered policies"
                response { HttpStatusCode.OK to { body<Map<String, String?>>() } }
            }) {
                call.respond(PolicyManager.listPolicyDescriptions())
            }
        }

        get("/.well-known/openid-configuration", {tags= listOf("Ebsi") }) {
            val metadata = buildJsonObject {
                put("token_endpoint", "$SERVER_URL/token")
                put("issuer", SERVER_URL)
                put("jwks_uri", "$SERVER_URL/jwks")
                put("response_types_supported", buildJsonArray {
                    add("code")
                    add("id_token")
                    add("vp_token")
                })
                put("subject_types_supported", buildJsonArray { add("public") })
                put("id_token_signing_alg_values_supported", buildJsonArray { add("ES256") })
                put("authorization_endpoint", "$SERVER_URL/authorize")
            }
            call.respond(metadata)
        }

        get("/jwks", {tags= listOf("Ebsi") }) {
            val jwks = buildJsonObject {
                put("keys", buildJsonArray {
                    val jwkWithKid = buildJsonObject {
                        SERVER_SIGNING_KEY.getPublicKey().exportJWKObject().forEach {
                            put(it.key, it.value)
                        }
                        put("kid", SERVER_SIGNING_KEY.getPublicKey().getKeyId())
                    }
                    add(jwkWithKid)
                })
            }

            call.respond(HttpStatusCode.OK, jwks)
        }

        get("authorize", {tags= listOf("Ebsi") }) {
            val params = call.parameters.toMap().toJsonObject()

            val stateParamAuthorizeReqEbsi = params["state"]?.jsonArray?.get(0)?.jsonPrimitive?.content
            var scope = params["scope"]?.jsonArray.toString().replace("\"", "").replace("[", "").replace("]", "")
            var presentationDefinitionJson: JsonElement? = null
            var responseType = "id_token"
            var isRedirectResponse = false// make if redirect_uri  = "openid://"
            if (scope.contains("openid ver_test:vp_token")) { //EBSI Conformance tests
                presentationDefinitionJson = Json.parseToJsonElement(fixedPresentationDefinitionForEbsiConformanceTest)
                responseType = "vp_token"
                isRedirectResponse = true
            }
            if (scope.contains("openid ver_test:id_token")) {//EBSI Conformance tests
                responseType = "id_token"
                isRedirectResponse= true
}
            scope = "openid"

            val stateId = UUID().toString()

            val session = verificationUseCase.createSession(
                vpPoliciesJson = null,
                vcPoliciesJson = buildJsonArray {
                    add("signature")
                    add("expired")
                    add("not-before")
                    add("revoked")
                },
                requestCredentialsJson = buildJsonArray {},
                presentationDefinitionJson = presentationDefinitionJson,
                responseMode = ResponseMode.direct_post,
                successRedirectUri = null,
                errorRedirectUri = null,
                statusCallbackUri = null,
                statusCallbackApiKey = null,
                stateId = stateId,
                stateParamAuthorizeReqEbsi = stateParamAuthorizeReqEbsi
            )

            // Create a jwt for the request parameter in response
            // Bind authentication request with state
            //                val idTokenRequestState = UUID().toString();
            //                val idTokenRequestNonce = UUID().toString();
            val responseMode = ResponseMode.direct_post

            val clientId = SERVER_URL
            val redirectUri = session.authorizationRequest!!.responseUri

            val response = session.authorizationRequest!!

            // Create a jwt as request object as defined in JAR OAuth2.0 specification
            val requestJwtPayload = buildJsonObject {
                put(JWTClaims.Payload.issuer, clientId)
                put(JWTClaims.Payload.audience, response.clientId)
                //                        put(JWTClaims.Payload.nonce, idTokenRequestNonce)
                //                        put("state", idTokenRequestState)
                put("client_id", clientId)
                put("redirect_uri", redirectUri)
                put("response_type", responseType)
                put("response_mode", responseMode.name)
                put("scope", scope)
                put("exp", 1776532276)
                if (presentationDefinitionJson != null) // if null, then it is IdToken
                    put("presentation_definition", presentationDefinitionJson)
                //                            put("presentation_definition_uri", response.presentationDefinitionUri)
            }

            val requestJwtHeader = mapOf(
                JWTClaims.Header.keyID to SERVER_SIGNING_KEY.getPublicKey().getKeyId(),
                JWTClaims.Header.type to "JWT"
            )

            val requestToken =
                SERVER_SIGNING_KEY.signJws(requestJwtPayload.toString().toByteArray(), requestJwtHeader).also {
                    logger.info { "Signed JWS: >> $it" }
                }

            var responseQueryString = response.toHttpQueryString()
            responseQueryString = responseQueryString.replace("client_id=", "client_id=$clientId")
            responseQueryString = responseQueryString.replace("response_uri", "redirect_uri")
            val presentationDefinitionUri = URLEncoder.encode(response.presentationDefinitionUri!!, "UTF-8")
            responseQueryString = responseQueryString.replace("&presentation_definition_uri=", "")
            responseQueryString = responseQueryString.replace(presentationDefinitionUri, "")
            responseQueryString = responseQueryString.replace("response_type=vp_token", "response_type=$responseType")
            responseQueryString = responseQueryString.plus("&scope=$scope")
            responseQueryString = responseQueryString.plus("&request=$requestToken")

            logger.info { "openid://?$responseQueryString" }
            if (isRedirectResponse)
                context.respondRedirect("openid://?$responseQueryString")
            else
                context.respond("openid://?$responseQueryString")
        }

    }
}
