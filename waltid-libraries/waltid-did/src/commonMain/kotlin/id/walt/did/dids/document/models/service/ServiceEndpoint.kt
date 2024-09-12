package id.walt.did.dids.document.models.service

import kotlinx.serialization.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
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
            element is JsonArray -> ServiceEndpointList.serializer()
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
        encoder.encodeString(value.url)

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

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceEndpointListSerializer::class)
data class ServiceEndpointList(val endpoints: List<ServiceEndpoint>) : ServiceEndpoint() {
    init {
        require(endpoints.isNotEmpty()) { "At least one service endpoint is required" }
    }
}

object ServiceEndpointListSerializer : KSerializer<ServiceEndpointList> {
    private val internalSerializer = ListSerializer(ServiceEndpoint.serializer())
    override val descriptor: SerialDescriptor = internalSerializer.descriptor

    override fun serialize(encoder: Encoder, value: ServiceEndpointList) {
        when (value.endpoints.size) {
            1 -> {
                encoder.encodeSerializableValue(
                    ServiceEndpoint.serializer(),
                    value.endpoints.first(),
                )
            }

            else -> {
                encoder.encodeSerializableValue(
                    ListSerializer(ServiceEndpoint.serializer()),
                    value.endpoints,
                )
            }
        }
    }


    override fun deserialize(decoder: Decoder): ServiceEndpointList =
        ServiceEndpointList(
            decoder.decodeSerializableValue(
                ListSerializer(ServiceEndpoint.serializer()),
            )
        )
}
