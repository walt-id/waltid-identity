package id.walt.openid4vci.repository.par

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Stored pushed authorization request payload.
 *
 * Applications can supply custom implementations if they need extra persistence fields.
 */
interface PARRecord {
    val requestId: String
    val requestParameters: Map<String, List<String>>
    val clientId: String
    val createdAt: Instant
    val expiresAt: Instant
    val clientMetadata: Map<String, String>
}

@Serializable
data class DefaultPARRecord(
    override val requestId: String,
    override val requestParameters: Map<String, List<String>>,
    override val createdAt: Instant,
    override val expiresAt: Instant,
    override val clientMetadata: Map<String, String> = emptyMap(),
) : PARRecord {
    override val clientId: String = requestParameters["client_id"].orEmpty()
        .singleOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Pushed authorization request must contain exactly one non-blank client_id")

    init {
        require(requestId.isNotBlank()) { "requestId must not be blank" }
        require(expiresAt > createdAt) { "expiresAt must be after createdAt" }
        require(requestParameters["request_uri"].orEmpty().none { it.isNotBlank() }) {
            "Pushed authorization request must not contain request_uri"
        }
    }
}

internal fun PARRecord.isValid(now: Instant): Boolean =
    now < expiresAt
