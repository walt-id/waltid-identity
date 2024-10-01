package id.walt.verifier.entra

import id.walt.commons.config.ConfigManager
import id.walt.policies.Verifier.runPolicyRequest
import id.walt.policies.models.PolicyRequest
import id.walt.policies.models.PolicyRequest.Companion.parsePolicyRequests
import id.walt.policies.models.PolicyResult
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.route
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
import io.ktor.server.util.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


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
    val tenantId: String, // "8bc955d9-38fd-4c15-a520-0c656407537a"
    val clientId: String, // "e50ceaa6-8554-4ae6-bfdf-fd95e2243ae0"
    val clientSecret: String, // "ctL8Q~Ezdrcrju85gEtvbCmQQDmm7bXjJKsdXbCr"
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
data class EntraAcceptableCredential(
    val type: String, val purpose: String? = null, val acceptedIssuers: List<String>? = null,
)

@Serializable
data class EntraVerificationRequest(
    val authorization: EntraAuthorizationInformation,

    val authority: String,
    // callback
    // client name

    val credentials: List<EntraAcceptableCredential>,
)

@OptIn(ExperimentalUuidApi::class)
object EntraVerifierApi {
    @Serializable
    private data class EntraCreatePresentationRequest(
        val authority: String,
        val callback: EntraCreatePresentationRequestCallback,
        val registration: EntraCreatePresentationRequestRegistration,
        val requestedCredentials: List<EntraAcceptableCredential>,
    ) {
        @Serializable
        data class EntraCreatePresentationRequestCallback(
            val url: String, val state: String, val headers: JsonObject,
        )

        @Serializable
        data class EntraCreatePresentationRequestRegistration(
            val clientName: String,
        )

        companion object {
            fun fromEntraVerificationRequest(
                req: EntraVerificationRequest,
                callback: EntraCreatePresentationRequestCallback,
            ): EntraCreatePresentationRequest =
                EntraCreatePresentationRequest(
                    authority = req.authority,
                    callback = callback,
                    registration = EntraCreatePresentationRequestRegistration("waltid-verifier-api"),
                    requestedCredentials = req.credentials
                )
        }
    }


    data class MappedData(
        val vcPolicies: List<PolicyRequest>,
        //val requestCredentials: List<PolicyRequest>
    ) {
        companion object {
            fun fromVerifierData(data: VerifierData): MappedData = MappedData(
                vcPolicies = data.vcPolicies.parsePolicyRequests(),
                //requestCredentials =
            )
        }
    }

    @Serializable
    data class VerifierData(
        @SerialName("vc_policies") val vcPolicies: JsonArray,
        //@SerialName("request_credentials") val requestCredentials: JsonArray
    )

    val callbackMapping = HashMap<Uuid, MappedData>()

    val config = ConfigManager.getConfig<EntraConfig>()

    val configuredCallbackUrl = config.callbackUrl

    fun createCallbackMapping(data: MappedData): Uuid {
        val uuid = Uuid.random()
        callbackMapping[uuid] = data

        return uuid
    }

    suspend fun createPresentationRequest(req: EntraVerificationRequest, data: VerifierData): Result<EntraVerifyResponse> {
        val accessToken = req.authorization.getAccessToken()


        val callbackMappingId = createCallbackMapping(MappedData.fromVerifierData(data))

        val callback = EntraCreatePresentationRequest.EntraCreatePresentationRequestCallback(
            url = "$configuredCallbackUrl/$callbackMappingId",
            state = "$callbackMappingId",
            headers = JsonObject(mapOf("api-key" to JsonPrimitive("1234")))
        )

        val createPresentationRequestBody = EntraCreatePresentationRequest.fromEntraVerificationRequest(req = req, callback = callback)

        val response = http.post("https://verifiedid.did.msidentity.com/v1.0/verifiableCredentials/createPresentationRequest") {
            header(HttpHeaders.Authorization, accessToken)
            contentType(ContentType.Application.Json)
            setBody(createPresentationRequestBody)
        }

        return runCatching {
            check(response.status == HttpStatusCode.Created) { "Entra did not respond with CREATED" }
            val responseObj = response.body<JsonObject>()
            val url = responseObj["url"]?.jsonPrimitive?.content
            check(url != null) { "Entra did not return a url!" }

            EntraVerifyResponse(url = url, nonce = callback.state)
        }
    }


    val policyStatusMapping =
        HashMap<Uuid, List<CredentialPolicyResults>>()
}

data class CredentialPolicyResults(
    val type: String,
    val credential: EntraVerificationApiResponse.VerifiedCredentialsData,
    val policies: List<PolicyResult>,
)


@Serializable
data class EntraVerifyRequest(
    val entraVerification: EntraVerificationRequest,
    val data: EntraVerifierApi.VerifierData,
)

@Serializable
data class EntraVerifyResponse(
    val url: String,
    val nonce: String,
)

@OptIn(ExperimentalUuidApi::class)
fun Application.entraVerifierApi() {
    routing {

        route("entra", {

        }) {
            post("verify", {
                tags = listOf("Entra Credential Verification")
                request { body<EntraVerifyRequest>() }
                response { HttpStatusCode.OK to { body<EntraVerifyResponse>() } }
            }) {
                val verifyRequest = context.receive<EntraVerifyRequest>()
                val res = EntraVerifierApi.createPresentationRequest(verifyRequest.entraVerification, verifyRequest.data)

                context.respond(res.getOrThrow())
            }

            post("verification-callback/{nonce}", {
                tags = listOf("Entra")
            }) {
                val nonce = context.parameters["nonce"]?.let { Uuid.parse(it) }

                println("--- ENTRA CALLBACK ---")
                println("Nonce: " + context.parameters["nonce"])
                println("Headers: " + context.request.headers)
                //println("Body: " + context.receiveText())
                println("URL: " + context.url())

                require(EntraVerifierApi.callbackMapping.containsKey(nonce)) { "Invalid nonce: $nonce" }

                val body = context.receiveText()
                println("Response: $body")
                val response = Json.decodeFromString<EntraVerificationApiResponse>(body)

                val callMapping = EntraVerifierApi.callbackMapping[nonce]!!

                response.verifiedCredentialsData?.let {
                    val result = response.verifiedCredentialsData.map {
                        val asJson = Json.encodeToJsonElement(it)
                        val policyResults = callMapping.vcPolicies.map {
                            PolicyResult(it, it.runPolicyRequest(asJson, emptyMap()))
                        }
                        CredentialPolicyResults(it.type.last(), it, policyResults)
                    }

                    EntraVerifierApi.policyStatusMapping[nonce!!] = result
                }

                context.respond(HttpStatusCode.OK)
            }

            get("status/{nonce}", {
                tags = listOf("Entra Credential Verification")
                request { pathParameter<String>("nonce") }
                response { HttpStatusCode.OK to { body<JsonArray>() } }
            }) {
                val nonce = context.parameters["nonce"]?.let { Uuid.parse(it) }

                val result =
                    EntraVerifierApi.policyStatusMapping[nonce]
                if (result != null) {

                    val output = buildJsonArray {
                        result.forEach {
                            addJsonObject {
                                put("type", it.type)
                                put("credential", Json.encodeToJsonElement(it.credential))
                                putJsonArray("policies") {
                                    it.policies.forEach {
                                        add(Json.encodeToJsonElement(it))
                                        // add(it.toJsonResult())
                                    }
                                }
                            }
                        }
                    }

                    context.respond(output)
                } else context.respond(HttpStatusCode.NotFound)
            }
        }
    }
}

fun main() {
    println(
        "entra: " + (System.getenv()["ENTRA_CALLBACK_URL"]
            ?: throw IllegalArgumentException("No ENTRA_CALLBACK_URL environment variable configured!"))
    )
}
