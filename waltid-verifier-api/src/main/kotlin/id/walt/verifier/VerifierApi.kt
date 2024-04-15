package id.walt.verifier

import id.walt.credentials.verification.PolicyManager
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.dif.*
import id.walt.verifier.oidc.VerificationUseCase
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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

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

private val verificationUseCase = VerificationUseCase(httpClient)


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
                        call.respond(HttpStatusCode.OK, it)
                    }.onFailure {
                        call.respond(HttpStatusCode.BadRequest, it.localizedMessage)
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
            get("policy-list", {
                tags = listOf("Credential Verification")
                summary = "List registered policies"
                response { HttpStatusCode.OK to { body<Map<String, String?>>() } }
            }) {
                call.respond(PolicyManager.listPolicyDescriptions())
            }
        }
    }
}
