package id.walt.oid4vc.interfaces

import io.ktor.http.*
import kotlinx.serialization.json.JsonObject

data class SimpleHttpResponse(
    val status: HttpStatusCode,
    val headers: Headers,
    val body: String?
)

interface IHttpClient {
    fun httpGet(url: Url, headers: Headers? = null): SimpleHttpResponse
    fun httpPostObject(url: Url, jsonObject: JsonObject, headers: Headers? = null): SimpleHttpResponse
    fun httpSubmitForm(url: Url, formParameters: Parameters, headers: Headers? = null): SimpleHttpResponse
}
