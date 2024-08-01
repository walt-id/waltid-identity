package id.walt.crypto.keys.tse

import io.github.reactivecircus.cache4k.Cache
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class TSEAuth(
    // Root token
    val accessKey: String? = null,

    // AppRole
    val roleId: String? = null,
    val secretId: String? = null,

    // UserPass
    val userpassPath: String = "userpass",
    val username: String? = null,
    val password: String? = null,
) {

    companion object {
        private val http = HttpClient {
            install(ContentNegotiation) {
                json()
            }
            defaultRequest {
                header(HttpHeaders.ContentType, ContentType.Application.Json)
            }
            install(Logging) {
                logger = Logger.DEFAULT
                level = LogLevel.ALL
            }
        }

        private val tokens = Cache.Builder<Pair<TSEAuth, String>, String>()
            .expireAfterWrite(24.hours)
            .build()
    }

    init {
        require(
            accessKey != null
                    || (roleId != null && secretId != null)
                    || (username != null && password != null)
        ) {
            "No valid authentication method passed!"
        }
    }

    private fun String.getServerUpTov1() = substringBefore("/v1/") + "/v1"

    private suspend fun HttpResponse.getClientToken() =
        body<JsonObject>().let {
            if (it.containsKey("errors")) {
                error("Errors occurred at TSE login: " + it["errors"]!!.jsonArray.map { it.jsonPrimitive.content }.joinToString())
            }
            it["auth"]?.jsonObject?.get("client_token")?.jsonPrimitive?.contentOrNull
                ?: error("Did not receive token after login!")
        }

    private suspend fun loginAppRole(server: String): String =
        http.post("$server/auth/approle/login") {
            setBody(
                mapOf(
                    "role_id" to roleId,
                    "secret_id" to secretId
                )
            )
        }.getClientToken()


    private suspend fun loginUserPass(server: String): String =
        http.post("$server/auth/$userpassPath/login/$username") {
            setBody(mapOf("password" to password))
        }.getClientToken()

    /**
     * @param server: The server url up to v1, e.g. http://127.0.0.1:8200/v1
     */
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getLoginToken(server: String): String {
        return when {
            accessKey != null -> accessKey
            roleId != null -> loginAppRole(server)
            username != null -> loginUserPass(server)
            else -> throw IllegalArgumentException("No valid authentication method passed!")
        }
    }

    /**
     * @param server: The server url (may include object further of v1), e.g. http://127.0.0.1:8200/v1/transit
     */
    @Suppress("NAME_SHADOWING")
    @JvmBlocking
    @JvmAsync
    @JsPromise
    @JsExport.Ignore
    suspend fun getCachedLogin(server: String): String {
        val server = server.getServerUpTov1()
        return tokens.get(Pair(this, server)) {
            getLoginToken(server)
        }
    }
}
