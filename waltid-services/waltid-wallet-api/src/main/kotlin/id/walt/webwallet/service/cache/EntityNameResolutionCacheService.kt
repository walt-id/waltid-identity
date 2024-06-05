package id.walt.webwallet.service.cache

import id.walt.webwallet.db.models.EntityNameResolutionCache
import id.walt.webwallet.db.models.EntityNameResolutionData
import kotlinx.datetime.Clock
import kotlinx.datetime.toJavaInstant
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert

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