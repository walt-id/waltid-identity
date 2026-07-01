package id.walt.openid4vci.repository.preauthorized

import id.walt.openid4vci.repository.authorization.DuplicateCodeException
import kotlin.coroutines.cancellation.CancellationException

/**
 * Storage abstraction for pre-authorized code sessions.
 */
interface PreAuthorizedCodeRepository {
    @Throws(DuplicateCodeException::class, CancellationException::class)
    suspend fun save(record: PreAuthorizedCodeRecord)
    suspend fun get(code: String): PreAuthorizedCodeRecord?
    suspend fun consume(code: String): PreAuthorizedCodeRecord?
}
