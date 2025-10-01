package id.walt.policies.policies.vp

import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.w3c.utils.VCFormat
import io.github.oshai.kotlinlogging.KotlinLogging.logger
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
class ExternalEVPForwardPolicy : CredentialWrapperValidatorPolicy() {
    val logger = logger { }
    override val name: String = "external-evp-forward"
    override val description: String =
        "Forwards the Enveloped VP JWT (evp_jwt) to an external service endpoint and interprets the response. Configure the target URL via policy args: string (the target URL)."
    override val supportedVCFormats = setOf(VCFormat.jwt_vp, VCFormat.jwt_vp_json)

    companion object {
        private val http = HttpClient {
            install(ContentNegotiation) { json() }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val url: String = when (args) {
            is String -> args
            is JsonPrimitive -> if (args.isString) args.content else null
            else -> null
        }
            ?: return Result.failure(IllegalArgumentException("external-evp-forward policy requires args as string (target URL)"))

        val vpTokenArray = context["vp_token"] as? JsonArray
        val evpJwt = vpTokenArray?.getOrNull(1)?.jsonPrimitive?.content

        val client = http

        return try {
            val response = client.post(url) {
                header(HttpHeaders.ContentType, ContentType.Application.Any)
                setBody(evpJwt)
            }

            logger.debug { "External EVP Forward Policy: response status: ${response.status}" }
            val body =
                runCatching { response.body<JsonObject>() }.getOrElse { JsonObject(mapOf("raw" to JsonPrimitive(response.toString()))) }
            if (response.status.isSuccess()) Result.success(body) else Result.failure(Exception(body.toString()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
