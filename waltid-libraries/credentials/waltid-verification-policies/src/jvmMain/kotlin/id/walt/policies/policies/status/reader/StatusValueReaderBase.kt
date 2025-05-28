package id.walt.policies.policies.status.reader

import id.walt.policies.policies.status.BitValueReader
import id.walt.policies.policies.status.StatusContent
import id.walt.policies.policies.status.parser.ContentParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json

abstract class StatusValueReaderBase<K, T : StatusContent>(
    private val parser: ContentParser<K>,
    private val bitValueReader: BitValueReader,
) : StatusValueReader {
    protected val logger = KotlinLogging.logger {}
    protected val jsonModule = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override fun read(response: String, statusListIndex: ULong) = runCatching {
        val payload = parser.parse(response)
        logger.debug { "Payload: $payload" }
        val statusList = parseStatusList(payload)
        logger.debug { "EncodedList: $statusList" }
        val bitValue = bitValueReader.get(statusList.list, statusListIndex, statusList.size)
        logger.debug { "EncodedList[$statusListIndex] = $bitValue" }
        // ensure bitValue always consists of valid binary characters (0,1)
        require(!bitValue.isNullOrEmpty()) { "Null or empty bit value" }
        require(isBinaryValue(bitValue)) { "Invalid bit value: $bitValue" }
        bitValue
    }

    protected abstract fun parseStatusList(payload: K): T

    private fun isBinaryValue(value: List<Char>) = setOf('0', '1').let { valid ->
        value.all { it in valid }
    }
}