package id.walt.crypto.keys.aws

import io.github.reactivecircus.cache4k.Cache
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
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
    val roleArn: String? = null,
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
        val instanceAuth = roleName != null && region != null
        val kubernetesAuth = roleArn != null
        if (!usingAccessKey && !instanceAuth && !kubernetesAuth) {
            throw IllegalArgumentException("AWSAuth requires either accessKeyId, secretAccessKey, and region or roleName")
        }

    }


}