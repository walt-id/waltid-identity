package id.walt.openid4vp.conformance.testplans.http

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.IOException

/** Only for reading suite state: even GET credential-offer delivery has side effects. */
internal suspend fun HttpClient.getConformanceState(path: String, operation: String): HttpResponse {
    for (attempt in 1..3) {
        val failure = try {
            val response = get(path) {
                expectSuccess = false
                header(HttpHeaders.CacheControl, "no-cache")
            }
            if (response.status.isSuccess()) return response
            val failure = ConformanceStateRequestException(operation, "HTTP ${response.status.value}")
            if (response.status.value !in setOf(429, 502, 503, 504)) throw failure
            failure
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpRequestTimeoutException) {
            ConformanceStateRequestException(operation, "request timeout", e)
        } catch (e: IOException) {
            ConformanceStateRequestException(operation, "transport failure", e)
        }
        if (attempt == 3) throw failure
        delay(attempt * 1_000L)
    }
    error("Unreachable")
}

/** The message contains only runner-controlled labels and numeric HTTP status codes. */
internal class ConformanceStateRequestException(operation: String, reason: String, cause: Throwable? = null) :
    IllegalStateException("$operation: $reason", cause)

/** Never publish exception messages, response bodies, or URLs containing test credentials. */
internal fun Throwable.safeConformanceFailure(): String = when (this) {
    is ConformanceStateRequestException -> message!!
    is ResponseException -> "${this::class.simpleName}: HTTP ${response.status.value}"
    else -> this::class.simpleName ?: "Unknown failure"
}
