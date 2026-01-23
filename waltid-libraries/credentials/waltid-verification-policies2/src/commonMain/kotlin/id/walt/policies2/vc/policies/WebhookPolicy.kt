package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.WebhookPolicyException
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("webhook")
data class WebhookPolicy(
    val url: String,

    @SerialName("basicauth_username")
    val basicAuthUsername: String? = null,
    @SerialName("basicauth_password")
    val basicAuthPassword: String? = null,

    @SerialName("bearerauth_token")
    val bearerAuthToken: String? = null,
) : CredentialVerificationPolicy2() {
    override val id = "webhook"

    companion object {
        private val http = HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }
    }

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        val responseResult = runCatching {
            http.post(url) {
                setBody(credential)
                header(HttpHeaders.ContentType, ContentType.Application.Json)

                if (basicAuthUsername != null && basicAuthPassword != null) {
                    basicAuth(basicAuthUsername, basicAuthPassword)
                }

                if (bearerAuthToken != null) {
                    bearerAuth(bearerAuthToken)
                }
            }
        }

        val response = responseResult.getOrElse { ex ->
            return Result.failure(IllegalArgumentException("Could not contact webhook URL: $url", ex))
        }

        val responseData = if (response.contentType()?.match(ContentType.Application.Json) == true) {
            response.body<JsonObject>()
        } else JsonNull

        return if (response.status.isSuccess()) Result.success(responseData)
        else Result.failure(WebhookPolicyException(responseData))

    }
}
