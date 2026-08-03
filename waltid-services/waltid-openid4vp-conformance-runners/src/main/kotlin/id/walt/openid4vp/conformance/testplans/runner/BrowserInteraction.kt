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
