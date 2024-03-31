package id.walt.webwallet.usecase.exchange.strategies

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.TypeFilter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

abstract class BaseFilterPresentationDefinitionMatchStrategy<T> : PresentationDefinitionMatchStrategy<T> {

    protected fun isMatching(credential: WalletCredential, fields: List<TypeFilter>) = fields.all { typeFilter ->
        val credField = credential.parsedDocument!![typeFilter.path] ?: return@all false
        when (credField) {
            is JsonPrimitive -> credField.jsonPrimitive.content == typeFilter.pattern
            is JsonArray -> credField.jsonArray.last().jsonPrimitive.content == typeFilter.pattern

            else -> false
        }
    }
}