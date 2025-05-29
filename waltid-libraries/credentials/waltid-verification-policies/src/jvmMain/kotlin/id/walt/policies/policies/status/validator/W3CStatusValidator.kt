package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.W3CStatusContent
import id.walt.policies.policies.status.W3CStatusPolicyArguments
import id.walt.policies.policies.status.bit.BigEndianRepresentation
import id.walt.policies.policies.status.bit.BitValueReader
import id.walt.policies.policies.status.entry.W3CEntry
import id.walt.policies.policies.status.expansion.BitstringStatusListExpansionAlgorithm
import id.walt.policies.policies.status.expansion.RevocationList2020ExpansionAlgorithm
import id.walt.policies.policies.status.expansion.StatusList2021ExpansionAlgorithm
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies.policies.status.reader.StatusValueReader
import io.github.oshai.kotlinlogging.KotlinLogging

class W3CStatusValidator(
    private val fetcher: CredentialFetcher,
    private val reader: StatusValueReader<W3CStatusContent>,
) : StatusValidator<W3CEntry, W3CStatusPolicyArguments> {
    private val logger = KotlinLogging.logger {}

    override suspend fun validate(entry: W3CEntry, arguments: W3CStatusPolicyArguments): Result<String> = runCatching {
        val statusList = processStatus(entry.uri, entry.index).getOrElse { return Result.failure(Throwable(it.cause)) }
        require(statusList.type == arguments.type)
        require(statusList.purpose == arguments.purpose)
        val bitValueReader = BitValueReader(
            expansionAlgorithm = getStatusListExpansionAlgorithm(type = statusList.type),
            bitRepresentationStrategy = BigEndianRepresentation()
        )
        val bitValue = bitValueReader.get(
            bitstring = statusList.list,
            idx = entry.index,
            bitSize = statusList.size,
        )
        logger.debug { "EncodedList[${entry.index}] = $bitValue" }
        require(bitValue.isNotEmpty()) { "Null or empty bit value" }
        // ensure bitValue always consists of valid binary characters (0,1)
        require(isBinaryValue(bitValue)) { "Invalid bit value: $bitValue" }
        val binaryString = bitValue.joinToString("")
        require(binToInt(binaryString).toUInt() == arguments.value)
        binaryString
    }

    private suspend fun processStatus(url: String, index: ULong): Result<W3CStatusContent> {
        // process status
        logger.debug { "Status list index: $index" }
        logger.debug { "Credential URL: $url" }
        // download status credential
        val response = fetcher.fetch(url).getOrThrow()
        // parse status list, response is a jwt
        return reader.read(response)
    }

    private fun getStatusListExpansionAlgorithm(type: String): StatusListExpansionAlgorithm = when (type) {
        "BitstringStatusList" -> BitstringStatusListExpansionAlgorithm()
        "StatusList2021" -> StatusList2021ExpansionAlgorithm()
        "RevocationList2020" -> RevocationList2020ExpansionAlgorithm()
        else -> TODO("not supported")
    }

    private fun isBinaryValue(value: List<Char>) = setOf('0', '1').let { valid ->
        value.all { it in valid }
    }

    private fun binToInt(bin: String) = bin.toInt(2)
}