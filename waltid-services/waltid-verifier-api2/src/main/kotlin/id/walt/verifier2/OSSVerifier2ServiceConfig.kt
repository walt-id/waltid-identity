package id.walt.verifier2

import id.walt.commons.config.WaltConfig
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

data class OSSVerifier2ServiceConfig(
    /** When omitted, unsigned cross-device sessions use `redirect_uri:<response_uri>`. Signed requests require an explicit value. */
    val clientId: String? = null,
    val clientMetadata: ClientMetadata? = null,
    val urlPrefix: String,
    val urlHost: String,
    /** Legacy key migrated to crypto2 in memory when [requestSigningStoredKey] is absent. */
    val key: JsonObject? = null,
    val x5c: List<String>? = null,
    /** Preferred encoded crypto2 StoredKey. Invalid values fail startup without falling back to [key]. */
    val requestSigningStoredKey: String? = null,
    /**
     * How long a used verification session is kept, in ISO-8601 duration format, e.g. `P30D`.
     *
     * A request may override it per session with `retention_duration`/`retention_date`. Omitted means the
     * historical ten years; `PT2147483647S`-style infinite values, or any non-finite duration, mean keep
     * indefinitely and never expire the record.
     */
    val sessionRetention: Duration? = null,
    /**
     * Ceiling on how many verification sessions the in-memory store keeps, or null for
     * [DEFAULT_MAX_IN_MEMORY_SESSIONS].
     *
     * Only applies to the default in-memory repository; a deployment with a persistent repository is bounded by
     * its database instead. Raise it when sessions are small and more history is wanted, lower it when they are
     * large - a count alone cannot bound memory, which is why [maxInMemoryBytes] exists as well.
     */
    val maxInMemorySessions: Int? = null,
    /**
     * Budget for the in-memory store in bytes of estimated retained size, or null for
     * [DEFAULT_MAX_IN_MEMORY_BYTES].
     *
     * This is the number to raise or lower after an out-of-memory incident, because a session's cost is set by
     * the credential presented to it: an mdoc carrying a 230 KB portrait measured 1.64 MiB retained per session,
     * so the same session count can mean 16 MB or 3.3 GB. Keep it well under the heap, since the store is not
     * the only thing on it.
     */
    val maxInMemoryBytes: Long? = null,
) : WaltConfig() {
    /** Preserves the JVM constructor descriptor from before the StoredKey field was added. */
    constructor(
        clientId: String?,
        clientMetadata: ClientMetadata?,
        urlPrefix: String,
        urlHost: String,
        key: JsonObject?,
        x5c: List<String>?,
    ) : this(clientId, clientMetadata, urlPrefix, urlHost, key, x5c, null)

    /** Preserves the JVM constructor descriptor from before session retention became configurable. */
    constructor(
        clientId: String?,
        clientMetadata: ClientMetadata?,
        urlPrefix: String,
        urlHost: String,
        key: JsonObject?,
        x5c: List<String>?,
        requestSigningStoredKey: String?,
    ) : this(clientId, clientMetadata, urlPrefix, urlHost, key, x5c, requestSigningStoredKey, null)

    /** Preserves the JVM constructor descriptor from before the in-memory store limits became configurable. */
    constructor(
        clientId: String?,
        clientMetadata: ClientMetadata?,
        urlPrefix: String,
        urlHost: String,
        key: JsonObject?,
        x5c: List<String>?,
        requestSigningStoredKey: String?,
        sessionRetention: Duration?,
    ) : this(clientId, clientMetadata, urlPrefix, urlHost, key, x5c, requestSigningStoredKey, sessionRetention, null, null)
}
