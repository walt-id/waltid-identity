package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.CredentialStatusPolicyArguments

interface StatusValidator<in K, T : CredentialStatusPolicyArguments> {
    suspend fun validate(entry: K, arguments: T): Result<String>
}