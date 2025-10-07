package id.walt.policies.policies

import id.walt.policies.CredentialWrapperValidatorPolicy
import id.walt.w3c.utils.VCFormat
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
class WebhookPolicy : CredentialWrapperValidatorPolicy(
) {


    override val name = "webhook"
    override val description =
        "Sends the credential data to an webhook URL as HTTP POST, and returns the verified status based on the webhooks set status code (success = 200 - 299)."
    override val supportedVCFormats = setOf(VCFormat.jwt_vc, VCFormat.jwt_vc_json, VCFormat.ldp_vc)

    companion object {
        private val http = HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val url = (args as JsonPrimitive).content

        val response = http.post(url) {
            setBody(data)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        return if (response.status.isSuccess()) Result.success(response.body<JsonObject>())
        else Result.failure(id.walt.policies.WebhookPolicyException(response.body<JsonObject>()))
    }
}
