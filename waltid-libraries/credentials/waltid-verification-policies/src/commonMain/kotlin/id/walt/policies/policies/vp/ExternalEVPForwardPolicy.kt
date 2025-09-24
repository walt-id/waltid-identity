package id.walt.policies.policies.vp

import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.w3c.utils.VCFormat
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
class ExternalEVPForwardPolicy : CredentialWrapperValidatorPolicy() {
    override val name: String = "external-evp-forward"
    override val description: String =
        "Forwards the Enveloped VP JWT (evp_jwt) to an external service endpoint and interprets the response. Configure the target URL via policy args: { url: string, timeoutMs?: number }."
    override val supportedVCFormats = setOf(VCFormat.jwt_vp, VCFormat.jwt_vp_json)

    companion object {
        private val http = HttpClient {
            install(ContentNegotiation) { json() }
            install(HttpTimeout) {
                requestTimeoutMillis = 5000
                connectTimeoutMillis = 5000
                socketTimeoutMillis = 5000
            }
        }
    }

    @Serializable
    data class Args(val url: String, val timeoutMs: Long? = null)


    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val argsObj = when (args) {
            is JsonObject -> runCatching { Json.decodeFromJsonElement(Args.serializer(), args) }.getOrNull()
            else -> null
        }
            ?: return Result.failure(IllegalArgumentException("external-evp-forward policy requires args: { url: string, timeoutMs?: number }"))

        val vpTokenArray = context["vp_token"] as? JsonArray
        val evpJwt = vpTokenArray?.getOrNull(1)?.jsonPrimitive?.content

        println("Second vp_token: $evpJwt")
        val client = if (argsObj.timeoutMs != null) {
            HttpClient {
                install(ContentNegotiation) { json() }
                install(HttpTimeout) {
                    requestTimeoutMillis = argsObj.timeoutMs
                    connectTimeoutMillis = argsObj.timeoutMs
                    socketTimeoutMillis = argsObj.timeoutMs
                }
            }
        } else http

        return try {
            val response = client.post(argsObj.url) {
                header(HttpHeaders.ContentType, ContentType.Application.Any)
                setBody(evpJwt)
            }

            println("response: $response")
            println("the vp sent to gaia is $evpJwt")
            val body =
                runCatching { response.body<JsonObject>() }.getOrElse { JsonObject(mapOf("raw" to JsonPrimitive(response.toString()))) }
            if (response.status.isSuccess()) Result.success(body) else Result.failure(Exception(body.toString()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
