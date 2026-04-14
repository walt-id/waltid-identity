package id.walt.webdatafetching

import kotlinx.serialization.Serializable

@Serializable
data class WebDataFetchingResult<Res : Any>(
    val body: Res,
    val success: Boolean,
    val status: Int,

    val cached: Boolean = false
)
