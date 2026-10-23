package id.walt.verifier2

/**
 * What `POST /vical/fetch` is allowed to retrieve. The shipped default is [NETWORK_ONLY].
 *
 * Filesystem modes exist for operators who import a VICAL from disk. They read any regular file the
 * process can read, and this service does not authenticate the caller.
 */
enum class VicalFetchMode {
    DISABLED,
    NETWORK_ONLY,
    FILESYSTEM_ONLY,
    BOTH,
    ;

    val allowsHttp: Boolean get() = this == NETWORK_ONLY || this == BOTH
    val allowsFile: Boolean get() = this == FILESYSTEM_ONLY || this == BOTH

    companion object {
        fun parse(raw: String): VicalFetchMode {
            val normalized = raw.trim().lowercase().replace('-', '_')
            return entries.firstOrNull { it.name.lowercase() == normalized }
                ?: throw IllegalArgumentException(
                    "Unknown vicalFetchMode '$raw'. Expected disabled, network-only, filesystem-only, or both.",
                )
        }
    }
}
