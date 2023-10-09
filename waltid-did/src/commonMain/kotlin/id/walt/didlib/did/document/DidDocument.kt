package id.walt.didlib.did.document

import id.walt.didlib.utils.JsonUtils.printAsJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

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
    constructor(jsonObject: JsonObject) : this(jsonObject.toMap())

    /**
     * To JsonObject
     */
    fun toJsonObject() = JsonObject(content)

}
