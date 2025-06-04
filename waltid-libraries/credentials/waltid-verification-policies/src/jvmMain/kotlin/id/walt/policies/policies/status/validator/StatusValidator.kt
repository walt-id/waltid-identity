package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialStatusPolicyAttribute
import id.walt.policies.policies.status.entry.StatusEntry

interface StatusValidator<in K : StatusEntry, in T : CredentialStatusPolicyAttribute> {
    suspend fun validate(entry: K, arguments: T): Result<String>
}