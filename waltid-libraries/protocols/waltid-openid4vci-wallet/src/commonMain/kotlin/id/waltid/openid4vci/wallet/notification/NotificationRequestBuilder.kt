package id.waltid.openid4vci.wallet.notification

import id.walt.openid4vci.errors.NotificationError
import id.walt.openid4vci.errors.OAuthError
import id.walt.openid4vci.requests.notification.NotificationRequest
import id.walt.openid4vci.tokens.access.AccessTokenAuthorizationScheme
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import id.waltid.openid4vci.wallet.dpop.USE_DPOP_NONCE
import id.waltid.openid4vci.wallet.dpop.postWithDpopNonceRetry
import id.waltid.openid4vci.wallet.token.DPoPProofFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
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

/** How the notification request presents its access token. */
enum class NotificationAccessTokenType {
    BEARER,
    DPOP,
    ;

    val authorizationScheme: String
        get() = when (this) {
            BEARER -> "Bearer"
            DPOP -> "DPoP"
        }

    companion object {
        fun fromTokenType(tokenType: String): NotificationAccessTokenType? =
            when (AccessTokenAuthorizationScheme.fromValue(tokenType)) {
                AccessTokenAuthorizationScheme.BEARER -> BEARER
                AccessTokenAuthorizationScheme.DPOP -> DPOP
                null -> null
            }
    }
}

/** Stable failure categories for an OpenID4VCI Notification Endpoint request. */
enum class NotificationRequestError {
    INVALID_ENDPOINT,
    NETWORK,
    ISSUER_RESPONSE,
}

sealed class NotificationDeliveryResult {
    data class Success(val request: NotificationRequest) : NotificationDeliveryResult()
    data class Failure(
        val statusCode: Int,
        val error: NotificationError?,
    ) : NotificationDeliveryResult()

    data class OAuthFailure(
        val statusCode: Int,
        val error: OAuthError?,
    ) : NotificationDeliveryResult()

    data class TransportFailure(
        val error: NotificationRequestError,
        val statusCode: Int? = null,
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
        accessTokenType: NotificationAccessTokenType,
        request: NotificationRequest,
        dpopProofFactory: DPoPProofFactory? = null,
    ): NotificationDeliveryResult {
        validateEndpoint(notificationEndpoint)?.let { return it }
        if (accessToken.isBlank()) {
            return NotificationDeliveryResult.TransportFailure(NotificationRequestError.INVALID_ENDPOINT)
        }
        when (accessTokenType) {
            NotificationAccessTokenType.BEARER ->
                if (dpopProofFactory != null) {
                    return NotificationDeliveryResult.TransportFailure(NotificationRequestError.INVALID_ENDPOINT)
                }
            NotificationAccessTokenType.DPOP ->
                if (dpopProofFactory == null) {
                    return NotificationDeliveryResult.TransportFailure(NotificationRequestError.INVALID_ENDPOINT)
                }
        }

        return try {
            executeNotificationRequest(
                notificationEndpoint = notificationEndpoint,
                accessToken = accessToken,
                accessTokenType = accessTokenType,
                request = request,
                dpopProofFactory = dpopProofFactory,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            NotificationDeliveryResult.TransportFailure(NotificationRequestError.NETWORK)
        }
    }

    private suspend fun executeNotificationRequest(
        notificationEndpoint: String,
        accessToken: String,
        accessTokenType: NotificationAccessTokenType,
        request: NotificationRequest,
        dpopProofFactory: DPoPProofFactory?,
    ): NotificationDeliveryResult {
        val response = try {
            httpClient.postWithDpopNonceRetry(
                endpoint = notificationEndpoint,
                accessToken = accessToken,
                authorizationScheme = accessTokenType.authorizationScheme,
                dpopProofFactory = dpopProofFactory,
            ) {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return NotificationDeliveryResult.TransportFailure(NotificationRequestError.NETWORK)
        }

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
            else -> NotificationDeliveryResult.TransportFailure(
                error = NotificationRequestError.ISSUER_RESPONSE,
                statusCode = response.status.value,
            )
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

    private fun validateEndpoint(endpoint: String): NotificationDeliveryResult.TransportFailure? {
        if (endpoint.isBlank()) {
            return NotificationDeliveryResult.TransportFailure(NotificationRequestError.INVALID_ENDPOINT)
        }
        return try {
            val url = Url(endpoint)
            if (url.host.isBlank()) {
                NotificationDeliveryResult.TransportFailure(NotificationRequestError.INVALID_ENDPOINT)
            } else {
                null
            }
        } catch (_: Exception) {
            NotificationDeliveryResult.TransportFailure(NotificationRequestError.INVALID_ENDPOINT)
        }
    }
}
