package id.walt.webdatafetching.config

import io.github.reactivecircus.cache4k.Cache
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Serializable
data class CacheConfiguration(
    val expireAfterAccess: Duration?,
    val expireAfterWrite: Duration?,
    val maximumCacheSize: Long?
) {
    fun <T : Any> buildCache() = Cache.Builder<String, T>()
        .apply { if (expireAfterAccess != null) expireAfterAccess(expireAfterAccess) }
        .apply { if (expireAfterWrite != null) expireAfterWrite(expireAfterWrite) }
        .apply { if (maximumCacheSize != null) maximumCacheSize(maximumCacheSize) }
        .build()

    companion object {
        val Example = CacheConfiguration(
            expireAfterAccess = 15.minutes,
            expireAfterWrite = 10.minutes,
            maximumCacheSize = 500
        )
    }
}
