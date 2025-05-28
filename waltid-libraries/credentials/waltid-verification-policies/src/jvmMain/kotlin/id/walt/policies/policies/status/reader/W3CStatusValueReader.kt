package id.walt.policies.policies.status.reader

import id.walt.policies.policies.Base64Utils
import id.walt.policies.policies.status.BitValueReader
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class W3CStatusValueReader(
    private val bitValueReader: BitValueReader
) : StatusValueReader {
    private val logger = KotlinLogging.logger {}

    override fun read(response: String, statusListIndex: ULong) = runCatching {
        val payload = response.substringAfter(".").substringBefore(".")
            .let { Json.decodeFromString<JsonObject>(Base64Utils.decode(it).decodeToString()) }

        logger.debug { "Payload: $payload" }
        val credentialSubject = payload["vc"]!!.jsonObject["credentialSubject"]?.jsonObject!!
        logger.debug { "CredentialSubject: $credentialSubject" }
        val encodedList = credentialSubject["encodedList"]?.jsonPrimitive?.content ?: ""
        logger.debug { "EncodedList: $encodedList" }
        val bitValue = bitValueReader.get(encodedList, statusListIndex)
        logger.debug { "EncodedList[$statusListIndex] = $bitValue" }
        // ensure bitValue always consists of valid binary characters (0,1)
        require(!bitValue.isNullOrEmpty()) { "Null or empty bit value" }
        require(isBinaryValue(bitValue)) { "Invalid bit value: $bitValue" }
        bitValue
    }

    fun isBinaryValue(value: List<Char>) = setOf('0', '1').let { valid ->
        value.all { it in valid }
    }
}