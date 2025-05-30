package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialFetcher
import id.walt.policies.policies.status.W3CCredentialStatusPolicyAttribute
import id.walt.policies.policies.status.W3CStatusContent
import id.walt.policies.policies.status.entry.W3CEntry
import id.walt.policies.policies.status.expansion.BitstringStatusListExpansionAlgorithm
import id.walt.policies.policies.status.expansion.RevocationList2020ExpansionAlgorithm
import id.walt.policies.policies.status.expansion.StatusList2021ExpansionAlgorithm
import id.walt.policies.policies.status.expansion.StatusListExpansionAlgorithm
import id.walt.policies.policies.status.reader.StatusValueReader

class W3CStatusValidator(
    fetcher: CredentialFetcher,
    reader: StatusValueReader<W3CStatusContent>,
) : StatusValidatorBase<W3CStatusContent, W3CEntry, W3CCredentialStatusPolicyAttribute>(fetcher, reader) {

    override fun getStatusListExpansionAlgorithm(statusList: W3CStatusContent): StatusListExpansionAlgorithm =
        when (statusList.type) {
            "BitstringStatusList" -> BitstringStatusListExpansionAlgorithm()
            "StatusList2021" -> StatusList2021ExpansionAlgorithm()
            "RevocationList2020" -> RevocationList2020ExpansionAlgorithm()
            else -> TODO("not supported")
        }

    override fun customValidations(statusList: W3CStatusContent, attribute: W3CCredentialStatusPolicyAttribute) {
        require(statusList.type == attribute.type)
        require(statusList.purpose == attribute.purpose)
    }
}