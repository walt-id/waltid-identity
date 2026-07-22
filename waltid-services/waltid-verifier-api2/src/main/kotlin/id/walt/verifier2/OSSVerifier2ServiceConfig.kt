package id.walt.verifier2

import id.walt.commons.config.WaltConfig
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class OSSVerifier2ServiceConfig(
    val clientId: String,
    val clientMetadata: ClientMetadata? = null,
    val urlPrefix: String,
    val urlHost: String,
    /** Legacy key migrated to crypto2 in memory when [requestSigningStoredKey] is absent. */
    val key: JsonObject? = null,
    val x5c: List<String>? = null,
    /** Preferred encoded crypto2 StoredKey. Invalid values fail startup without falling back to [key]. */
    val requestSigningStoredKey: String? = null,
) : WaltConfig() {
    /** Preserves the JVM constructor descriptor from before the StoredKey field was added. */
    constructor(
        clientId: String,
        clientMetadata: ClientMetadata?,
        urlPrefix: String,
        urlHost: String,
        key: JsonObject?,
        x5c: List<String>?,
    ) : this(clientId, clientMetadata, urlPrefix, urlHost, key, x5c, null)
}
