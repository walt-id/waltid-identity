package id.walt.issuer.entra

import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

private val http = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(Logging) {
        logger = Logger.SIMPLE
        level = LogLevel.ALL
    }
    followRedirects = false
}

@Serializable
data class EntraAuthorizationInformation(
    val tenantId: String,
    val clientId: String,
    val clientSecret: String,
    val scope: String, // 3db474b9-6a0c-4840-96ac-1fceb342124f/.default
) {
    suspend fun getAccessToken(): String {
        val response = http.submitForm("https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token", parameters {
            append("client_id", clientId)
            append("scope", scope)
            append("client_secret", clientSecret)
            append("grant_type", "client_credentials")
        }).body<JsonObject>()
        return "${response["token_type"]!!.jsonPrimitive.content} ${response["access_token"]!!.jsonPrimitive.content}"
    }
}

@Serializable
data class EntraIssuanceRequestData(
    val authority: String,
    val type: String,
    val manifest: String,
    val claims: JsonObject,
)

@Serializable
data class EntraRegistration(
    val clientName: String,
)

@Serializable
data class EntraFullIssuanceRequest(
    val authority: String,
    val registration: EntraRegistration,
    val type: String,
    val manifest: String,
    val claims: JsonObject,
)

@Serializable
data class EntraIssuanceRequest(
    val authorization: EntraAuthorizationInformation,
    val data: EntraIssuanceRequestData,
)

fun Application.entraIssuance() {
    routing {
        route("entra", {
            tags = listOf("entra")
        }) {
            post("issue", {
                request { body<EntraIssuanceRequest> { required = true } }
            }) {
                val req = call.receive<EntraIssuanceRequest>()

                val url = EntraIssuanceApi.entraIssuance(req.authorization, req.data)

                call.respond(url)
            }
        }
    }
}

object EntraIssuanceApi {
    suspend fun entraIssuance(authorization: EntraAuthorizationInformation, req: EntraIssuanceRequestData): String {
        println("--- ENTRA ISSUANCE TEST ---")

        println("============ Issuer ============")

        println("> Doing Entra authorize...")
        val accessToken = authorization.getAccessToken()
        println("> Using access token: $accessToken")

        val createIssuanceReq = EntraFullIssuanceRequest(
            authority = req.authority,
            registration = EntraRegistration("waltid-issuer-api"),
            type = req.type,
            manifest = req.manifest,
            claims = req.claims

        )

        println("> Create issuance request: $createIssuanceReq")

        val createIssuanceRequestUrl =
            "https://verifiedid.did.msidentity.com/v1.0/verifiableCredentials/createIssuanceRequest"

        println("> Sending HTTP POST with create issuance request to: $createIssuanceRequestUrl")
        val response = http.post(createIssuanceRequestUrl) {
            header(HttpHeaders.Authorization, accessToken)
            contentType(ContentType.Application.Json)
            setBody(createIssuanceReq)
        }
        println("> Response: $response")

        check(response.status == HttpStatusCode.Created) { "Invalid Entra response: $response" }

        val responseObj = response.body<JsonObject>()
        println("> Response JSON body: $responseObj")

        val url =
            responseObj["url"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("No url from Entra received")

        return url
    }
}
