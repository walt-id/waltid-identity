package id.walt.walletdemo.compose.logic.walletapi2

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class WalletApi2AuthClient(
    private val baseUrl: String,
    private val kind: WalletApiKind = WalletApiKind.OpenSource,
    private val http: HttpClient = WalletApi2Client.defaultHttpClient(baseUrl),
) {
    suspend fun register(email: String, password: String) {
        require(kind.canRegister) { "This API does not support registration" }
        val response = http.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(EmailPasswordRequest(email, password))
        }
        if (response.status != HttpStatusCode.Created && !response.status.isSuccess()) {
            throw WalletApi2Exception(response.status, authErrorMessage(response.status, response.bodyAsText(), registering = true))
        }
    }

    suspend fun login(email: String, password: String): String {
        val response = http.post(kind.loginPath) {
            contentType(ContentType.Application.Json)
            setBody(EmailPasswordRequest(email, password))
        }
        if (!response.status.isSuccess()) {
            throw WalletApi2Exception(response.status, authErrorMessage(response.status, response.bodyAsText(), registering = false))
        }
        return response.body<AuthSessionResponse>().token
            ?: throw WalletApi2Exception(response.status, "Login succeeded without a token")
    }

    suspend fun logout(token: String) {
        val client = WalletApi2Client.authenticatedHttpClient(baseUrl, token)
        try {
            client.post(kind.logoutPath)
        } finally {
            client.close()
        }
    }
}

data class WalletApi2Session(
    val kind: WalletApiKind,
    val baseUrl: String,
    val token: String,
    val walletId: String,
    val email: String,
    val walletTargets: List<String> = emptyList(),
)

/** Account errors explain recovery; exception envelopes and HTML do not belong in the form. */
internal fun authErrorMessage(status: HttpStatusCode, body: String, registering: Boolean): String {
    if (status == HttpStatusCode.Unauthorized) return "Invalid email or password."
    if (status == HttpStatusCode.Conflict && registering) return "An account with this email already exists. Sign in instead."
    if (status == HttpStatusCode.TooManyRequests) return "Too many attempts. Try again later."
    if (status.value in 400..499) {
        val parsed = runCatching { walletApi2Json.parseToJsonElement(body) }.getOrNull()
        // Older deployments wrap their JSON error envelope in a JSON string.
        val envelope = if (parsed is JsonPrimitive && parsed.isString)
            runCatching { walletApi2Json.parseToJsonElement(parsed.content) }.getOrNull() else parsed
        val message = ((envelope as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
        message?.trim()?.takeIf { it.isNotEmpty() && it.length <= 300 }?.let { return it }
    }
    return if (registering) "Unable to create your account. Try again." else "Unable to sign in. Try again."
}
