package id.walt.trust.fetcher

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

actual object SourceFetcher {

    actual var securityConfig: FetcherSecurityConfig = FetcherSecurityConfig()

    actual suspend fun fetch(url: String, config: FetcherSecurityConfig): FetchResult {
        val (validatedUrl, validationError) = SourceFetcherCommon.validateUrlBasic(url, config)
        if (validatedUrl == null) {
            return FetchResult(success = false, error = "URL validation failed: $validationError")
        }

        return try {
            HttpClient().use { client ->
                val response = client.get(validatedUrl.parsedUrl)
                if (response.status.isSuccess()) {
                    FetchResult(
                        success = true,
                        content = response.bodyAsText(),
                        contentType = response.contentType()?.toString(),
                        statusCode = response.status.value
                    )
                } else {
                    FetchResult(
                        success = false,
                        statusCode = response.status.value,
                        error = "HTTP ${response.status.value}: ${response.status.description}"
                    )
                }
            }
        } catch (e: Exception) {
            FetchResult(success = false, error = e.message ?: "Unknown error")
        }
    }

    actual fun detectFormat(contentType: String?, content: String): SourceFormat {
        return SourceFetcherCommon.detectSourceFormat(contentType, content)
    }
}
