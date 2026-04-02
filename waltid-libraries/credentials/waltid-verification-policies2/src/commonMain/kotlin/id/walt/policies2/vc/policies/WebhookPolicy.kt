package id.walt.policies2.vc.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies2.vc.WebhookPolicyException
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.webdatafetching.WebDataFetchingConfiguration
import id.walt.webdatafetching.config.RequestConfiguration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonElement

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

    @Transient
    private val web = WebDataFetcher(
        WebDataFetcherId.WEBHOOK_POLICY2,
        defaultConfiguration = WebDataFetchingConfiguration(
            request = RequestConfiguration(
                auth = when {
                    basicAuthUsername != null && basicAuthPassword != null -> RequestConfiguration.HttpAuthConfiguration.BasicAuth(
                        username = basicAuthUsername,
                        password = basicAuthPassword
                    )

                    bearerAuthToken != null -> RequestConfiguration.HttpAuthConfiguration.BearerAuth(bearerAuthToken)
                    else -> null
                }
            )
        )
    )

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {
        val responseResult = runCatching {
            web.send<DigitalCredential, JsonElement>(url, credential)
        }

        val response = responseResult.getOrElse { ex ->
            return Result.failure(IllegalArgumentException("Could not contact webhook URL: $url", ex))
        }

        return if (response.success) Result.success(response.body)
        else Result.failure(WebhookPolicyException(response.body))

    }
}
