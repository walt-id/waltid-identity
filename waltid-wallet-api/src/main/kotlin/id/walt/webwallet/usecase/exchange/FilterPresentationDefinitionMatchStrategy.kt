package id.walt.webwallet.usecase.exchange

import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.webwallet.db.models.WalletCredential
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class FilterPresentationDefinitionMatchStrategy(
    private val filterParser: PresentationDefinitionFilterParser,
) : PresentationDefinitionMatchStrategy {
    override fun match(
        credentials: List<WalletCredential>, presentationDefinition: PresentationDefinition
    ): List<WalletCredential> = match(credentials, filterParser.parse(presentationDefinition))

    private fun match(
        credentialList: List<WalletCredential>, filters: List<List<TypeFilter>>
    ) = credentialList.filter { credential ->
        filters.any { fields ->
            fields.all { typeFilter ->
                val credField = credential.parsedDocument!![typeFilter.path] ?: return@all false

                when (credField) {
                    is JsonPrimitive -> credField.jsonPrimitive.content == typeFilter.pattern
                    is JsonArray -> credField.jsonArray.last().jsonPrimitive.content == typeFilter.pattern

                    else -> false
                }
            }
        }
    }
}