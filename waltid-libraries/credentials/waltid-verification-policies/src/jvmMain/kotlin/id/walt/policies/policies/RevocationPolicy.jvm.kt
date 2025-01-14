package id.walt.policies.policies

import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import java.io.InputStream
import java.util.Base64
import java.util.BitSet
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
            // ensure bitValue always consists of valid binary characters (0,1)
            require(!bitValue.isNullOrEmpty()) { "Null or empty bit value" }
            require(isBinaryValue(bitValue)) { "Invalid bit value" }
            if (StreamUtils.binToInt(bitValue.joinToString("")) == 0) {
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
    private const val BITS_PER_BYTE = 8u

    fun binToInt(bin: String) = bin.toInt(2)

    fun getBitValue(inputStream: InputStream, index: ULong, bitSize: Int): List<Char> = inputStream.use { stream ->
        //TODO: bitSize constraints
        val bitStartPosition = index * bitSize.toUInt()
        val byteStart = bitStartPosition / BITS_PER_BYTE
        stream.skip(byteStart.toLong())
        val bytesToRead = (bitSize - 1) / BITS_PER_BYTE.toInt() + 1
        extractBitValue(stream.readNBytes(bytesToRead), index, bitSize.toUInt())
    }

    private fun extractBitValue(bytes: ByteArray, index: ULong, bitSize: UInt): List<Char> {
        val bitSet = BitSet.valueOf(bytes)
        val bitStart = index * bitSize % BITS_PER_BYTE
        val result = mutableListOf<Char>()
        for (i in bitStart..<bitStart + bitSize) {
            val b = bitSet[i.toInt()].takeIf { it }?.let { 1 } ?: 0
            result.add(b.digitToChar())
        }
        return result
    }
}

fun get(bitstring: String, idx: ULong? = null, bitSize: Int = 1) =
    idx?.let { StreamUtils.getBitValue(GZIPInputStream(Base64Utils.decode(bitstring).inputStream()), it, bitSize) }

fun isBinaryValue(value: List<Char>) = setOf('0', '1').let { valid ->
    value.all { it in valid }
}