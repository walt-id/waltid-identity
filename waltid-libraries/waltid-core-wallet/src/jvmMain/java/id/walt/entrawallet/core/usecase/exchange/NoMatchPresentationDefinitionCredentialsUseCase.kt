@file:OptIn(ExperimentalUuidApi::class)

package id.walt.corewallet.usecase.exchange

import id.walt.corewallet.utils.WalletCredential
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.corewallet.usecase.exchange.strategies.PresentationDefinitionMatchStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.ExperimentalUuidApi


class NoMatchPresentationDefinitionCredentialsUseCase(
    private vararg val matchStrategies: PresentationDefinitionMatchStrategy<List<FilterData>>,
) {
    private val logger = KotlinLogging.logger { }

    fun find(credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition): List<FilterData> {
        return matchStrategies.fold<PresentationDefinitionMatchStrategy<List<FilterData>>, List<FilterData>>(listOf()) { acc, i ->
            acc.plus(i.match(credentials, presentationDefinition))
        }.groupBy {
            it.credential
        }.map {
            FilterData(
                credential = it.key,
                filters = it.value.map { it.filters }.flatten()
            )
        }
    }
}
