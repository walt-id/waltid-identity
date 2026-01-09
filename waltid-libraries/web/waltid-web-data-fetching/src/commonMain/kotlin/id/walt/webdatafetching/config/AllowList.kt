package id.walt.webdatafetching.config

import kotlinx.serialization.Serializable

@Serializable
data class AllowList<T>(
    val whitelist: List<T>? = null,
    val blacklist: List<T>? = null
) {
    init {
        require(whitelist != null || blacklist != null) { "Requiring either whitelist or blacklist to define an AllowList" }
    }

    fun isBlacklisted(value: T) = blacklist?.contains(value) == true
    fun isWhitelisted(value: T) = whitelist?.contains(value) == true

    fun isAllowed(value: T): Boolean {
        if (whitelist != null) {
            return isWhitelisted(value)
        }
        if (blacklist != null) {
            return !isBlacklisted(value)
        }

        // This cannot happen
        throw IllegalStateException("Neither whitelist nor blacklist is defined")
    }
}
