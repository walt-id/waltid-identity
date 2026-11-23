package id.walt.webwallet.service.account

import id.walt.webwallet.web.model.AccountRequest

abstract class AccountStrategy<in T : AccountRequest> {
    abstract suspend fun register(tenant: String, request: T): Result<RegistrationResult>

    abstract suspend fun authenticate(tenant: String, request: T): AuthenticatedUser
}

abstract class PasswordAccountStrategy<T : AccountRequest> : AccountStrategy<T>() {
    protected fun hashPassword(password: ByteArray): String = Argon2Passwords.hash(password)
}

abstract class PasswordlessAccountStrategy<T : AccountRequest> : AccountStrategy<T>()