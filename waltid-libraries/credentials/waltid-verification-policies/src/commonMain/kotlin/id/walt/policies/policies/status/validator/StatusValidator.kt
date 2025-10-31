package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.model.StatusEntry
import id.walt.policies.policies.status.model.StatusPolicyAttribute

interface StatusValidator<in K : StatusEntry, in T : StatusPolicyAttribute> {
    suspend fun validate(entry: K, attribute: T): Result<Unit>
}
