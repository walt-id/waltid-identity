package id.walt.idp.verifier

import com.nfeld.jsonpathkt.JsonPath
import com.nfeld.jsonpathkt.kotlinx.resolveAsStringOrNull
import id.walt.idp.utils.JsonUtils.toJsonObject
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.util.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

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

    suspend fun verify(request: Map<String, Any>, redirectUrl: String): Pair<String, String> {
        val response: HttpResponse = client.post("http://localhost:7003/openid4vc/verify") {
            setBody(
                request.toJsonObject()
            )
            header("successRedirectUri", redirectUrl)
        }

        val presentationRequest = response.bodyAsText()

        val state = parseQueryString(presentationRequest).getOrFail("state")
        return Pair(presentationRequest, state)
    }

    @OptIn(ExperimentalEncodingApi::class)
    /**
     * @param requestedClaims provide a map in the form of {claimName=JSON-path-to-attribute}
     */
    suspend fun getVerificationResult(id: String, requestedClaims: Map<String, String>): VerificationResultStatus {
        val resp = client.get("http://localhost:7003/openid4vc/session/$id").body<JsonObject>()

        if (resp["tokenResponse"] == null) {
            return VerificationResultStatus(VerificationStatus.WAITING_FOR_SUBMISSION)
        }

        val overall = resp["verificationResult"]?.jsonPrimitive?.boolean == true


        val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        val payload = base64.decode(resp["tokenResponse"]!!.jsonObject["vp_token"]!!.jsonPrimitive.content.split(".")[1]).decodeToString()
        val vp = Json.parseToJsonElement(payload).jsonObject

        val did = vp["sub"]!!.jsonPrimitive.content
        println(did)

        val credentials = vp["vp"]!!.jsonObject["verifiableCredential"]!!.jsonArray.map {
            Json.parseToJsonElement(
                base64.decode(
                    it.jsonPrimitive.content.split(".")[1]
                ).decodeToString()
            ).jsonObject["vc"]!!.jsonObject
        }
        println(credentials)

        val vc = credentials.first()


        val claims = requestedClaims.map { (claimName, jsonPathSelector) ->
            Pair(claimName, vc.resolveAsStringOrNull(JsonPath.compile(jsonPathSelector)))
        }.toMap().toMutableMap().apply {
            put("sub", did)
        }
        println("Claims: $claims")

        return VerificationResultStatus(VerificationStatus.RESPONSE_RECEIVED, overall, claims)
    }
}
