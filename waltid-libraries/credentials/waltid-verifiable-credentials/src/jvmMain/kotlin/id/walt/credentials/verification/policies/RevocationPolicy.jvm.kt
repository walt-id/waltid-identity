package id.walt.credentials.verification.policies

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import java.io.BufferedReader
import java.io.InputStream
import java.util.Base64
import java.util.zip.GZIPInputStream


@Serializable
actual class RevocationPolicy : RevocationPolicyMp() {
    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val credentialStatus = data["vc"]?.jsonObject?.get("credentialStatus")
            ?: return Result.success(
                JsonObject(mapOf("policy_available" to JsonPrimitive(false)))
            )

        val statusListIndex = credentialStatus.jsonObject["statusListIndex"]?.jsonPrimitive?.content?.toULong()
        val statusListCredentialUrl = credentialStatus.jsonObject["statusListCredential"]?.jsonPrimitive?.content

        val httpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = runCatching { httpClient.get(statusListCredentialUrl!!).bodyAsText() }

        if (response.isFailure) {
            return Result.failure(Throwable("Error when getting Status List Credential from  $statusListCredentialUrl"))
        }

        return try {
            // response is a jwt
            val payload = response.getOrThrow().substringAfter(".").substringBefore(".")
                .let { Json.decodeFromString<JsonObject>(Base64Utils.decode(it).decodeToString()) }

            val credentialSubject = payload["vc"]!!.jsonObject["credentialSubject"]?.jsonObject!!
            val encodedList = credentialSubject["encodedList"]?.jsonPrimitive?.content ?: ""
            val bitValue = get(encodedList, statusListIndex)
            if (bitValue!![0].code == 0) {
                Result.success(statusListCredentialUrl!!)
            } else {
                Result.failure(Throwable("Credential has been revoked"))
            }
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException()
        }
    }

}

object Base64Utils {
    fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
    fun urlDecode(base64Url: String): ByteArray = Base64.getUrlDecoder().decode(base64Url)
}

object StreamUtils {
    fun getBitValue(inputStream: InputStream, index: ULong, bitSize: Int) =
        inputStream.bufferedReader().use { buffer ->
            buffer.skip((index * bitSize.toULong()).toLong())
            extractBitValue(buffer, index, bitSize.toULong())
        }

    private fun extractBitValue(it: BufferedReader, index: ULong, bitSize: ULong): List<Char> {
        var int = 0
        var count = index * bitSize
        val result = mutableListOf<Char>()
        while (count < index * bitSize + bitSize) {
            int = it.read().takeIf { it != -1 } ?: error("Reached end of stream")
            result.add(int.digitToChar())
            count += 1.toULong()
        }
        return result
    }
}

fun get(bitstring: String, idx: ULong? = null, bitSize: Int = 1) =
    idx?.let { StreamUtils.getBitValue(GZIPInputStream(Base64Utils.decode(bitstring).inputStream()), it, bitSize) }
