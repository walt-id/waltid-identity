package id.walt.issuer2.domain

import id.walt.openid4vci.responses.credential.CredentialResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import kotlin.time.Instant

/** Private, session-lived checkpoints; durable consumed-reference history is stored separately. */
@Serializable
data class CredentialStatusIssuanceState(
    val leaseId: String? = null,
    val leaseExpiresAt: Instant? = null,
    val operations: Map<String, CredentialStatusIssuanceOperation> = emptyMap(),
)

@OptIn(ExperimentalSerializationApi::class)
@JsonIgnoreUnknownKeys
@Serializable
data class CredentialStatusIssuanceOperation(
    val id: String,
    val holderKeys: List<String>,
    val response: CredentialResponse? = null,
) {
    init {
        require(holderKeys.isNotEmpty()) { "At least one verified holder binding is required" }
    }
}
