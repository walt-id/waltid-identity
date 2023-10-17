package id.walt.verifier

import id.walt.credentials.verification.models.PolicyRequest
import id.walt.credentials.verification.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.credentials.verification.policies.JwtSignaturePolicy
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.dif.*
import id.walt.verifier.oidc.OIDCVerifierService
import id.walt.verifier.oidc.PresentationSessionInfo
import io.github.smiley4.ktorswaggerui.dsl.get
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

private val prettyJson = Json { prettyPrint = true }

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

fun Application.verfierApi() {
    routing {

        route("vp", {
            tags = listOf("Verifiable Presentation sessions")
        }) {
            post("initOidc", {
                summary = "Initialize OIDC presentation session"
                description =
                    "Initializes an OIDC presentation session, with the given presentation definition and parameters. The URL returned can be rendered as QR code for the holder wallet to scan, or called directly on the holder if the wallet base URL is given."
                request {
                    headerParameter<String>("authorizeBaseUrl") {
                        description = "Base URL of wallet authorize endpoint, defaults to: $defaultAuthorizeBaseUrl"
                        required = false
                    }
                    headerParameter<ResponseMode>("responseMode") {
                        description = "Response mode, for vp_token response, defaults to ${ResponseMode.direct_post}"
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
                        example("Example with VP, VC & specific policies, and explicit presentation_definition  (maximum example)", VerifierApiExamples.maxExample)
                        example("Example with presentation definition policy", VerifierApiExamples.presentationDefinitionPolicy)
                    }
                }
            }) {
                val authorizeBaseUrl = context.request.header("authorizeBaseUrl") ?: defaultAuthorizeBaseUrl
                val responseMode =
                    context.request.header("responseMode")?.let { ResponseMode.valueOf(it) } ?: ResponseMode.direct_post

                val body = context.receive<JsonObject>()

                /*val presentationDefinition = (body["presentation_definition"]
                    ?: throw IllegalArgumentException("No `presentation_definition` supplied!"))
                    .let { PresentationDefinition.fromJSON(it.jsonObject) }*/

                val vpPolicies = body["vp_policies"]?.jsonArray?.parsePolicyRequests()
                    ?: listOf(PolicyRequest(JwtSignaturePolicy()))

                val vcPolicies = body["vc_policies"]?.jsonArray?.parsePolicyRequests()
                    ?: listOf(PolicyRequest(JwtSignaturePolicy()))

                val requestCredentialsArr = body["request_credentials"]!!.jsonArray

                val requestedTypes = requestCredentialsArr.map {
                    when (it) {
                        is JsonPrimitive -> it.contentOrNull
                        is JsonObject -> it["credential"]?.jsonPrimitive?.contentOrNull
                        else -> throw IllegalArgumentException("Invalid JSON type for requested credential: $it")
                    } ?: throw IllegalArgumentException("Invalid VC type for requested credential: $it")
                }

                val presentationDefinition = (body["presentation_definition"]?.let { PresentationDefinition.fromJSON(it.jsonObject) })
                    ?: PresentationDefinition.primitiveGenerationFromVcTypes(requestedTypes)
                println("Presentation definition: " + presentationDefinition.toJSON())

                val session =
                    OIDCVerifierService.initializeAuthorization(presentationDefinition, responseMode = responseMode)

                val specificPolicies = requestCredentialsArr
                    .filterIsInstance<JsonObject>()
                    .associate { (it["credential"] ?: throw IllegalArgumentException("No `credential` name supplied, in `request_credentials`."))
                        .jsonPrimitive.content to (it["policies"] ?: throw IllegalArgumentException("No `policies` supplied, in `request_credentials`."))
                        .jsonArray.parsePolicyRequests() }

                println("vpPolicies: $vpPolicies")
                println("vcPolicies: $vcPolicies")
                println("spPolicies: $specificPolicies")


                OIDCVerifierService.sessionPolicies[session.id] =
                    OIDCVerifierService.SessionPolicyRequests(vpPolicies, vcPolicies, specificPolicies)

                context.respond(authorizeBaseUrl.plus("?").plus(session.authorizationRequest!!.toHttpQueryString()))
            }
            get("/session/{id}", {
                summary = "Get info about OIDC presentation session, that was previously initialized"
                description =
                    "Session info, containing current state and result information about an ongoing OIDC presentation session"
                request {
                    pathParameter<String>("id") {
                        description = "Session ID"
                        required = true
                    }
                }
            }) {
                val id = call.parameters["id"] ?: throw IllegalArgumentException("No id provided!")
                val session = OIDCVerifierService.getSession(id)
                    ?: throw IllegalArgumentException("Invalid id provided (expired?): $id")

                val policyResults = OIDCVerifierService.policyResults[session.id]
                    ?: throw IllegalStateException("No policy results found for id")

                call.respond(
                    Json { prettyPrint = true }.encodeToString(
                        PresentationSessionInfo.fromPresentationSession(
                            session,
                            policyResults.toJson()
                        )
                    )
                )
            }
            get("/pd/{id}", {
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
                val pd = id?.let { OIDCVerifierService.getSession(it)?.presentationDefinition }
                if (pd != null) {
                    call.respond(pd.toJSON())
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

        }
    }
}
