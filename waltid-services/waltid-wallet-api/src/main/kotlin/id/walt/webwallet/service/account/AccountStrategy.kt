package id.walt.webwallet.service.account

import de.mkammerer.argon2.Argon2Factory
import id.walt.webwallet.web.model.AccountRequest

abstract class AccountStrategy<in T : AccountRequest> {
    abstract suspend fun register(tenant: String, request: T): Result<RegistrationResult>

    abstract suspend fun authenticate(tenant: String, request: T): AuthenticatedUser
}

abstract class PasswordAccountStrategy<T : AccountRequest> : AccountStrategy<T>() {
    protected fun hashPassword(password: ByteArray): String = Argon2Factory.create().run {
        hash(10, 65536, 1, password).also {
            wipeArray(password)
        }
    }
}

abstract class PasswordlessAccountStrategy<T : AccountRequest> : AccountStrategy<T>()