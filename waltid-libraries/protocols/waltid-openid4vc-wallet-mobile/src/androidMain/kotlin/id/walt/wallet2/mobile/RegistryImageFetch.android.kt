package id.walt.wallet2.mobile

import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess

internal actual suspend fun fetchRegistryIconBytes(url: String): ByteArray? {
    if (!isHttpsUrl(url)) return null
    return runCatching {
        RegistryImageHttp.client.get(url).let { response ->
            if (!response.status.isSuccess()) return@runCatching null
            response.bodyAsBytes()
        }
    }.getOrNull()
}

private object RegistryImageHttp {
    val client: HttpClient = HttpClient(Android)
}
