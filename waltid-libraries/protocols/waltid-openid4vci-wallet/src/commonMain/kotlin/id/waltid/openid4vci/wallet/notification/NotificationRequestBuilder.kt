package id.waltid.openid4vci.wallet.notification

import id.walt.openid4vci.errors.NotificationError
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.notification.DefaultNotificationRequest
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/** Stable failure categories for an OpenID4VCI Notification Endpoint request. */
enum class NotificationRequestError {
    INVALID_ENDPOINT,
    NETWORK,
    ISSUER_RESPONSE,
}

/** Sanitized notification-endpoint failure that never retains token, proof, or response body material. */
class NotificationRequestException internal constructor(
    val error: NotificationRequestError,
    val statusCode: Int? = null,
) : Exception(
    buildString {
        append("Notification request failed: ")
        append(error.name.lowercase())
        statusCode?.let { append(" (HTTP ").append(it).append(')') }
    },
)

sealed class NotificationDeliveryResult {
    data class Success(val request: DefaultNotificationRequest) : NotificationDeliveryResult()
    data class Failure(
        val statusCode: Int,
        val error: NotificationError?,
    ) : NotificationDeliveryResult()

    data class OAuthFailure(
        val statusCode: Int,
        val error: OAuthError?,
    ) : NotificationDeliveryResult()

    fun isSuccess(): Boolean = this is Success
}

/** Sends OpenID4VCI 1.0 Section 11 events to the issuer's advertised Notification Endpoint. */
class NotificationRequestBuilder(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun send(
        notificationEndpoint: String,
        accessToken: String,
        request: DefaultNotificationRequest,
    ): NotificationDeliveryResult {
        validateEndpoint(notificationEndpoint)
        require(accessToken.isNotBlank()) { "Access token cannot be blank" }

        val response = postNotification(
            notificationEndpoint = notificationEndpoint,
            accessToken = accessToken,
            request = request,
        )

        if (response.status.isSuccess()) {
            return NotificationDeliveryResult.Success(request)
        }

        return when (response.status.value) {
            400 -> NotificationDeliveryResult.Failure(
                statusCode = response.status.value,
                error = response.notificationError(),
            )

            401 -> NotificationDeliveryResult.OAuthFailure(
                statusCode = response.status.value,
                error = response.oauthError(),
            )

            else -> throw NotificationRequestException(
                error = NotificationRequestError.ISSUER_RESPONSE,
                statusCode = response.status.value,
            )
        }
    }

    private suspend fun postNotification(
        notificationEndpoint: String,
        accessToken: String,
        request: DefaultNotificationRequest,
    ): HttpResponse {
        return try {
            httpClient.post(notificationEndpoint) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                setBody(json.encodeToString(request))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            throw NotificationRequestException(NotificationRequestError.NETWORK)
        }
    }

    private suspend fun HttpResponse.notificationError(): NotificationError? =
        decodeErrorBody<NotificationError>()

    private suspend fun HttpResponse.oauthError(): OAuthError? =
        decodeErrorBody()

    private suspend inline fun <reified T> HttpResponse.decodeErrorBody(): T? =
        try {
            json.decodeFromString<T>(bodyAsText())
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    private fun validateEndpoint(endpoint: String) {
        if (endpoint.isBlank()) {
            throw NotificationRequestException(NotificationRequestError.INVALID_ENDPOINT)
        }
        try {
            val url = Url(endpoint)
            if (url.host.isBlank()) {
                throw NotificationRequestException(NotificationRequestError.INVALID_ENDPOINT)
            }
        } catch (error: NotificationRequestException) {
            throw error
        } catch (_: Exception) {
            throw NotificationRequestException(NotificationRequestError.INVALID_ENDPOINT)
        }
    }
}
