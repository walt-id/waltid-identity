@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.exchange

import id.walt.entrawallet.core.utils.WalletCredential
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.usecase.exchange.strategies.PresentationDefinitionMatchStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.uuid.ExperimentalUuidApi


class MatchPresentationDefinitionCredentialsUseCase(
    private vararg val matchStrategies: PresentationDefinitionMatchStrategy<List<WalletCredential>>,
) {
    private val logger = KotlinLogging.logger { }

    fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition,
    ): List<WalletCredential> {
        var matchedCredentials = emptyList<WalletCredential>()
        run loop@{
            matchStrategies.forEach {
                matchedCredentials = it.match(credentials, presentationDefinition)
                if (matchedCredentials.isNotEmpty()) return@loop
            }
        }

        logger.debug { "Matched credentials: $matchedCredentials" }

        return matchedCredentials
    }
}
