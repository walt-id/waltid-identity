package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.bit.BitValueReaderFactory
import id.walt.policies.policies.status.bit.LittleEndianRepresentation
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies.policies.status.model.IETFEntry
import id.walt.policies.policies.status.model.IETFStatusContent
import id.walt.policies.policies.status.model.IETFStatusPolicyAttribute
import id.walt.policies.policies.status.reader.StatusValueReader

class IETFStatusValidator(
    fetcher: CredentialFetcher,
    bitValueReaderFactory: BitValueReaderFactory,
    private val expansionAlgorithm: StatusListExpansionAlgorithm,
    vararg reader: StatusValueReader<IETFStatusContent>,
) : StatusValidatorBase<IETFStatusContent, IETFEntry, IETFStatusPolicyAttribute>(fetcher, *reader) {
    private val bitValueReader = bitValueReaderFactory.new(strategy = LittleEndianRepresentation())

    override suspend fun getBitValue(statusList: IETFStatusContent, entry: IETFEntry): List<Char> =
        bitValueReader.get(
            bitstring = statusList.list,
            idx = entry.index,
            bitSize = statusList.size,
            expansionAlgorithm = expansionAlgorithm,
        ).let { bitValue ->
            // IETF Token Status List stores values LSB-first; reverse valid bits so
            // binToInt matches the integer status (suspended = 2). Invalid bit lists
            // are left as-is so error messages stay stable.
            if (bitValue.all { it == '0' || it == '1' }) bitValue.reversed() else bitValue
        }

    override fun customValidations(statusList: IETFStatusContent, attribute: IETFStatusPolicyAttribute) = Unit
}