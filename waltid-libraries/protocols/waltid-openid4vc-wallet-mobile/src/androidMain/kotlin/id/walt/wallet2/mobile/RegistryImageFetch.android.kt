package id.walt.wallet2.mobile

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

internal suspend fun fetchRegistryIconBytes(url: String): ByteArray? {
    if (!isHttpsUrl(url)) return null
    return runCatching {
        RegistryImageHttp.client.prepareGet(url).execute { response ->
            if (!response.status.isSuccess()) return@execute null
            val contentLength = response.contentLength()
            if (contentLength != null && contentLength > MaxRegistryIconBytes) return@execute null
            val bytes = response.bodyAsChannel().readRemaining(MaxRegistryIconBytes + 1L).readByteArray()
            if (bytes.size > MaxRegistryIconBytes) return@execute null
            bytes
        }
    }.getOrNull()
}

private object RegistryImageHttp {
    val client: HttpClient = HttpClient(Android) {
        install(HttpTimeout) {
            requestTimeoutMillis = RegistryIconFetchTimeoutMs
            connectTimeoutMillis = RegistryIconFetchTimeoutMs
            socketTimeoutMillis = RegistryIconFetchTimeoutMs
        }
    }
}
