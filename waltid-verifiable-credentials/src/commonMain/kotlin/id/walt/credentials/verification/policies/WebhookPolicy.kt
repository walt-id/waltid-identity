package id.walt.credentials.verification.policies

import id.walt.credentials.verification.CredentialWrapperValidatorPolicy
import id.walt.credentials.verification.WebhookPolicyException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
class WebhookPolicy : CredentialWrapperValidatorPolicy(
    "webhook",
    "Sends the credential data to an webhook URL as HTTP POST, and returns the verified status based on the webhooks set status code (success = 200 - 299)."
) {

    val http = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any> {
        val url = (args as JsonPrimitive).content

        val response = http.post(url) {
            setBody(data)
            header(HttpHeaders.ContentType, ContentType.Application.Json)
        }

        return if (response.status.isSuccess()) Result.success(response.body<JsonObject>())
        else Result.failure(WebhookPolicyException(response.body<JsonObject>()))
    }
}
