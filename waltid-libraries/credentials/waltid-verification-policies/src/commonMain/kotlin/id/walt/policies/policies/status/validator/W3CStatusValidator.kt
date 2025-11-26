package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.bit.BigEndianRepresentation
import id.walt.policies.policies.status.bit.BitValueReaderFactory
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithmFactory
import id.walt.policies.policies.status.model.StatusVerificationError
import id.walt.policies.policies.status.model.W3CEntry
import id.walt.policies.policies.status.model.W3CStatusContent
import id.walt.policies.policies.status.model.W3CStatusPolicyAttribute
import id.walt.policies.policies.status.reader.StatusValueReader

class W3CStatusValidator(
    fetcher: CredentialFetcher,
    reader: StatusValueReader<W3CStatusContent>,
    private val bitValueReaderFactory: BitValueReaderFactory,
    private val expansionAlgorithmFactory: StatusListExpansionAlgorithmFactory<W3CStatusContent>,
) : StatusValidatorBase<W3CStatusContent, W3CEntry, W3CStatusPolicyAttribute>(fetcher, reader) {

    override suspend fun getBitValue(statusList: W3CStatusContent, entry: W3CEntry): List<Char> =
        bitValueReaderFactory.new(strategy = BigEndianRepresentation()).get(
            bitstring = statusList.list,
            idx = entry.index,
            bitSize = entry.size,
            expansionAlgorithm = expansionAlgorithmFactory.create(statusList),
        )

    override fun customValidations(statusList: W3CStatusContent, attribute: W3CStatusPolicyAttribute) {
        if (!statusList.type.equals(attribute.type, ignoreCase = true)) {
            throw StatusVerificationError("Type validation failed: expected ${attribute.type}, but got ${statusList.type}")
        }
        if (!statusList.purpose.equals(attribute.purpose, ignoreCase = true)) {
            throw StatusVerificationError("Purpose validation failed: expected ${attribute.purpose}, but got ${statusList.purpose}")
        }
    }
}
