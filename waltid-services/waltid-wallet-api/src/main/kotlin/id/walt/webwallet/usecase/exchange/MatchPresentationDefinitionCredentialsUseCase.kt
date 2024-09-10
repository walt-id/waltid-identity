@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.usecase.exchange

import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.usecase.exchange.strategies.PresentationDefinitionMatchStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.jsonArray
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


class MatchPresentationDefinitionCredentialsUseCase(
    private val credentialService: CredentialsService,
    private vararg val matchStrategies: PresentationDefinitionMatchStrategy<List<WalletCredential>>,
) {
    private val logger = KotlinLogging.logger { }

    fun match(
        wallet: Uuid, presentationDefinition: PresentationDefinition,
    ): List<WalletCredential> {
        val credentialList = credentialService.list(wallet, CredentialFilterObject.default)
        logger.debug { "WalletCredential list is: ${credentialList.map { it.parsedDocument?.get("type")!!.jsonArray }}" }

        var matchedCredentials = emptyList<WalletCredential>()
        run loop@{
            matchStrategies.forEach {
                matchedCredentials = it.match(credentialList, presentationDefinition)
                if (matchedCredentials.isNotEmpty()) return@loop
            }
        }

        logger.debug { "Matched credentials: $matchedCredentials" }

        return matchedCredentials
    }
}
