package id.walt.webdatafetching.config

import kotlinx.serialization.Serializable

@Serializable
data class RequestConfiguration(
    val headers: Map<String, String>?,
    val expectSuccess: Boolean,
)
