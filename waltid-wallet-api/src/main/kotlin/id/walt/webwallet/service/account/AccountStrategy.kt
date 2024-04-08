package id.walt.webwallet.service.account

import id.walt.webwallet.web.controllers.ByteLoginRequest
import id.walt.webwallet.web.model.AccountRequest

abstract class AccountStrategy<in T : AccountRequest> {
    abstract suspend fun register(tenant: String, request: T): Result<RegistrationResult>

    abstract suspend fun authenticate(tenant: String, request: T): AuthenticatedUser
}

abstract class PasswordAccountStrategy<T : AccountRequest> : AccountStrategy<T>() {
    /*protected fun hashPassword(password: ByteArray): String = Argon2Factory.create().run {
        hash(10, 65536, 1, password).also {
            wipeArray(password)
        }
    }*/
    protected fun hashPassword(password: ByteArray): String = password.decodeToString()

    /*protected fun verifyPassword(pwHash: String, req: ByteLoginRequest) = Argon2Factory.create().run {
        verify(pwHash, req.password).also {
            wipeArray(req.password)
        }
    }*/
    protected fun verifyPassword(pwHash: String, req: ByteLoginRequest) = req.password.decodeToString() == pwHash
}

abstract class PasswordlessAccountStrategy<T : AccountRequest> : AccountStrategy<T>()
