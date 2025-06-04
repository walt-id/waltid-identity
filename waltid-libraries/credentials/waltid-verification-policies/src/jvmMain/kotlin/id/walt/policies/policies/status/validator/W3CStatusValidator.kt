package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.Values.BITSTRING_STATUS_LIST
import id.walt.policies.policies.status.Values.REVOCATION_LIST_2020
import id.walt.policies.policies.status.Values.STATUS_LIST_2021
import id.walt.policies.policies.status.model.W3CStatusContent
import id.walt.policies.policies.status.model.W3CStatusPolicyAttribute
import id.walt.policies.policies.status.bit.BigEndianRepresentation
import id.walt.policies.policies.status.bit.BitValueReaderFactory
import id.walt.policies.policies.status.model.W3CEntry
import id.walt.policies.policies.status.model.StatusVerificationError
import id.walt.policies.policies.status.expansion.BitstringStatusListExpansionAlgorithm
import id.walt.policies.policies.status.expansion.RevocationList2020ExpansionAlgorithm
import id.walt.policies.policies.status.expansion.StatusList2021ExpansionAlgorithm
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies.policies.status.reader.StatusValueReader

class W3CStatusValidator(
    fetcher: CredentialFetcher,
    reader: StatusValueReader<W3CStatusContent>,
    private val bitValueReaderFactory: BitValueReaderFactory,
) : StatusValidatorBase<W3CStatusContent, W3CEntry, W3CStatusPolicyAttribute>(fetcher, reader) {

    override fun getBitValue(statusList: W3CStatusContent, index: ULong): List<Char> =
        bitValueReaderFactory.new(strategy = BigEndianRepresentation()).get(
            bitstring = statusList.list,
            idx = index,
            bitSize = statusList.size,
            expansionAlgorithm = getStatusListExpansionAlgorithm(statusList),
        )

    private fun getStatusListExpansionAlgorithm(statusList: W3CStatusContent): StatusListExpansionAlgorithm =
        when (statusList.type) {
            BITSTRING_STATUS_LIST -> BitstringStatusListExpansionAlgorithm()
            STATUS_LIST_2021 -> StatusList2021ExpansionAlgorithm()
            REVOCATION_LIST_2020 -> RevocationList2020ExpansionAlgorithm()
            else -> throw IllegalArgumentException("W3C status type not supported: ${statusList.type}")
        }

    override fun customValidations(statusList: W3CStatusContent, attribute: W3CStatusPolicyAttribute) {
        if (statusList.type != attribute.type) {
            throw StatusVerificationError("Type validation failed: expected ${attribute.type}, but got ${statusList.type}")
        }
        if (statusList.purpose != attribute.purpose) {
            throw StatusVerificationError("Purpose validation failed: expected ${attribute.purpose}, but got ${statusList.purpose}")
        }
    }
}