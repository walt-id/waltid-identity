package id.walt.webdatafetching

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.observer.wrapWithContent
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.http.contentLength
import io.ktor.util.pipeline.PipelinePhase
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.cancel
import io.ktor.utils.io.close
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.exhausted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Thrown when a response declares or delivers more bytes than the configured cap. */
class ResponseBodyTooLargeException(
    val maxBytes: Long,
    contentLength: Long? = null,
) : IllegalStateException(
    if (contentLength != null) {
        "Response of $contentLength bytes exceeds the $maxBytes byte limit"
    } else {
        "Response exceeds the $maxBytes byte limit"
    },
)

class ResponseSizeLimitConfig {
    var maxBytes: Long = WebDataFetchingConfiguration.DEFAULT_MAX_RESPONSE_BYTES
}

/**
 * Rejects an oversized body before Ktor's SaveBody plugin stores it.
 * A declared Content-Length is refused immediately. A chunked body is capped while it is copied,
 * and the remainder is cancelled.
 *
 * `rawContent`, `wrapWithContent`, `copyTo`, `exhausted`, and `cancel` are Ktor 3.6 `@InternalAPI`.
 * Recheck this plugin when the Ktor version in `gradle/libs.versions.toml` changes.
 */
@OptIn(InternalAPI::class)
val ResponseSizeLimit = createClientPlugin("ResponseSizeLimit", ::ResponseSizeLimitConfig) {
    val maxBytes = pluginConfig.maxBytes
    if (maxBytes <= 0) return@createClientPlugin

    val phase = PipelinePhase("ResponseSizeLimit")
    val httpClient = client
    client.receivePipeline.insertPhaseBefore(HttpReceivePipeline.Before, phase)
    client.receivePipeline.intercept(phase) { response ->
        val declared = response.contentLength()
        if (declared != null && declared > maxBytes) {
            val error = ResponseBodyTooLargeException(maxBytes, declared)
            response.rawContent.cancel(error)
            throw error
        }
        val limited = ByteChannel()
        val source = response.rawContent
        httpClient.launch {
            try {
                val copied = source.copyTo(limited, maxBytes)
                if (copied == maxBytes && !source.exhausted()) {
                    val error = ResponseBodyTooLargeException(maxBytes)
                    source.cancel(error)
                    limited.close(error)
                } else {
                    limited.close()
                }
            } catch (error: Throwable) {
                source.cancel(error)
                limited.cancel(error)
                if (error is CancellationException) throw error
            }
        }
        proceedWith(response.call.wrapWithContent(limited).response)
    }
}
