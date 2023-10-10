package id.walt.verifier


import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.responses.TokenResponse
import id.walt.verifier.oidc.OIDCVerifierService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.github.smiley4.ktorswaggerui.dsl.post
import io.github.smiley4.ktorswaggerui.dsl.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

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

object OidcApi {

    val logger = KotlinLogging.logger { }

    private fun Application.oidcRoute(build: Route.() -> Unit) {
        routing {
            //   authenticate("authenticated") {
            /*route("oidc", {
                tags = listOf("oidc")
            }) {*/
            build.invoke(this)
            /*}*/
        }
        //}
    }

    fun Application.oidcApi() = oidcRoute {

        route("oidc", {
            tags = listOf("oidc")
        }) {
            post("/verify/{state}", {
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
                val session = call.parameters["state"]?.let { OIDCVerifierService.getSession(it) }
                val tokenResponse = TokenResponse.fromHttpParameters(context.request.call.receiveParameters().toMap())
                if (session == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        "State parameter doesn't refer to an existing session, or session expired"
                    )
                } else if (OIDCVerifierService.verify(tokenResponse, session).verificationResult == true) {
                    call.respond(HttpStatusCode.OK)
                } else
                    call.respond(HttpStatusCode.BadRequest, "Response could not be verified.")
            }
        }
    }
}
