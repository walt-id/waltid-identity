package id.walt.webwallet.usecase.exchange

import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.service.credentials.CredentialFilterObject
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.usecase.exchange.strategies.PresentationDefinitionMatchStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.jsonArray
import kotlinx.uuid.UUID

class NoMatchPresentationDefinitionCredentialsUseCase(
    private val credentialService: CredentialsService,
    private vararg val matchStrategies: PresentationDefinitionMatchStrategy<List<TypeFilter>>
) {
    private val logger = KotlinLogging.logger { }

    fun find(wallet: UUID, presentationDefinition: PresentationDefinition): List<TypeFilter> {
        val credentialList = credentialService.list(wallet, CredentialFilterObject.default)
        logger.debug { "WalletCredential list is: ${credentialList.map { it.parsedDocument?.get("type")!!.jsonArray }}" }
        return matchStrategies.fold<PresentationDefinitionMatchStrategy<List<TypeFilter>>, List<TypeFilter>>(listOf()) { acc, i ->
            acc.plus(i.match(credentialList, presentationDefinition))
        }.distinct()
    }
}