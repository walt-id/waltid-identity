package id.walt.openid4vci.repository.refresh

import id.walt.openid4vci.repository.authorization.DuplicateCodeException

interface RefreshTokenRepository {
    @Throws(DuplicateCodeException::class)
    suspend fun save(record: RefreshTokenRecord)
    suspend fun get(tokenSignature: String): RefreshTokenRecord?

    /**
     * Keeps the old record as inactive and stores [replacement].
     *
     * Returns the old active record on success. Returns null when the token is
     * unknown, already inactive, or expired.
     */
    @Throws(DuplicateCodeException::class)
    suspend fun rotate(tokenSignature: String, replacement: RefreshTokenRecord): RefreshTokenRecord?
}