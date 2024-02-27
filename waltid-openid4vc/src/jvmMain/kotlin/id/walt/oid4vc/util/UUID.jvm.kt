package id.walt.oid4vc.util

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.security.MessageDigest

actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

private val ktorClient = HttpClient(Java) {
}
actual suspend fun httpGet(url: String): String {
    return ktorClient.get(url).bodyAsText()
}

