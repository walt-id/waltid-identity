@file:OptIn(ExperimentalTime::class)

package id.walt.webwallet.service.cache

import id.walt.webwallet.db.models.EntityNameResolutionCache
import id.walt.webwallet.db.models.EntityNameResolutionData
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

object EntityNameResolutionCacheService {
    fun get() = transaction {
        EntityNameResolutionCache.selectAll().map { EntityNameResolutionData(it) }
    }

    fun get(did: String) = transaction {
        EntityNameResolutionCache.selectAll().where { EntityNameResolutionCache.did eq did }.singleOrNull()?.let {
            EntityNameResolutionData(it)
        }
    }

    fun addOrUpdate(data: EntityNameResolutionData) = transaction {
        EntityNameResolutionCache.upsert(
            EntityNameResolutionCache.did
        ) {
            it[this.did] = data.did
            it[this.name] = data.name
            it[this.timestamp] = Clock.System.now().toJavaInstant()
        }
    }

    fun delete(did: String) = transaction {
        EntityNameResolutionCache.deleteWhere { EntityNameResolutionCache.did eq did }
    }
}
