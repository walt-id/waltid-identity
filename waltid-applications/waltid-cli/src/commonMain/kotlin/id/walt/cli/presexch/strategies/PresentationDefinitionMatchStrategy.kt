package id.walt.cli.presexch.strategies

import id.walt.cli.models.Credential
import id.walt.oid4vc.data.dif.PresentationDefinition

interface PresentationDefinitionMatchStrategy<out T> {
    fun match(
        credentials: List<Credential>, presentationDefinition: PresentationDefinition,
    ): T
}