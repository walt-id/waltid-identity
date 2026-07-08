package id.walt.openid4vci.repository.authorization

import kotlin.coroutines.cancellation.CancellationException

/**
 * Storage abstraction for authorization code sessions.
 */
interface AuthorizationCodeRepository {
    @Throws(DuplicateCodeException::class, CancellationException::class)
    suspend fun save(record: AuthorizationCodeRecord)
    suspend fun consume(code: String): AuthorizationCodeRecord?
}

class DuplicateCodeException : IllegalStateException("Code collision detected")
