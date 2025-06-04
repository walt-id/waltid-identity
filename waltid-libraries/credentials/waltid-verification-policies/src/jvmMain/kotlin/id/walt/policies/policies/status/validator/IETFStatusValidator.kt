package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.model.IETFStatusPolicyAttribute
import id.walt.policies.policies.status.model.IETFStatusContent
import id.walt.policies.policies.status.bit.BitValueReaderFactory
import id.walt.policies.policies.status.bit.LittleEndianRepresentation
import id.walt.policies.policies.status.model.IETFEntry
import id.walt.policies.policies.status.expansion.TokenStatusListExpansionAlgorithm
import id.walt.policies.policies.status.reader.StatusValueReader

class IETFStatusValidator(
    fetcher: CredentialFetcher,
    reader: StatusValueReader<IETFStatusContent>,
    bitValueReaderFactory: BitValueReaderFactory,
) : StatusValidatorBase<IETFStatusContent, IETFEntry, IETFStatusPolicyAttribute>(fetcher, reader) {
    private val bitValueReader = bitValueReaderFactory.new(strategy = LittleEndianRepresentation())

    override fun getBitValue(statusList: IETFStatusContent, index: ULong): List<Char> = bitValueReader.get(
        bitstring = statusList.list,
        idx = index,
        bitSize = statusList.size,
        expansionAlgorithm = TokenStatusListExpansionAlgorithm(),
    )

    override fun customValidations(statusList: IETFStatusContent, attribute: IETFStatusPolicyAttribute) = Unit
}