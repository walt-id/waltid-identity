package id.walt.issuer2.repository

import id.walt.issuer2.domain.IssuanceSession

interface IssuanceSessionRepository {
    suspend fun save(session: IssuanceSession): IssuanceSession
    suspend fun get(sessionId: String): IssuanceSession?
    suspend fun list(): List<IssuanceSession>
    suspend fun remove(sessionId: String)
}
