package id.walt.trust.fetcher

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

/**
 * JS implementation of [SourceFetcher] using the default Ktor JS engine.
 *
 * Note: SSRF protection (IP blocking, DNS pinning) is not implemented on JS, as the platform's
 * security model (same-origin policy in browsers, OS-level controls in Node.js) provides
 * equivalent protections in typical deployment scenarios.
 * For server-side JS environments where SSRF is a concern, use the remote service mode
 * of [id.walt.policies2.vc.policies.ETSITrustListPolicy] with an explicitly trusted URL instead.
 */
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
