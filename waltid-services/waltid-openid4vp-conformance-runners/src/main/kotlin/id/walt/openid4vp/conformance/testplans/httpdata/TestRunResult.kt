package id.walt.openid4vp.conformance.testplans.httpdata


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
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
    fun getExposedAuthorizationEndpoint() = exposed["authorization_endpoint"]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("Missing authorization_endpoint in TestRunResult")

    @Serializable
    data class Browser(
        val browserApiRequests: List<JsonElement?> = listOf(),
        val runners: List<JsonElement?> = listOf(),
        @SerialName("show_qr_code")
        val showQrCode: Boolean, // false
        val urls: List<JsonElement?> = listOf(),
        val urlsWithMethod: List<JsonElement?> = listOf(),
        val visited: List<JsonElement?> = listOf(),
        val visitedUrlsWithMethod: List<JsonElement?> = listOf()
    )

    @Serializable
    data class Owner(
        val iss: String, // https://developer.com
        val sub: String // developer
    )
}
