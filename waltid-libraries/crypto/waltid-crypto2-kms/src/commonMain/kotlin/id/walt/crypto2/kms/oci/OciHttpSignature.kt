package id.walt.crypto2.kms.oci

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.kms.digest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.fullPath
import io.ktor.http.toHttpDate
import io.ktor.util.date.GMTDate
import kotlin.io.encoding.Base64
import kotlin.time.Instant

internal suspend fun ociHttpSignatureHeaders(
    method: HttpMethod,
    endpoint: String,
    body: String?,
    credential: OciApiKeyCredential,
    instant: Instant,
): Map<String, String> {
    val url = Url(endpoint)
    val date = GMTDate(instant.toEpochMilliseconds()).toHttpDate()
    val host = if (url.port == url.protocol.defaultPort) url.host else "${url.host}:${url.port}"
    val requestTarget = "(request-target): ${method.value.lowercase()} ${url.fullPath}"
    val signingHeaders: List<Pair<String, String>> = if (body == null) {
        listOf(
            "host" to host,
            "(request-target)" to requestTarget.substringAfter(": "),
            "date" to date,
        )
    } else {
        val bodyBytes = body.encodeToByteArray()
        listOf(
            "date" to date,
            "(request-target)" to requestTarget.substringAfter(": "),
            "host" to host,
            "content-length" to bodyBytes.size.toString(),
            "content-type" to ContentType.Application.Json.toString(),
            "x-content-sha256" to Base64.Default.encode(digest(bodyBytes, DigestAlgorithm.SHA_256).value.toByteArray()),
        )
    }
    val signingString = signingHeaders.joinToString("\n") { (name, value) -> "$name: $value" }
    val privateKey = CryptographyProvider.Default.get(RSA.PKCS1)
        .privateKeyDecoder(SHA256)
        .decodeFromByteArray(RSA.PrivateKey.Format.PEM, credential.privateKeyPem.encodeToByteArray())
    val signature = Base64.Default.encode(privateKey.signatureGenerator().generateSignature(signingString.encodeToByteArray()))
    val names = signingHeaders.joinToString(" ") { it.first }
    return buildMap {
        put(
            HttpHeaders.Authorization,
            "Signature version=\"1\",headers=\"$names\",keyId=\"${credential.keyId}\",algorithm=\"rsa-sha256\",signature=\"$signature\"",
        )
        put(HttpHeaders.Host, host)
        put(HttpHeaders.Date, date)
        body?.let {
            put(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            put(HttpHeaders.ContentLength, it.encodeToByteArray().size.toString())
            put("x-content-sha256", signingHeaders.last().second)
        }
    }
}
