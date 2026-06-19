package id.walt.openid4vci.repository.preauthorized

import id.walt.openid4vci.repository.authorization.DuplicateCodeException

/**
 * Storage abstraction for pre-authorized code sessions.
 */
interface PreAuthorizedCodeRepository {
    @Throws(DuplicateCodeException::class)
    suspend fun save(record: PreAuthorizedCodeRecord)
    suspend fun get(code: String): PreAuthorizedCodeRecord?
    suspend fun consume(code: String): PreAuthorizedCodeRecord?
}
