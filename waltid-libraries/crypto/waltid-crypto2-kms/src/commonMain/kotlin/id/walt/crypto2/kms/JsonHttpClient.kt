package id.walt.crypto2.kms

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.coroutines.CancellationException

internal class JsonHttpResponse(val status: Int, val body: JsonObject?)

internal suspend fun HttpClient.executeJson(
    provider: String,
    endpoint: String,
    method: HttpMethod,
    headers: Map<String, String> = emptyMap(),
    contentType: ContentType? = null,
    body: String? = null,
): JsonObject? = executeJsonResponse(provider, endpoint, method, headers, contentType, body).body

/** Like [executeJson], but also returns the success status, for APIs that give 200 and 202 different meanings. */
internal suspend fun HttpClient.executeJsonResponse(
    provider: String,
    endpoint: String,
    method: HttpMethod,
    headers: Map<String, String> = emptyMap(),
    contentType: ContentType? = null,
    body: String? = null,
): JsonHttpResponse {
    val response = try {
        request(endpoint) {
            this.method = method
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            headers.forEach { (name, value) -> header(name, value) }
            body?.let {
                contentType?.let(::contentType)
                setBody(it)
            }
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        throw KmsProviderException("$provider request failed", cause)
    }
    if (!response.status.isSuccess()) {
        throw KmsProviderException("$provider request failed with HTTP ${response.status.value}", null, response.status.value)
    }
    val responseBody = response.body<String>()
    if (responseBody.isBlank()) return JsonHttpResponse(response.status.value, null)
    val json = try {
        Json.parseToJsonElement(responseBody).jsonObject
    } catch (cause: Exception) {
        throw KmsProviderException("$provider returned an invalid JSON response", cause)
    }
    return JsonHttpResponse(response.status.value, json)
}
