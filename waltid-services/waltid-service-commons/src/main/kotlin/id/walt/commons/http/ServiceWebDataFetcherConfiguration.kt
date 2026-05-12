package id.walt.commons.http

import id.walt.webdatafetching.WebDataFetchingConfiguration
import kotlinx.serialization.Serializable

@Serializable
data class ServiceWebDataFetcherConfiguration(
    val globalDefault: WebDataFetchingConfiguration? = null,

    val service: Map<String, WebDataFetchingConfiguration>? = null
)
