package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.W3CStatusContent
import id.walt.policies.policies.status.W3CCredentialStatusPolicyAttribute
import id.walt.policies.policies.status.bit.BigEndianRepresentation
import id.walt.policies.policies.status.bit.BitValueReader
import id.walt.policies.policies.status.entry.W3CEntry
import id.walt.policies.policies.status.expansion.BitstringStatusListExpansionAlgorithm
import id.walt.policies.policies.status.expansion.RevocationList2020ExpansionAlgorithm
import id.walt.policies.policies.status.expansion.StatusList2021ExpansionAlgorithm
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies.policies.status.reader.StatusValueReader

class W3CStatusValidator(
    fetcher: CredentialFetcher,
    private val reader: StatusValueReader<W3CStatusContent>,
) : StatusValidatorBase<W3CStatusContent, W3CEntry, W3CCredentialStatusPolicyAttribute>(fetcher, reader) {

    override suspend fun validate(entry: W3CEntry, arguments: W3CCredentialStatusPolicyAttribute): Result<String> = runCatching {
        val statusListContent = downloadStatusCredential(entry.uri).getOrThrow()
        // parse status list, response is a jwt
        val statusList = reader.read(statusListContent).getOrThrow()
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
        logger.debug { "Status list index: ${entry.index}" }
        logger.debug { "EncodedList[${entry.index}] = $bitValue" }
        require(bitValue.isNotEmpty()) { "Null or empty bit value" }
        // ensure bitValue always consists of valid binary characters (0,1)
        require(isBinaryValue(bitValue)) { "Invalid bit value: $bitValue" }
        val binaryString = bitValue.joinToString("")
        require(binToInt(binaryString).toUInt() == arguments.value)
        binaryString
    }

    override fun getStatusListExpansionAlgorithm(type: String): StatusListExpansionAlgorithm = when (type) {
        "BitstringStatusList" -> BitstringStatusListExpansionAlgorithm()
        "StatusList2021" -> StatusList2021ExpansionAlgorithm()
        "RevocationList2020" -> RevocationList2020ExpansionAlgorithm()
        else -> TODO("not supported")
    }
}