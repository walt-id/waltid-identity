package id.walt.trust.decision

import id.walt.trust.model.TrustDecision
import id.walt.trust.model.TrustDecisionCode
import id.walt.trust.model.TrustedEntityType
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.walt.webdatafetching.utils.UrlUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Asks a remote trust registry over HTTP: one `POST` per expected entity type to a
 * certificate-chain resolve endpoint, combined with [anyOfEntityTypes].
 *
 * Works with the Enterprise route (`{base}/v1/{target}/trust-registry-api/resolve/certificate-chain`,
 * which needs a bearer token with resolve permission, passed through [headers]) and with any resolver
 * serving the same contract, such as the bring-your-own resolver path that `ETSITrustListPolicy` calls
 * (`{base}/trust-registry/resolve/certificate-chain`). No OSS service serves that path.
 *
 * Requests go through [WebDataFetcher] id [WebDataFetcherId.TRUST_DECISION_RESOLVE], so the
 * private-network guard applies: a resolver on a loopback or private address must be allowed through
 * `PrivateNetworkGuardSettings`.
 *
 * Failures never throw: an unreachable resolver, a timeout or a non-2xx answer is
 * [TrustDecisionCode.UNKNOWN]; an answer that is not a trust decision is [TrustDecisionCode.PROCESSING_ERROR].
 *
 * @param headers extra request headers, read before every request (for example a fresh bearer token).
 */
class HttpTrustDecisionEvaluator internal constructor(
    resolveCertificateChainUrl: String,
    private val headers: suspend () -> Map<String, String>,
    private val fetcher: WebDataFetcher,
) : TrustDecisionEvaluator {

    constructor(
        resolveCertificateChainUrl: String,
        headers: suspend () -> Map<String, String> = { emptyMap() },
    ) : this(resolveCertificateChainUrl, headers, defaultFetcher)

    private val url: Url = UrlUtils.parseUrl(resolveCertificateChainUrl)

    override suspend fun evaluate(request: TrustDecisionRequest): TrustDecision =
        when (val subject = request.subject) {
            is TrustSubject.CertificateChain -> request.anyOfEntityTypes { type ->
                resolve(ResolveCertificateChainBody(subject.pemOrDer, request.at.toString(), type, request.expectedServiceType))
            }
        }

    private suspend fun resolve(body: ResolveCertificateChainBody): TrustDecision {
        val response = try {
            val extraHeaders = headers()
            fetcher.rawFetch(url) {
                method = HttpMethod.Post
                accept(ContentType.Application.Json)
                extraHeaders.forEach { (name, value) -> header(name, value) }
                setBody(TextContent(json.encodeToString(body), ContentType.Application.Json))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.warn { "Trust resolver $url is unavailable: ${e::class.simpleName}: ${e.message}" }
            return unknown("Trust resolver is unavailable")
        }

        if (!response.status.isSuccess()) {
            log.warn { "Trust resolver $url answered HTTP ${response.status.value}" }
            return unknown("Trust resolver answered HTTP ${response.status.value}")
        }

        val text = try {
            response.bodyAsText()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return unknown("Trust resolver response could not be read")
        }
        return runCatching { json.decodeFromString<TrustDecision>(text) }.getOrElse {
            log.warn { "Trust resolver $url answered with something that is not a trust decision" }
            TrustDecision(TrustDecisionCode.PROCESSING_ERROR, warnings = listOf("Trust resolver response is not a trust decision"))
        }
    }

    private fun unknown(warning: String) = TrustDecision(TrustDecisionCode.UNKNOWN, warnings = listOf(warning))

    /** Request body of the `resolve/certificate-chain` contract. */
    @Serializable
    private data class ResolveCertificateChainBody(
        val certificateChainPemOrDer: List<String>,
        val instant: String,
        val expectedEntityType: TrustedEntityType? = null,
        val expectedServiceType: String? = null,
    )

    private companion object {
        val log = KotlinLogging.logger { }

        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }

        val defaultFetcher by lazy { WebDataFetcher(WebDataFetcherId.TRUST_DECISION_RESOLVE) }
    }
}
