package id.walt.cli.presexch.strategies

import id.walt.cli.models.Credential
import id.walt.cli.presexch.PresentationDefinitionFilterParser
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.cli.presexch.FilterData

class FilterPresentationDefinitionMatchStrategy(
    private val filterParser: PresentationDefinitionFilterParser,
) : BaseFilterPresentationDefinitionMatchStrategy<List<Credential>>() {

    override fun match(
        credentials: List<Credential>, presentationDefinition: PresentationDefinition,
    ): List<Credential> = match(credentials, filterParser.parse(presentationDefinition))

    private fun match(
        credentialList: List<Credential>, filters: List<FilterData>,
    ) = filters.isNotEmpty().takeIf { it }?.let {
        credentialList.filter { credential ->
            filters.any { fields ->
                isMatching(credential, fields.filters)
            }
        }
    } ?: emptyList()
}