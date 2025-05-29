package id.walt.policies.policies.status.validator

import id.walt.policies.policies.status.IETFStatusPolicyArguments
import id.walt.policies.policies.status.entry.IETFEntry

class IETFStatusValidator : StatusValidator<IETFEntry, IETFStatusPolicyArguments> {
    override suspend fun validate(entry: IETFEntry, arguments: IETFStatusPolicyArguments): Result<String> {
        TODO("Not yet implemented")
    }
}