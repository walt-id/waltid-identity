package id.walt.wallet2.handlers

import id.walt.openid4vci.requests.notification.NotificationEvent
import id.walt.openid4vci.requests.notification.NotificationRequest
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.waltid.openid4vci.wallet.notification.NotificationAccessTokenType
import id.waltid.openid4vci.wallet.notification.NotificationDeliveryResult
import id.waltid.openid4vci.wallet.notification.NotificationRequestBuilder
import id.waltid.openid4vci.wallet.token.DPoPProofFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException

private val notificationLog = KotlinLogging.logger {}

/**
 * Best-effort OpenID4VCI notification. Missing endpoint or notification id is a no-op.
 * A DPoP access token is not sent as Bearer when no proof factory is available.
 * Delivery failures are logged and do not fail issuance.
 */
internal suspend fun deliverCredentialNotification(
    httpClient: HttpClient,
    notificationEndpoint: String?,
    notificationId: String?,
    accessToken: String,
    tokenType: String,
    event: NotificationEvent,
    eventDescription: String? = null,
    dpopProofFactory: DPoPProofFactory? = null,
) {
    val endpoint = notificationEndpoint ?: return
    val id = notificationId ?: return
    val accessTokenType = when {
        tokenType.equals("DPoP", ignoreCase = true) -> NotificationAccessTokenType.DPOP
        tokenType.equals("Bearer", ignoreCase = true) -> NotificationAccessTokenType.BEARER
        else -> {
            notificationLog.warn { "Skipping issuer notification for unsupported token type, event=$event" }
            return
        }
    }
    if (accessTokenType == NotificationAccessTokenType.DPOP && dpopProofFactory == null) {
        notificationLog.warn { "Skipping issuer notification because the DPoP access token has no proof, event=$event" }
        return
    }

    try {
        val result = NotificationRequestBuilder(httpClient).send(
            notificationEndpoint = endpoint,
            accessToken = accessToken,
            accessTokenType = accessTokenType,
            request = NotificationRequest(
                notificationId = id,
                event = event,
                eventDescription = eventDescription,
            ),
            dpopProofFactory = dpopProofFactory,
        )
        when (result) {
            is NotificationDeliveryResult.Success -> Unit
            is NotificationDeliveryResult.Failure -> notificationLog.warn {
                "Issuer notification was rejected: status=${result.statusCode}, error=${result.error?.error}, event=$event"
            }
            is NotificationDeliveryResult.OAuthFailure -> notificationLog.warn {
                "Issuer notification authorization failed: status=${result.statusCode}, error=${result.error?.error}, event=$event"
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        notificationLog.warn(error) { "Issuer notification delivery failed for event=$event" }
    }
}

internal fun dpopProofFactoryFor(
    tokenType: String,
    dpopAlgorithms: Set<String>?,
    keyMaterial: WalletKeyStoreEntry,
    accessToken: String,
): DPoPProofFactory? {
    val algorithms = dpopAlgorithmsForToken(tokenType, dpopAlgorithms) ?: return null
    return { endpoint, nonce ->
        buildDpopProof(
            keyMaterial = keyMaterial,
            algorithms = algorithms,
            endpoint = endpoint,
            accessToken = accessToken,
            nonce = nonce,
        )
    }
}
