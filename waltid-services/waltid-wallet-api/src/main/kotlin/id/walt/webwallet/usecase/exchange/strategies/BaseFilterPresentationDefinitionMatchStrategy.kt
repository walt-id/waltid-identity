package id.walt.webwallet.usecase.exchange.strategies

import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.TypeFilter
import id.walt.webwallet.utils.JsonUtils
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

abstract class BaseFilterPresentationDefinitionMatchStrategy<T> : PresentationDefinitionMatchStrategy<T> {
    private val modifiersRegex = """(\$/[gmixsuXUAJn]*)""".toRegex()
    protected fun isMatching(credential: WalletCredential, fields: List<TypeFilter>) = fields.all { typeFilter ->
        typeFilter.path.mapNotNull {
            JsonUtils.tryGetData(credential.parsedDocument, it)?.let {
                when (it) {
                    is JsonPrimitive -> it.jsonPrimitive.content
                    is JsonArray -> it.jsonArray.last().jsonPrimitive.content
                    else -> ""
                }
            }
        }.any {
            modifiersRegex.replace(typeFilter.pattern.removePrefix("/"), """\$""").toRegex().matches(it)
        }
    }
}