package id.walt.webdatafetching.config

import id.walt.webdatafetching.config.http.HttpMethod
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RequestConfiguration(
    val method: HttpMethod = HttpMethod.Get,

    val headers: Map<String, String>? = null,
    val cookies: Map<String, String>? = null,
    val auth: HttpAuthConfiguration? = null,

    val userAgent: UserAgentConfiguration? = null,

    val expectSuccess: Boolean = true,
) {

    fun applyConfiguration(builder: HttpRequestBuilder) {
        builder.method = method.ktorHttpMethod
        builder.expectSuccess = expectSuccess

        /*headers?.let { // if below API only appends single
            headers { it.forEach { (key, value) -> append(key, value) } }
        }*/
        headers?.forEach { (key, value) -> builder.header(key, value) }
        cookies?.forEach { (key, value) -> builder.cookie(key, value) }

        auth?.let {
            when (it) {
                is HttpAuthConfiguration.BasicAuth -> builder.basicAuth(it.username, it.password)
                is HttpAuthConfiguration.BearerAuth -> builder.bearerAuth(it.token)
            }
        }
    }

    @Serializable
    sealed class HttpAuthConfiguration {
        @Serializable
        @SerialName("basic")
        data class BasicAuth(val username: String, val password: String) : HttpAuthConfiguration()

        @Serializable
        @SerialName("bearer")
        data class BearerAuth(val token: String) : HttpAuthConfiguration()
    }

    @Serializable
    sealed class UserAgentConfiguration {
        @Serializable
        @SerialName("custom")
        data class CustomUserAgent(val agent: String) : UserAgentConfiguration()

        @Serializable
        @SerialName("curl")
        class CurlUserAgent : UserAgentConfiguration()

        @Serializable
        @SerialName("browser")
        class BrowserUserAgent : UserAgentConfiguration()
    }

    companion object {
        val Example = RequestConfiguration(
            method = HttpMethod.Get,
            headers = mapOf("X-Requested-By" to "walt.id"),
            cookies = mapOf("my_login" to "123456"),
            auth = HttpAuthConfiguration.BearerAuth("my-token"),
            userAgent = UserAgentConfiguration.CustomUserAgent("custom user agent"),
            expectSuccess = false
        )
    }
}
