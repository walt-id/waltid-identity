package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.CredentialStatusPolicyAttribute
import id.walt.policies.policies.status.StatusContent
import id.walt.policies.policies.status.bit.BigEndianRepresentation
import id.walt.policies.policies.status.bit.BitValueReader
import id.walt.policies.policies.status.entry.StatusEntry
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies.policies.status.reader.StatusValueReader
import io.github.oshai.kotlinlogging.KotlinLogging

abstract class StatusValidatorBase<K : StatusContent, M : StatusEntry, T : CredentialStatusPolicyAttribute>(
    private val fetcher: CredentialFetcher,
    private val reader: StatusValueReader<K>,
) : StatusValidator<M, T> {
    protected val logger = KotlinLogging.logger {}


    override suspend fun validate(entry: M, attribute: T): Result<String> = runCatching {
        logger.debug { "Credential URL: ${entry.uri}" }
        // download status credential
        val statusListContent = fetcher.fetch(entry.uri).getOrThrow()
        // parse status list, response is a jwt
        val statusList = reader.read(statusListContent).getOrThrow()
        val bitValueReader = BitValueReader(
            expansionAlgorithm = getStatusListExpansionAlgorithm(statusList = statusList),
            bitRepresentationStrategy = BigEndianRepresentation()
        )
        val bitValue = bitValueReader.get(
            bitstring = statusList.list,
            idx = entry.index,
            bitSize = statusList.size,
        )
        customValidations(statusList, attribute)
        logger.debug { "Status list index: ${entry.index}" }
        logger.debug { "EncodedList[${entry.index}] = $bitValue" }
        require(bitValue.isNotEmpty()) { "Null or empty bit value" }
        // ensure bitValue always consists of valid binary characters (0,1)
        require(isBinaryValue(bitValue)) { "Invalid bit value: $bitValue" }
        val binaryString = bitValue.joinToString("")
        require(binToInt(binaryString).toUInt() == attribute.value)
        binaryString
    }

    protected abstract fun getStatusListExpansionAlgorithm(statusList: K): StatusListExpansionAlgorithm

    protected abstract fun customValidations(statusList: K, attribute: T)

    protected fun isBinaryValue(value: List<Char>) = setOf('0', '1').let { valid ->
        value.all { it in valid }
    }

    protected fun binToInt(bin: String) = bin.toInt(2)
}