package id.walt.ktorauthnz.methods

import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.kotlinx.resolveAsStringOrNull
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.methods.config.VerifiableCredentialAuthConfiguration
import io.github.smiley4.ktoropenapi.route
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object VerifiableCredential : AuthenticationMethod("vc") {


    // TODO:
    val verifierUrl = "http://localhost:7003"

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        route("vc", {

        }) {
            get("start-presentation") {
                val session = call.getAuthSession(authContext)
                val config = session.lookupFlowMethodConfiguration<VerifiableCredentialAuthConfiguration>(this@VerifiableCredential)

                val redirectUrl = call.request.uri.removeSuffix("/start-presentation") + "/callback"

                val resp = Verifier.verify(verifierUrl, config.verification, redirectUrl)

                call.respond(resp.presentationRequest)
            }
            get("callback") {
                call.respond("handle further...")
                //val session = getSession(authContext)
                //context.handleAuthSuccess(session, )
            }
        }
    }
}

enum class VerificationStatus {
    WAITING_FOR_SUBMISSION,
    RESPONSE_RECEIVED
}

data class VerificationResultStatus(
    val state: VerificationStatus,
    val success: Boolean? = null,
    val claims: Map<String, String?>? = null,
)

object Verifier {

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
        defaultRequest {
            contentType(ContentType.Application.Json)
        }
    }

    @Serializable
    data class VerificationSessionResponse(
        val presentationRequest: String,
        val state: String,
    )

    suspend fun verify(
        verifierUrl: String,
        verificationRequest: Map<String, JsonElement>,
        redirectUrl: String? = null,
    ): VerificationSessionResponse {
        val response: HttpResponse = client.post("$verifierUrl/openid4vc/verify") {
            setBody(verificationRequest)

            redirectUrl?.let {
                header("successRedirectUri", redirectUrl)
                header("errorRedirectUri", redirectUrl)
            }
        }

        val presentationRequest = response.bodyAsText()

        val state = parseQueryString(presentationRequest).getOrFail("state")
        return VerificationSessionResponse(presentationRequest, state)
    }

    @OptIn(ExperimentalEncodingApi::class)
    suspend fun getVerificationResult(verifierUrl: String, id: String, requestedClaims: List<String>): VerificationResultStatus {
        val resp = client.get("$verifierUrl/openid4vc/session/$id").body<JsonObject>()

        if (resp["tokenResponse"] == null) {
            return VerificationResultStatus(VerificationStatus.WAITING_FOR_SUBMISSION)
        }

        val overall = resp["verificationResult"]?.jsonPrimitive?.boolean == true


        val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        val payload = base64.decode(resp["tokenResponse"]!!.jsonObject["vp_token"]!!.jsonPrimitive.content.split(".")[1]).decodeToString()
        val vp = Json.parseToJsonElement(payload).jsonObject

        val did = vp["sub"]!!.jsonPrimitive.content

        val credentials = vp["vp"]!!.jsonObject["verifiableCredential"]!!.jsonArray.map {
            Json.parseToJsonElement(
                base64.decode(
                    it.jsonPrimitive.content.split(".")[1]
                ).decodeToString()
            ).jsonObject["vc"]!!.jsonObject
        }

        val vc = credentials.first()


        val claims = requestedClaims.map {
            Pair(it, vc.resolveAsStringOrNull(JsonPath.compile(it)))
        }.toMap().toMutableMap().apply {
            put("sub", did)
        }

        return VerificationResultStatus(VerificationStatus.RESPONSE_RECEIVED, overall, claims)
    }
}
