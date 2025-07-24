package id.walt.commons.config.list

import kotlinx.serialization.Serializable

@Serializable
data class WebConfig(
    /**
     * What host should the server listen on, mostly either
     * - 127.0.0.1: only locally accessible
     * - 0.0.0.0: accessible from all network interfaces
     */
    val webHost: String = "0.0.0.0",
    val webPort: Int = 3000,

    /**
     * Slower, but human-readable: Pretty-print encoded JSON results
     */
    val humanReadableResultEncoding: Boolean = false
)
