package id.walt.webwallet.usecase.entity

import id.walt.webwallet.db.models.EntityNameResolutionData
import id.walt.webwallet.service.cache.EntityNameResolutionCacheService
import id.walt.webwallet.service.entity.EntityNameResolutionService
import kotlinx.datetime.Clock.System.now
import kotlinx.datetime.Instant

class EntityNameResolutionUseCase(
    private val cacheService: EntityNameResolutionCacheService,
    private val nameResolutionService: EntityNameResolutionService,
) {
    //TODO: make configurable
    private val cacheAge = 1//day
    suspend fun resolve(did: String): String = let {
        cacheService.get(did) ?: EntityNameResolutionData(did = did)
    }.let { it.takeIf { !it.name.isNullOrEmpty() && validateAge(it.timestamp) } ?: resolveNameAndUpdate(it) }.name
        ?: did

    private suspend fun resolveNameAndUpdate(data: EntityNameResolutionData) =
        data.copy(name = nameResolutionService.resolve(data.did).getOrNull()).also { cacheService.addOrUpdate(it) }

    private fun validateAge(age: Instant?) = age?.let {
        now().minus(it).inWholeDays <= cacheAge
    } ?: false
}
