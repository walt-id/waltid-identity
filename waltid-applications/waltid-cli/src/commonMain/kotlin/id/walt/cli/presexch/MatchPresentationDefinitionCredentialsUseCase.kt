package id.walt.cli.presexch

import id.walt.cli.models.Credential
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.cli.presexch.strategies.PresentationDefinitionMatchStrategy

class MatchPresentationDefinitionCredentialsUseCase(
    private val credentialList: List<Credential>,
    private val matchStrategies: List<PresentationDefinitionMatchStrategy<List<Credential>>>,
) {
    fun match(
        presentationDefinition: PresentationDefinition,
    ): List<Credential> {

        var matchedCredentials = emptyList<Credential>()
        run loop@{
            matchStrategies.forEach {
                matchedCredentials = it.match(credentialList, presentationDefinition)
                if (matchedCredentials.isNotEmpty()) return@loop
            }
        }
        return matchedCredentials
    }
}