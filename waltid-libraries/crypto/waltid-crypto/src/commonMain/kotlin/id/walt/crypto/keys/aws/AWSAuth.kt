package id.walt.crypto.keys.aws

import io.github.reactivecircus.cache4k.Cache
import io.github.reactivecircus.cache4k.Cache.Builder.Companion.invoke
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.Duration.Companion.hours


@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable
data class AWSAuth(
    val accessKeyId: String? = null,
    val secretAccessKey: String? = null,
    val region: String? = null,
    val roleName: String? = null,
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

        private val tokens = Cache.Builder<Pair<AWSAuth, String>, String>()
            .expireAfterWrite(24.hours)
            .build()
    }

    init {
        requireAuthenticationMethod()
    }

    private fun requireAuthenticationMethod() {
        val usingAccessKey = accessKeyId != null && secretAccessKey != null && region != null
        val instanceAuth = roleName != null
        if (!usingAccessKey && !instanceAuth) {
            throw IllegalArgumentException("AWSAuth requires either accessKeyId, secretAccessKey, and region or roleName")
        }

    }


}