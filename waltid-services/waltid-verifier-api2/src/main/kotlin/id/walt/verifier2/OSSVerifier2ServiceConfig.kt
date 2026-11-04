package id.walt.verifier2

import id.walt.commons.config.WaltConfig
import id.walt.verifier.openid.models.authorization.ClientMetadata
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

/**
 * JVM overloads preserve the compatibility constructor descriptors without exposing secondary Kotlin
 * constructors to Hoplite. Hoplite otherwise accepts an older constructor and silently ignores newer config.
 *
 * Preserves the JVM constructor descriptor from before the StoredKey field was added.
 * Preserves the JVM constructor descriptor from before session retention became configurable.
 * Preserves the JVM constructor descriptor from before the in-memory store limits became configurable.
 * Preserves the JVM constructor descriptor from before VICAL fetch mode became configurable.
 */
data class OSSVerifier2ServiceConfig @JvmOverloads constructor(
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
     * How long a used verification session is kept, in Hoplite duration format, e.g. `30d`.
     *
     * A request may override it per session with `retention_duration`/`retention_date`. Omitted means the
     * historical ten years; Non-finite durations mean keep
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
    /**
     * When false, `POST /vical/fetch` is not registered. `POST /vical/validate` stays available.
     * Fetch accepts only `http` and `https`. This service does not authenticate callers.
     */
    val vicalFetchEnabled: Boolean = true,
    /**
     * When true, network fetches may target RFC1918 and IPv6 unique-local addresses. Loopback, link-local
     * and cloud-metadata addresses stay blocked. Default false, including on the public demo.
     */
    val allowPrivateNetworkVical: Boolean = false,
) : WaltConfig()
