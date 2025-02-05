package id.walt.policies.policies

import id.walt.policies.policies.StreamUtils.getBitValue
import io.github.oshai.kotlinlogging.KotlinLogging
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
    private val logger = KotlinLogging.logger {}

    @JvmBlocking
    @JvmAsync
    actual override suspend fun verify(data: JsonObject, args: Any?, context: Map<String, Any>): Result<Any> {
        val credentialStatus = data["vc"]?.jsonObject?.get("credentialStatus")
            ?: return Result.success(
                JsonObject(mapOf("policy_available" to JsonPrimitive(false)))
            )

        logger.debug { "Credential status: $credentialStatus" }
        val statusListIndex = credentialStatus.jsonObject["statusListIndex"]?.jsonPrimitive?.content?.toULong()
        val statusListCredentialUrl = credentialStatus.jsonObject["statusListCredential"]?.jsonPrimitive?.content
        logger.debug { "Status list index: $statusListIndex" }
        logger.debug { "Credential URL: $statusListCredentialUrl" }

        val httpClient = HttpClient {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }

        val response = runCatching { httpClient.get(statusListCredentialUrl!!).bodyAsText() }.getOrElse {
            return Result.failure(Throwable("Error when getting Status List Credential from  $statusListCredentialUrl"))
        }
        logger.debug { "Credential URL response: $response" }
        // response is a jwt
        val bitValue = getRevocationStatusValue(response, statusListIndex).getOrElse {
            return Result.failure(Throwable(it.cause))
        }

        checkStatus(bitValue).getOrElse {
            return Result.failure(Throwable("Credential has been revoked"))
        }
        return Result.success(statusListCredentialUrl!!)
    }

    private fun checkStatus(it: List<Char>) = runCatching {
        require(StreamUtils.binToInt(it.joinToString("")) == 0)
    }

    private fun getRevocationStatusValue(
        response: String,
        statusListIndex: ULong?
    ) = runCatching {
        val payload = response.substringAfter(".").substringBefore(".")
            .let { Json.decodeFromString<JsonObject>(Base64Utils.decode(it).decodeToString()) }

        logger.debug { "Payload: $payload" }
        val credentialSubject = payload["vc"]!!.jsonObject["credentialSubject"]?.jsonObject!!
        logger.debug { "CredentialSubject: $credentialSubject" }
        val encodedList = credentialSubject["encodedList"]?.jsonPrimitive?.content ?: ""
        logger.debug { "EncodedList: $encodedList" }
        val bitValue = get(encodedList, statusListIndex)
        logger.debug { "EncodedList[$statusListIndex] = $bitValue" }
        // ensure bitValue always consists of valid binary characters (0,1)
        require(!bitValue.isNullOrEmpty()) { "Null or empty bit value" }
        require(isBinaryValue(bitValue)) { "Invalid bit value: $bitValue" }
        bitValue
    }

}

object Base64Utils {
    fun decode(base64: String): ByteArray = Base64.getDecoder().decode(base64)
    fun urlDecode(base64Url: String): ByteArray = Base64.getUrlDecoder().decode(base64Url)
}

object StreamUtils {
    private const val BITS_PER_BYTE_UNSIGNED = 8u

    fun binToInt(bin: String) = bin.toInt(2)

    fun getBitValue(inputStream: InputStream, index: ULong, bitSize: Int): List<Char> = inputStream.use { stream ->
        //TODO: bitSize constraints
        val bitStartPosition = index * bitSize.toUInt()
        val byteStart = bitStartPosition / BITS_PER_BYTE_UNSIGNED
        stream.skip(byteStart.toLong())
        val bytesToRead = (bitSize - 1) / BITS_PER_BYTE_UNSIGNED.toInt() + 1
        extractBitValue(stream.readNBytes(bytesToRead), index, bitSize.toUInt())
    }

    private fun extractBitValue(bytes: ByteArray, index: ULong, bitSize: UInt): List<Char> {
        val bits = bytes.toBitSequence()
        val bitStartPosition = index * bitSize % BITS_PER_BYTE_UNSIGNED
        val bitSet = bits.drop(bitStartPosition.toInt()).iterator()
        val result = mutableListOf<Boolean>()
        var b = 0u
        while (b++ < bitSize) {
            result.add(bitSet.next())
        }
        return result.map { if (it) '1' else '0' }
    }
}

fun get(bitstring: String, idx: ULong? = null, bitSize: Int = 1) =
    idx?.let { getBitValue(GZIPInputStream(Base64Utils.decode(bitstring).inputStream()), it, bitSize) }

fun isBinaryValue(value: List<Char>) = setOf('0', '1').let { valid ->
    value.all { it in valid }
}

fun ByteArray.toBitSequence(): Sequence<Boolean> = this.fold(emptySequence<Boolean>()) { acc, byte ->
    acc + (7 downTo 0).map { i -> (byte.toUInt() shr i) and 1u == 1u }.asSequence()
}