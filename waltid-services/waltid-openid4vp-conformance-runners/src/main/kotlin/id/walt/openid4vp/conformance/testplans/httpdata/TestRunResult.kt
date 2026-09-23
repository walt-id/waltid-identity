package id.walt.openid4vp.conformance.testplans.httpdata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Instant


@Serializable
data class TestRunResult(
    val id: String, // 6WMEqdwfeer04vr
    val name: String, // oid4vp-1final-verifier-happy-flow

    val created: Instant, // 2025-10-14T02:29:36.320126648Z
    val updated: Instant, // 2025-10-14T02:29:37.801881891Z

    val owner: Owner,

    val browser: Browser,
    val exposed: JsonObject,

    val error: JsonElement? = null, // null
) {
    fun getExposedAuthorizationEndpoint() = exposed["authorization_endpoint"]?.jsonPrimitive?.contentOrNull
        ?: throw IllegalArgumentException("Missing authorization_endpoint in TestRunResult")

    /**
     * URLs the conformance suite wants a browser to visit.
     *
     * In wallet test plans the suite plays the verifier and publishes the authorization request as a
     * redirect aimed at the wallet's `authorization_endpoint`; opening it is what starts the flow.
     * Entries are plain strings in current releases, but are tolerated as objects carrying a `url`
     * field so a format change does not break the runners silently.
     */
    fun getBrowserUrls(): List<String> = browser.urls.mapNotNull { entry ->
        when (entry) {
            is JsonPrimitive -> entry.contentOrNull
            is JsonObject -> entry["url"]?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }

    @Serializable
    data class Browser(
        val browserApiRequests: List<JsonElement?> = listOf(),
        val runners: List<JsonElement?> = listOf(),
        @SerialName("show_qr_code")
        val showQrCode: Boolean, // false
        val urls: List<JsonElement?> = listOf(),
        val urlsWithMethod: List<JsonElement?> = listOf(),
        val visited: List<JsonElement?> = listOf(),
        val visitedUrlsWithMethod: List<JsonElement?> = listOf(),

        /**
         * Manual "paste the openid4vp:// authorization request here" prompts, added by the
         * conformance suite in release-v5.2.2. Automated runners drive the authorization
         * endpoint directly and therefore ignore these, but the field is modelled so the
         * suite's expectations stay visible.
         */
        val uriInputRequests: List<UriInputRequest> = listOf()
    )

    /** A conformance-suite prompt asking a human to submit a request URI to [submitUrl]. */
    @Serializable
    data class UriInputRequest(
        val submitUrl: String,
        val description: String? = null
    )

    @Serializable
    data class Owner(
        val iss: String, // https://developer.com
        val sub: String // developer
    )
}
