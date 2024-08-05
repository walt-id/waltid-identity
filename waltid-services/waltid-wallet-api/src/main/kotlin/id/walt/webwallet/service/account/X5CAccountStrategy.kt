package id.walt.webwallet.service.account

import id.walt.webwallet.web.model.X5CAccountRequest

class X5CAccountStrategy(

) : PasswordlessAccountStrategy<X5CAccountRequest>() {
    override suspend fun register(tenant: String, request: X5CAccountRequest): Result<RegistrationResult> {
        TODO("Not yet implemented")
    }

    override suspend fun authenticate(tenant: String, request: X5CAccountRequest): AuthenticatedUser {
        TODO("Not yet implemented")
    }
}