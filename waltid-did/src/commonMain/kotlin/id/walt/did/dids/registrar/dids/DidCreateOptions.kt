package id.walt.did.dids.registrar.dids

import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.utils.EnumUtils.enumValueIgnoreCase
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@ExperimentalJsExport
@JsExport
open class DidCreateOptions(val method: String, val options: JsonElement) {

    @JsName("secondaryConstructor")
    constructor(method: String, options: Map<String, Any?>) : this(method, options.toJsonElement())

    @JsExport.Ignore
    inline operator fun <reified T> get(name: String): T? =
        options.jsonObject["options"]?.jsonObject?.get(name)?.jsonPrimitive?.content?.let {
            when (T::class) {
                Boolean::class -> it.toBoolean()
                Int::class -> it.toIntOrNull()
                Long::class -> it.toLongOrNull()
                Double::class -> it.toDoubleOrNull()
                KeyType::class -> enumValueIgnoreCase<KeyType>(it)
                String::class -> it
                else -> null
            } as? T
        }
}

@ExperimentalJsExport
@JsExport
internal fun options(options: Map<String, Any>, secret: Map<String, Any> = emptyMap()) = mapOf(
    "options" to options,
    "didDocument" to mapOf(
        "@context" to "https://www.w3.org/ns/did/v1",
        "authentication" to emptyList<Any>(),
        "service" to emptyList<Any>()
    ),
    "secret" to secret
)

@ExperimentalJsExport
@JsExport
internal fun options(vararg inlineOptions: Pair<String, Any>) = options(mapOf(*inlineOptions))
