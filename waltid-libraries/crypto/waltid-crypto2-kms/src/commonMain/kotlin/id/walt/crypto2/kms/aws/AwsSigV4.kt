package id.walt.crypto2.kms.aws

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.kms.digest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import kotlin.time.Instant

internal suspend fun awsSigV4Headers(
    options: AwsKmsOptions,
    credentials: AwsCredentials,
    payload: String,
    target: String,
    instant: Instant,
): Map<String, String> {
    val timestamp = instant.toAwsTimestamp()
    val date = timestamp.substring(0, 8)
    val host = Url(options.endpointUrl()).let { if (it.port == it.protocol.defaultPort) it.host else "${it.host}:${it.port}" }
    val canonicalHeaders = buildList {
        add("content-type" to AWS_CONTENT_TYPE)
        add("host" to host)
        add("x-amz-date" to timestamp)
        credentials.sessionToken?.let { add("x-amz-security-token" to it) }
        add("x-amz-target" to target)
    }
    val signedHeaders = canonicalHeaders.joinToString(";") { it.first }
    val canonicalRequest = buildString {
        append("POST\n/\n\n")
        canonicalHeaders.forEach { (name, value) -> append(name).append(':').append(value.trim()).append('\n') }
        append('\n').append(signedHeaders).append('\n')
        append(digest(payload.encodeToByteArray(), DigestAlgorithm.SHA_256).value.toByteArray().hex())
    }
    val scope = "$date/${options.region}/kms/aws4_request"
    val stringToSign = "AWS4-HMAC-SHA256\n$timestamp\n$scope\n" +
        digest(canonicalRequest.encodeToByteArray(), DigestAlgorithm.SHA_256).value.toByteArray().hex()
    val dateKey = hmac("AWS4${credentials.secretAccessKey}".encodeToByteArray(), date)
    val regionKey = hmac(dateKey, options.region)
    val serviceKey = hmac(regionKey, "kms")
    val signingKey = hmac(serviceKey, "aws4_request")
    val signature = hmac(signingKey, stringToSign).hex()
    return buildMap {
        put(HttpHeaders.Authorization, "AWS4-HMAC-SHA256 Credential=${credentials.accessKeyId}/$scope, SignedHeaders=$signedHeaders, Signature=$signature")
        put(HttpHeaders.ContentType, AWS_CONTENT_TYPE)
        put(HttpHeaders.Host, host)
        put("X-Amz-Date", timestamp)
        put("X-Amz-Target", target)
        credentials.sessionToken?.let { put("X-Amz-Security-Token", it) }
    }
}

private suspend fun hmac(key: ByteArray, data: String): ByteArray = CryptographyProvider.Default
    .get(HMAC)
    .keyDecoder(SHA256)
    .decodeFromByteArray(HMAC.Key.Format.RAW, key)
    .signatureGenerator()
    .generateSignature(data.encodeToByteArray())

private fun Instant.toAwsTimestamp(): String {
    val value = toString()
    require(value.length >= 19) { "AWS signing instant is invalid" }
    return value.substring(0, 19).replace("-", "").replace(":", "") + "Z"
}

private fun ByteArray.hex(): String = joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

internal val AWS_JSON_CONTENT_TYPE: ContentType = ContentType.parse(AWS_CONTENT_TYPE)
private const val AWS_CONTENT_TYPE = "application/x-amz-json-1.1"
