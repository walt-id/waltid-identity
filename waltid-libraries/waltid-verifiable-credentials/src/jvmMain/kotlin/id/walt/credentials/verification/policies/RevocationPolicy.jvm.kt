package id.walt.credentials.verification.policies

import java.io.BufferedReader
import java.io.InputStream
import java.util.*

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.io.encoding.ExperimentalEncodingApi
import java.util.zip.GZIPInputStream


actual class RevocationPolicy  : RevocationPolicyMp() {
    @OptIn(ExperimentalEncodingApi::class)
    @JvmBlocking
    @JvmAsync
    override suspend fun verify(data: JsonElement, args: Any?, context: Map<String, Any>): Result<Any> {

        var successfulKey = ""

        fun setKey(key: String) {
            successfulKey = key
        }

        val credentialStatus = data.jsonObject["vc"]?.jsonObject?.get("credentialStatus")
            ?: return Result.success(
                JsonObject(mapOf("policy_available" to JsonPrimitive(false)))
            )

        val statusListIndex = credentialStatus.jsonObject["statusListIndex"]?.jsonPrimitive?.content?.toULong()
        val statusListCredentialUrl = credentialStatus.jsonObject["statusListCredential"]?.jsonPrimitive?.content

        val httpClient = HttpClient() {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
        val now = Clock.System.now()
        val availableIn = now - now

         runCatching {httpClient.get(statusListCredentialUrl!!).bodyAsText() }
             .onSuccess{
                 try {
                     // response is a jwt
                     val payload = it.substringAfter(".").substringBefore(".").let {  Json.decodeFromString<JsonObject>(Base64Utils.decode(it).decodeToString()) }

                     val credentialSubject = payload["vc"]!!.jsonObject["credentialSubject"]?.jsonObject!!
                     val encodedList = credentialSubject["encodedList"]?.jsonPrimitive?.content  as? String ?: ""
                     val bitValue = get(encodedList, statusListIndex)
                     if (bitValue!![0].code == 0) {
                         return Result.success("")
                     } else {
                         return  Result.failure(Throwable("Credential has been revoked"))
                     }
                 } catch (e: NumberFormatException) {
                     throw IllegalArgumentException()
                 }
             }
             .onFailure {
                 return Result.failure(Throwable("Error when getting Status List Credential from  $statusListCredentialUrl"))
             }
        return Result.success("")
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
