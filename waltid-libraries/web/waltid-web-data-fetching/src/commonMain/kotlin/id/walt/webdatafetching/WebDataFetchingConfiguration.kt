package id.walt.webdatafetching

import id.walt.webdatafetching.config.CacheConfiguration
import id.walt.webdatafetching.config.RequestConfiguration
import id.walt.webdatafetching.config.url.UrlConfiguration
import kotlinx.serialization.Serializable

@Serializable
data class WebDataFetchingConfiguration(
    val url: UrlConfiguration? = null,
    val cache: CacheConfiguration? = null,
    val request: RequestConfiguration? = null,

    //val defaultOnError: JsonElement?,
) {
    companion object {
        val Default = WebDataFetchingConfiguration()
    }

}

