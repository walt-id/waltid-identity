package id.walt.openid4vci.repository.par

import id.walt.openid4vci.requests.authorization.AuthorizationRequest
import id.walt.openid4vci.requests.authorization.DefaultAuthorizationRequest
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Stored pushed authorization request payload.
 *
 * Applications can supply custom implementations if they need extra persistence fields.
 */
interface PARRecord {
    val requestId: String
    val authorizationRequest: AuthorizationRequest
    val clientId: String
    val createdAt: Instant
    val expiresAt: Instant
    val clientMetadata: Map<String, String>
}

@Serializable
data class DefaultPARRecord(
    override val requestId: String,
    override val authorizationRequest: DefaultAuthorizationRequest,
    override val createdAt: Instant,
    override val expiresAt: Instant,
    override val clientMetadata: Map<String, String> = emptyMap(),
) : PARRecord {
    override val clientId: String
        get() = authorizationRequest.client.id

    init {
        require(clientId.isNotBlank()) { "Pushed authorization request must have a non-blank client_id" }
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
        require(authorizationRequest.requestForm["request_uri"].orEmpty().none { it.isNotBlank() }) {
            "Pushed authorization request must not contain request_uri"
        }
    }
}

internal fun PARRecord.isValid(now: Instant): Boolean =
    now < expiresAt
