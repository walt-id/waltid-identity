package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.StatusPolicyAttribute
import id.walt.policies.policies.status.entry.StatusEntry

interface StatusValidator<in K : StatusEntry, in T : StatusPolicyAttribute> {
    suspend fun validate(entry: K, attribute: T): Result<Unit>
}