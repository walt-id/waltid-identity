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

@OptIn(ExperimentalJsExport::class)
@JsExport
open class DidCreateOptions(val method: String, val config: JsonElement) {

    @JsName("secondaryConstructor")
    constructor(method: String, config: Map<String, Any?>) : this(method, config.toJsonElement())

    @JsExport.Ignore
    inline operator fun <reified T> get(name: String): T? =
        config.jsonObject["config"]?.jsonObject?.get(name)?.jsonPrimitive?.content?.let {
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

@OptIn(ExperimentalJsExport::class)
@JsExport
internal fun config(config: Map<String, Any>, secret: Map<String, Any> = emptyMap()) = mapOf(
    "config" to config,
    "didDocument" to mapOf(
        "@context" to "https://www.w3.org/ns/did/v1",
        "authentication" to emptyList<Any>(),
        "service" to emptyList<Any>()
    ),
    "secret" to secret
)

@OptIn(ExperimentalJsExport::class)
@JsExport
internal fun config(vararg inlineConfig: Pair<String, Any>) = config(mapOf(*inlineConfig))
