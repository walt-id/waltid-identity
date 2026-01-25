package id.walt.policies2.vc.policies.status.validator

import id.walt.policies2.vc.policies.status.CredentialFetcher
import id.walt.policies2.vc.policies.status.model.StatusContent
import id.walt.policies2.vc.policies.status.model.StatusPolicyAttribute
import id.walt.policies2.vc.policies.status.model.StatusRetrievalError
import id.walt.policies2.vc.policies.status.model.StatusVerificationError
import id.walt.policies2.vc.policies.status.reader.StatusValueReader
import io.github.oshai.kotlinlogging.KotlinLogging

abstract class StatusValidatorBase<K : StatusContent, M : id.walt.policies2.vc.policies.status.model.StatusEntry, T : StatusPolicyAttribute>(
    private val fetcher: CredentialFetcher,
    private val reader: StatusValueReader<K>,
) : StatusValidator<M, T> {
    protected val logger = KotlinLogging.logger {}

    override suspend fun validate(entry: M, attribute: T): Result<Unit> = runCatching {
        logger.debug { "Credential URL: ${entry.uri}" }
        // download status credential
        val statusListContent = fetcher.fetch(entry.uri)
            .getOrElse {
                throw StatusRetrievalError(
                    it.message ?: "Status credential download error"
                )
            }
        // parse status list, response is a jwt
        val statusList = reader.read(statusListContent)
            .getOrElse {
                throw StatusRetrievalError(
                    it.message ?: "Status credential parsing error"
                )
            }
        val bitValue = getBitValue(statusList, entry)
        logger.debug { "EncodedList[${entry.index}] = $bitValue" }
        customValidations(statusList, attribute)
        statusValidations(bitValue, attribute)
    }

    protected abstract suspend fun getBitValue(statusList: K, entry: M): List<Char>

    protected abstract fun customValidations(statusList: K, attribute: T)

    private fun statusValidations(bitValue: List<Char>, attribute: T) {
        if (bitValue.isEmpty()) {
            throw StatusVerificationError("Null or empty bit value")
        }
        // ensure bitValue always consists of valid binary characters (0,1)
        if (!isBinaryValue(bitValue)) {
            throw StatusVerificationError("Invalid bit value: $bitValue")
        }
        val binaryString = bitValue.joinToString("")
        val intValue = binToInt(binaryString)
        if (intValue.toUInt() != attribute.value) {
            throw StatusVerificationError("Status validation failed: expected ${attribute.value}, but got $intValue")
        }
    }

    private fun isBinaryValue(value: List<Char>) = setOf('0', '1').let { valid ->
        value.all { it in valid }
    }

    private fun binToInt(bin: String) = bin.toInt(2)
}
