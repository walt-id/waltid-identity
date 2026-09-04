package id.waltid.openid4vci.wallet.notification

import id.walt.openid4vci.errors.NotificationError
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.notification.DefaultNotificationRequest
import id.waltid.openid4vci.wallet.dpop.DPOP_HEADER
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_ATTEMPTS
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import id.waltid.openid4vci.wallet.dpop.USE_DPOP_NONCE
import id.waltid.openid4vci.wallet.token.DPoPProofFactory
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
        dpopProofFactory: DPoPProofFactory? = null,
    ): NotificationDeliveryResult {
        validateEndpoint(notificationEndpoint)
        require(accessToken.isNotBlank()) { "Access token cannot be blank" }

        return executeNotificationRequest(
            notificationEndpoint = notificationEndpoint,
            accessToken = accessToken,
            request = request,
            dpopProofFactory = dpopProofFactory,
        )
    }

    private suspend fun executeNotificationRequest(
        notificationEndpoint: String,
        accessToken: String,
        request: DefaultNotificationRequest,
        dpopProofFactory: DPoPProofFactory?,
    ): NotificationDeliveryResult {
        var dpopNonce: String? = null
        repeat(DPOP_NONCE_ATTEMPTS) { attempt ->
            val response = postNotification(
                notificationEndpoint = notificationEndpoint,
                accessToken = accessToken,
                request = request,
                dpopProofFactory = dpopProofFactory,
                dpopNonce = dpopNonce,
            )

            if (response.status.isSuccess()) {
                return NotificationDeliveryResult.Success(request)
            }

            when (response.status.value) {
                400 -> return NotificationDeliveryResult.Failure(
                    statusCode = response.status.value,
                    error = response.notificationError(),
                )

                401 -> {
                    val oauthError = response.oauthError()
                    val suppliedNonce = response.headers[DPOP_NONCE_HEADER]
                    if (
                        attempt == 0 &&
                        dpopProofFactory != null &&
                        oauthError?.error == USE_DPOP_NONCE &&
                        !suppliedNonce.isNullOrBlank()
                    ) {
                        dpopNonce = suppliedNonce
                        return@repeat
                    }
                    return NotificationDeliveryResult.OAuthFailure(
                        statusCode = response.status.value,
                        error = oauthError,
                    )
                }

                else -> throw NotificationRequestException(
                    error = NotificationRequestError.ISSUER_RESPONSE,
                    statusCode = response.status.value,
                )
            }
        }
        error("DPoP nonce retry exhausted for the notification endpoint")
    }

    private suspend fun postNotification(
        notificationEndpoint: String,
        accessToken: String,
        request: DefaultNotificationRequest,
        dpopProofFactory: DPoPProofFactory?,
        dpopNonce: String?,
    ): HttpResponse {
        return try {
            val dpopProof = dpopProofFactory?.invoke(notificationEndpoint, dpopNonce)
            val authorizationScheme = if (dpopProofFactory == null) "Bearer" else "DPoP"
            httpClient.post(notificationEndpoint) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "$authorizationScheme $accessToken")
                dpopProof?.let { header(DPOP_HEADER, it) }
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

    private suspend fun HttpResponse.oauthError(): OAuthError? {
        if (headers[HttpHeaders.WWWAuthenticate]?.contains(USE_DPOP_NONCE, ignoreCase = true) == true) {
            return OAuthError(USE_DPOP_NONCE)
        }
        return decodeErrorBody()
    }

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
