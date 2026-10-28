package id.walt.policies.policies.status.validator

import id.walt.credentials.formats.DigitalCredential
import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.bit.BitValueReaderFactory
import id.walt.policies.policies.status.bit.LittleEndianRepresentation
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies.policies.status.model.IETFEntry
import id.walt.policies.policies.status.model.IETFStatusContent
import id.walt.policies.policies.status.model.IETFStatusPolicyAttribute
import id.walt.policies.policies.status.reader.StatusValueReader
import id.walt.policies.policies.status.signature.IetfStatusListTokenChecker
import id.walt.policies2.vc.policies.status.signature.StatusListSignerAuthorizer

class IETFStatusValidator(
    fetcher: CredentialFetcher,
    bitValueReaderFactory: BitValueReaderFactory,
    private val expansionAlgorithm: StatusListExpansionAlgorithm,
    vararg reader: StatusValueReader<IETFStatusContent>,
    private val tokenChecker: IetfStatusListTokenChecker = IetfStatusListTokenChecker(),
) : StatusValidatorBase<IETFStatusContent, IETFEntry, IETFStatusPolicyAttribute>(fetcher, *reader) {
    private val bitValueReader = bitValueReaderFactory.new(strategy = LittleEndianRepresentation())

    suspend fun validate(
        entry: IETFEntry,
        attribute: IETFStatusPolicyAttribute,
        referencedCredential: DigitalCredential,
        signerAuthorizer: StatusListSignerAuthorizer? = null,
    ): Result<Unit> = validate(entry, attribute, authenticate = { content ->
        tokenChecker.check(content, entry.uri, referencedCredential, signerAuthorizer)
    })

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