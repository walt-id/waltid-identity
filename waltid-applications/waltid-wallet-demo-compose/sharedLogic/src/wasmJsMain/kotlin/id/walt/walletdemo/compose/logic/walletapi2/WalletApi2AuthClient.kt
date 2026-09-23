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

internal class WalletApi2AuthClient(
    private val baseUrl: String,
    private val http: HttpClient = WalletApi2Client.defaultHttpClient(baseUrl),
) {
    suspend fun register(email: String, password: String) {
        val response = http.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(EmailPasswordRequest(email, password))
        }
        if (response.status != HttpStatusCode.Created && !response.status.isSuccess()) {
            val details = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            throw WalletApi2Exception(response.status, details.ifBlank { "Registration failed" })
        }
    }

    suspend fun login(email: String, password: String): String {
        val response = http.post("/auth/emailpass") {
            contentType(ContentType.Application.Json)
            setBody(EmailPasswordRequest(email, password))
        }
        if (!response.status.isSuccess()) {
            val details = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
            throw WalletApi2Exception(response.status, details.ifBlank { "Login failed" })
        }
        return response.body<AuthSessionResponse>().token
            ?: throw WalletApi2Exception(response.status, "Login succeeded without a token")
    }

    suspend fun logout(token: String) {
        val client = WalletApi2Client.authenticatedHttpClient(baseUrl, token)
        try {
            client.post("/auth/logout")
        } finally {
            client.close()
        }
    }
}

class WalletApi2Session(
    val baseUrl: String,
    val token: String,
    val walletId: String,
    val email: String,
)
