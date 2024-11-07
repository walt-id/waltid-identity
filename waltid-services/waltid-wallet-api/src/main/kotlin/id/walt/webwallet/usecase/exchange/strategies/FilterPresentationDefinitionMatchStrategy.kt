package id.walt.webwallet.usecase.exchange.strategies

import id.walt.definitionparser.PresentationDefinitionParser
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.PresentationDefinitionFilterParser
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import id.walt.definitionparser.PresentationDefinition as PdParserPresentationDefinition
import id.walt.oid4vc.data.dif.PresentationDefinition as Oid4vcLibPresentationDefinition

class FilterPresentationDefinitionMatchStrategy(
    private val filterParser: PresentationDefinitionFilterParser,
) : BaseFilterPresentationDefinitionMatchStrategy<List<WalletCredential>>() {

    override suspend fun match(
        credentials: List<WalletCredential>, presentationDefinition: Oid4vcLibPresentationDefinition,
    ): List<WalletCredential> {
        val credentialsToMatch = credentials.asFlow().mapNotNull { it.parsedDocument } // TODO: Are there credentials without parsed document?
        val matched = presentationDefinition.inputDescriptors.map { oidLibInputDescriptor ->
            val pdParserInputDescriptor: PdParserPresentationDefinition.InputDescriptor =
                PdParserPresentationDefinition.InputDescriptor(
                    id = oidLibInputDescriptor.id,
                    name = oidLibInputDescriptor.name,
                    purpose = oidLibInputDescriptor.purpose,
                    format = Json.encodeToJsonElement(oidLibInputDescriptor.format),
                    group = oidLibInputDescriptor.group?.toList(),
                    constraints = Json.decodeFromJsonElement(Json.encodeToJsonElement(oidLibInputDescriptor.constraints))
                )

            PresentationDefinitionParser.matchCredentialsForInputDescriptor(credentialsToMatch, pdParserInputDescriptor)
        }
        val allMatched = matched.flatMap { it.toList() }.distinct()
        val walletCredentialsMatched = credentials.filter { it.parsedDocument in allMatched }

        return walletCredentialsMatched
    }
}
