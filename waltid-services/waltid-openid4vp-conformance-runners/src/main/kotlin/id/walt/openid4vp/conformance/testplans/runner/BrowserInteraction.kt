package id.walt.openid4vp.conformance.testplans.runner

import com.microsoft.playwright.Page
import id.walt.openid4vp.conformance.testplans.httpdata.TestRunResult
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A front-channel interaction exposed by the conformance suite. */
internal data class BrowserInteraction(
    val url: String,
    val method: String,
)

internal fun TestRunResult.browserInteractionsForAutomation(): List<BrowserInteraction> =
    pendingBrowserInteractions().ifEmpty {
        browser.visitedUrlsWithMethod.mapNotNull { it?.browserUrlWithMethod() } +
            browser.visited.mapNotNull { it?.browserUrl() }
    }

internal fun TestRunResult.pendingBrowserInteractions(): List<BrowserInteraction> =
    browser.urlsWithMethod.mapNotNull { it?.browserUrlWithMethod() }
        .ifEmpty { browser.urls.mapNotNull { it?.browserUrl() } }

internal fun TestRunResult.browserInteractionSummary(): String =
    "urlsWithMethod=${browser.urlsWithMethod.map { it.shortJson() }}, " +
        "urls=${browser.urls.map { it.shortJson() }}, " +
        "visitedUrlsWithMethod=${browser.visitedUrlsWithMethod.map { it.shortJson() }}, " +
        "visited=${browser.visited.map { it.shortJson() }}"

internal fun openBrowserInteraction(page: Page, interaction: BrowserInteraction) {
    if (!interaction.method.equals("POST", ignoreCase = true)) {
        page.navigate(interaction.url)
        return
    }

    val uri = URI.create(interaction.url)
    val action = "${uri.scheme}://${uri.rawAuthority}${uri.rawPath.orEmpty()}"
    val inputs = parseBrowserFormParameters(uri.rawQuery)
        .joinToString("\n") { (name, value) ->
            "<input type=\"hidden\" name=\"${name.htmlEscape()}\" value=\"${value.htmlEscape()}\">"
        }

    page.setContent(
        """
        <!doctype html>
        <html><body>
          <form method="post" action="${action.htmlEscape()}">$inputs</form>
          <script>document.forms[0].submit();</script>
        </body></html>
        """.trimIndent()
    )
}

internal fun parseBrowserFormParameters(rawQuery: String?): List<Pair<String, String>> =
    rawQuery
        ?.takeIf { it.isNotBlank() }
        ?.split("&")
        ?.filter { it.isNotBlank() }
        ?.map { part ->
            val separator = part.indexOf('=')
            val name = if (separator == -1) part else part.substring(0, separator)
            val value = if (separator == -1) "" else part.substring(separator + 1)
            URLDecoder.decode(name, StandardCharsets.UTF_8) to
                URLDecoder.decode(value, StandardCharsets.UTF_8)
        }
        ?: emptyList()

private fun JsonElement.browserUrlWithMethod(): BrowserInteraction? {
    val obj = this as? JsonObject ?: return browserUrl()
    val url = obj.stringValue("url")
        ?: obj.stringValue("originalUrl")
        ?: obj.stringValue("fullUrl")
        ?: return null
    return BrowserInteraction(url = url, method = (obj.stringValue("method") ?: "GET").uppercase())
}

private fun JsonElement.browserUrl(): BrowserInteraction? = runCatching {
    jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }?.let { BrowserInteraction(it, "GET") }
}.getOrNull()

private fun JsonObject.stringValue(name: String): String? =
    get(name)?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.takeIf { it.isNotBlank() }

private fun JsonElement?.shortJson(): String =
    this?.toString()?.let { if (it.length > 500) it.take(500) + "..." else it } ?: "null"

private fun String.htmlEscape(): String = buildString {
    this@htmlEscape.forEach { char ->
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '\"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}
