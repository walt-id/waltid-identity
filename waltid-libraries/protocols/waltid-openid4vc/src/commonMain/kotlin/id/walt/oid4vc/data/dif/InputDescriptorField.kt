package id.walt.oid4vc.data.dif

import id.walt.mdoc.docrequest.MDocRequestBuilder
import id.walt.oid4vc.data.JsonDataObject
import id.walt.oid4vc.data.JsonDataObjectSerializer
import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@OptIn(ExperimentalSerializationApi::class)
@KeepGeneratedSerializer
@Serializable(with = InputDescriptorFieldSerializer::class)
data class InputDescriptorField(
    val path: List<String>,
    val id: String? = null,
    val purpose: String? = null,
    val name: String? = null,
    val filter: JsonObject? = null,
    val optional: Boolean? = null,
    @SerialName("intent_to_retain") val intentToRetain: Boolean? = null,
    override val customParameters: Map<String, JsonElement>? = mapOf()
) : JsonDataObject() {
    override fun toJSON() = Json.encodeToJsonElement(InputDescriptorFieldSerializer, this).jsonObject

    fun addToMdocRequest(mDocRequestBuilder: MDocRequestBuilder, intentToRetain: Boolean = false): MDocRequestBuilder {
        path.firstOrNull()?.trimStart('$')?.replace("['", "")?.replace("']", ".")?.trimEnd('.')
            ?.let { it.substringBeforeLast('.') to it.substringAfterLast('.') }
            ?.also { (namespace, lastSegment) ->
                mDocRequestBuilder.addDataElementRequest(namespace, lastSegment, intentToRetain)
            }
        return mDocRequestBuilder
    }
}

internal object InputDescriptorFieldSerializer :
    JsonDataObjectSerializer<InputDescriptorField>(InputDescriptorField.generatedSerializer())

internal object InputDescriptorFieldListSerializer : KSerializer<List<InputDescriptorField>> {
    private val internalSerializer = ListSerializer(InputDescriptorFieldSerializer)
    override val descriptor = internalSerializer.descriptor
    override fun deserialize(decoder: Decoder) = internalSerializer.deserialize(decoder)
    override fun serialize(encoder: Encoder, value: List<InputDescriptorField>) =
        internalSerializer.serialize(encoder, value)
}
