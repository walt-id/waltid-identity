package id.walt.openid4vci.repository.authorization

/**
 * Storage abstraction for authorization code sessions.
 */
interface AuthorizationCodeRepository {
    @Throws(DuplicateCodeException::class)
    suspend fun save(record: AuthorizationCodeRecord)
    suspend fun consume(code: String): AuthorizationCodeRecord?
}

class DuplicateCodeException : IllegalStateException("Code collision detected")
