package id.walt.did.dids.document

import id.walt.crypto.utils.JsonUtils.printAsJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.js.JsName

@ExperimentalJsExport
@JsExport
@Serializable
class DidDocument(
    private val content: Map<String, JsonElement>
) : Map<String, JsonElement> by content {
    override fun equals(other: Any?): Boolean = content == other
    override fun hashCode(): Int = content.hashCode()
    override fun toString(): String = content.printAsJson()

    /**
     * From JsonObject
     */
    @JsName("secondaryConstructor")
    constructor(jsonObject: JsonObject) : this(jsonObject.toMap())

    /**
     * To JsonObject
     */
    fun toJsonObject() = JsonObject(content)

}
