@file:OptIn(ExperimentalUuidApi::class)

package id.walt.corewallet.usecase.exchange

import id.walt.corewallet.utils.WalletCredential
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.corewallet.usecase.exchange.strategies.PresentationDefinitionMatchStrategy
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
