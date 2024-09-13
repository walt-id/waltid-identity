package id.walt.did.dids.document.models.service

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceEndpointBaseSerializer::class)
sealed class ServiceEndpoint

object ServiceEndpointBaseSerializer : JsonContentPolymorphicSerializer<ServiceEndpoint>(ServiceEndpoint::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServiceEndpoint> {
        return when {
            element is JsonPrimitive && element.isString -> ServiceEndpointURL.serializer()
            element is JsonObject -> ServiceEndpointObject.serializer()
            else -> throw SerializationException("Invalid ServiceEndpoint type")
        }
    }
}

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceEndpointURLSerializer::class)
data class ServiceEndpointURL(val url: String) : ServiceEndpoint()

object ServiceEndpointURLSerializer : KSerializer<ServiceEndpointURL> {
    override val descriptor = JsonPrimitive.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ServiceEndpointURL) =
        encoder.encodeSerializableValue(JsonElement.serializer(), value.url.toJsonElement())

    override fun deserialize(decoder: Decoder): ServiceEndpointURL =
        ServiceEndpointURL(decoder.decodeString())
}

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceEndpointObjectSerializer::class)
data class ServiceEndpointObject(val jsonObject: JsonObject) : ServiceEndpoint()

object ServiceEndpointObjectSerializer : KSerializer<ServiceEndpointObject> {
    override val descriptor = JsonObject.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ServiceEndpointObject) =
        encoder.encodeSerializableValue(JsonObject.serializer(), value.jsonObject)


    override fun deserialize(decoder: Decoder): ServiceEndpointObject =
        ServiceEndpointObject(decoder.decodeSerializableValue(JsonObject.serializer()))
}
