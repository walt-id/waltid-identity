package id.walt.crypto2.kms

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
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
            // The status is checked below. Ktor's default validation would throw first, without the status, and with
            // the response body in the exception message.
            expectSuccess = false
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
            headers.forEach { (name, value) -> header(name, value) }
            body?.let {
                contentType?.let(::contentType)
                setBody(it)
            }
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: ResponseException) {
        // A custom response validator of the injected client still runs: keep its status, not its body.
        throw httpStatusFailure(provider, cause.response.status.value)
    } catch (cause: Exception) {
        throw KmsProviderException("$provider request failed", cause)
    }
    if (!response.status.isSuccess()) throw httpStatusFailure(provider, response.status.value)
    val responseBody = response.body<String>()
    if (responseBody.isBlank()) return JsonHttpResponse(response.status.value, null)
    val json = try {
        Json.parseToJsonElement(responseBody).jsonObject
    } catch (cause: Exception) {
        throw KmsProviderException("$provider returned an invalid JSON response", cause)
    }
    return JsonHttpResponse(response.status.value, json)
}

/** Response bodies can echo credentials, so the failure carries only the status. */
private fun httpStatusFailure(provider: String, status: Int) =
    KmsProviderException("$provider request failed with HTTP $status", null, status)
