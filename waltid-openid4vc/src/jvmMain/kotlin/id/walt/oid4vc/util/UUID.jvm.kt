package id.walt.oid4vc.util

import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import java.security.MessageDigest
import java.util.UUID

actual fun randomUUID(): String {
    return UUID.randomUUID().toString()
}

actual fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

private val ktorClient = HttpClient(Java) {
}
actual suspend fun httpGet(url: String): String {
    return ktorClient.get(url).bodyAsText()
}

