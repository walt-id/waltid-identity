package id.walt.walletdemo.compose.logic

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.*
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.io.readByteArray
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64

/** Routing never accepts an offer, sends credentials or authenticates a request. The SDK does that. */
data class ResolvedWalletLink(val url: String, val kind: WalletLinkKind)

data class WalletLinkDocument(val status: Int, val body: String = "", val location: String? = null)

class WalletLinkException(message: String) : Exception(message)

/** Plain web links get one bounded, unauthenticated GET before opening the existing protocol review. */
suspend fun resolveWalletLink(
    input: String,
    fetch: suspend (String) -> WalletLinkDocument = ::fetchWalletLinkDocument,
): ResolvedWalletLink = try {
    withTimeoutOrNull(10_000) {
        var url = input.trim()
        val visited = mutableSetOf<String>()
        repeat(6) {
            when (val kind = WalletLinkKind.classify(url)) {
                WalletLinkKind.Offer, WalletLinkKind.Presentation, WalletLinkKind.AuthorizationCallback ->
                    return@withTimeoutOrNull ResolvedWalletLink(url, kind)
                WalletLinkKind.Web -> Unit
                else -> throw WalletLinkException(unsupportedLinkMessage)
            }
            if (!visited.add(url)) throw WalletLinkException("This link keeps redirecting. Ask for a fresh QR code.")
            val parsed = Url(url)
            if (parsed.user != null || parsed.password != null) throw WalletLinkException(unsupportedLinkMessage)
            val document = fetch(url)
            if (document.status in setOf(301, 302, 303, 307, 308)) {
                val location = document.location ?: throw WalletLinkException(unsupportedLinkMessage)
                val target = URLBuilder(parsed).takeFrom(location).buildString()
                if (parsed.protocol == URLProtocol.HTTPS && Url(target).protocol == URLProtocol.HTTP) {
                    throw WalletLinkException("This link redirects to an insecure page. Ask for a new link.")
                }
                url = target
            } else {
                if (document.status !in 200..299) throw WalletLinkException(unavailableLinkMessage)
                return@withTimeoutOrNull routeWalletLinkDocument(url, document.body)
            }
        }
        throw WalletLinkException("This link has too many redirects. Ask for a fresh QR code.")
    } ?: throw WalletLinkException("This link took too long to open. Check your connection and try again.")
} catch (error: CancellationException) {
    throw error
} catch (error: WalletLinkException) {
    throw error
} catch (_: Exception) {
    throw WalletLinkException(unavailableLinkMessage)
}

/** Inspect shape only; supplied identity and JWT claims are never treated as verified metadata. */
fun routeWalletLinkDocument(url: String, body: String): ResolvedWalletLink {
    if (body.encodeToByteArray().size > maxWalletLinkBytes) throw WalletLinkException(unsupportedLinkMessage)
    val value = body.trim()
    val json = runCatching { Json.parseToJsonElement(value) as? JsonObject }.getOrNull()
    val parts = value.split('.')
    val jwt = if (json == null && parts.size == 3) runCatching {
        val payload = parts[1].padEnd(parts[1].length + (4 - parts[1].length % 4) % 4, '=')
        Json.parseToJsonElement(Base64.UrlSafe.decode(payload).decodeToString()) as? JsonObject
    }.getOrNull() else null
    val objectValue = json ?: jwt ?: throw WalletLinkException(unsupportedLinkMessage)
    val offer = (objectValue["credential_issuer"] as? JsonPrimitive)?.isString == true &&
        (objectValue["credential_configuration_ids"] is JsonArray || objectValue["credentials"] is JsonArray)
    val clientId = (objectValue["client_id"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.takeIf { it.isNotBlank() }
    val presentation = clientId != null && (objectValue.containsKey("dcql_query") ||
        objectValue.containsKey("presentation_definition") ||
        (objectValue["response_type"] as? JsonPrimitive)?.contentOrNull?.split(' ')?.contains("vp_token") == true)
    if (offer == presentation) throw WalletLinkException(unsupportedLinkMessage)
    if (offer && json != null) return ResolvedWalletLink(
        "openid-credential-offer://?credential_offer=${value.encodeURLParameter()}", WalletLinkKind.Offer)
    if (presentation) {
        // Preserve the signed bytes. Unsigned JSON remains a request_uri so the SDK's normal
        // request-object policy, content-type handling and warning still apply.
        val reference = if (jwt != null) "request=${value.encodeURLParameter()}" else "request_uri=${url.encodeURLParameter()}"
        return ResolvedWalletLink("openid4vp://?client_id=${clientId.encodeURLParameter()}&$reference", WalletLinkKind.Presentation)
    }
    throw WalletLinkException(unsupportedLinkMessage)
}

private suspend fun fetchWalletLinkDocument(url: String): WalletLinkDocument {
    val client = HttpClient { followRedirects = false; expectSuccess = false }
    try {
        return client.prepareGet(url) {
            headers.append(HttpHeaders.Accept, "application/json, application/oauth-authz-req+jwt")
        }.execute { response ->
            if (response.status.value in 300..399) return@execute WalletLinkDocument(response.status.value,
                location = response.headers[HttpHeaders.Location])
            if ((response.contentLength() ?: 0) > maxWalletLinkBytes) throw WalletLinkException(unsupportedLinkMessage)
            val bytes = response.bodyAsChannel().readRemaining(maxWalletLinkBytes + 1L).readByteArray()
            if (bytes.size > maxWalletLinkBytes) throw WalletLinkException(unsupportedLinkMessage)
            WalletLinkDocument(response.status.value, bytes.decodeToString(throwOnInvalidSequence = true))
        }
    } finally { client.close() }
}

private const val maxWalletLinkBytes = 262_144
private const val unsupportedLinkMessage = "This page does not contain a credential offer or sharing request. Open the issuer or verifier page and scan its QR code."
private const val unavailableLinkMessage = "Could not open this link. Check your connection or ask for a fresh QR code, then try again."
