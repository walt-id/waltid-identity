package id.walt.policies2.vc.policies.status.validator

import id.walt.policies2.vc.policies.status.CredentialFetcher
import id.walt.policies2.vc.policies.status.bit.BitValueReaderFactory
import id.walt.policies2.vc.policies.status.bit.LittleEndianRepresentation
import id.walt.policies2.vc.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies2.vc.policies.status.model.IETFEntry
import id.walt.policies2.vc.policies.status.model.IETFStatusContent
import id.walt.policies2.vc.policies.status.model.IETFStatusPolicyAttribute
import id.walt.policies2.vc.policies.status.reader.StatusValueReader

class IETFStatusValidator(
    fetcher: CredentialFetcher,
    reader: StatusValueReader<IETFStatusContent>,
    bitValueReaderFactory: BitValueReaderFactory,
    private val expansionAlgorithm: StatusListExpansionAlgorithm,
) : StatusValidatorBase<IETFStatusContent, IETFEntry, IETFStatusPolicyAttribute>(
    fetcher,
    reader
) {
    private val bitValueReader =
        bitValueReaderFactory.new(strategy = LittleEndianRepresentation())

    override suspend fun getBitValue(
        statusList: IETFStatusContent,
        entry: IETFEntry
    ): List<Char> = bitValueReader.get(
        bitstring = statusList.list,
        idx = entry.index,
        bitSize = statusList.size,
        expansionAlgorithm = expansionAlgorithm,
    )

    override fun customValidations(
        statusList: IETFStatusContent,
        attribute: IETFStatusPolicyAttribute
    ) = Unit
}
