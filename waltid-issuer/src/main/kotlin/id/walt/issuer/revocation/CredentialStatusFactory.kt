package id.walt.issuer.revocation

import id.walt.issuer.revocation.statuslist2021.StatusList2021EntryClientService
import id.walt.issuer.utils.asMap

object CredentialStatusFactory {
    private val statusList2021 = StatusList2021EntryClientService()
    fun create(parameter: CredentialStatusFactoryParameter): Map<String, Any?> = when (parameter) {
        is StatusListEntryFactoryParameter -> statusList2021.create(parameter).asMap()
        else -> throw IllegalArgumentException("Status type not supported: ${parameter.javaClass.simpleName}")
    }
}

interface CredentialStatusFactoryParameter
data class StatusListEntryFactoryParameter(
    val credentialUrl: String,
    val purpose: String,
    val issuer: String,
) : CredentialStatusFactoryParameter
