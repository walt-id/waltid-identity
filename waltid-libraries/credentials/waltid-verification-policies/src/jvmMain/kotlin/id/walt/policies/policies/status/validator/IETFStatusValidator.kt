package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.IETFCredentialStatusPolicyAttribute
import id.walt.policies.policies.status.IETFStatusContent
import id.walt.policies.policies.status.entry.IETFEntry
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies.policies.status.expansion.TokenStatusListExpansionAlgorithm
import id.walt.policies.policies.status.reader.StatusValueReader

class IETFStatusValidator(
    fetcher: CredentialFetcher,
    reader: StatusValueReader<IETFStatusContent>,
) : StatusValidatorBase<IETFStatusContent, IETFEntry, IETFCredentialStatusPolicyAttribute>(fetcher, reader) {

    override fun getStatusListExpansionAlgorithm(statusList: IETFStatusContent): StatusListExpansionAlgorithm =
        TokenStatusListExpansionAlgorithm()

    override fun customValidations(statusList: IETFStatusContent, attribute: IETFCredentialStatusPolicyAttribute) = Unit
}