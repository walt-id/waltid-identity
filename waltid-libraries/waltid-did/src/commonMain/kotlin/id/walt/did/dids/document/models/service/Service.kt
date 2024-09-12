package id.walt.did.dids.document.models.service

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceSerializer::class)
data class Service(val serviceBlocks: List<ServiceBlock>) {

    init {
        require(serviceBlocks.isNotEmpty()) { "Service blocks cannot be empty" }
    }
}

object ServiceSerializer : KSerializer<Service> {
    private val internalSerializer = ListSerializer(ServiceBlock.serializer())
    override val descriptor: SerialDescriptor = internalSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Service) =
        encoder.encodeSerializableValue(
            ListSerializer(ServiceBlock.serializer()),
            value.serviceBlocks,
        )


    override fun deserialize(decoder: Decoder): Service =
        Service(
            decoder.decodeSerializableValue(
                ListSerializer(ServiceBlock.serializer()),
            )
        )
}

//object ServiceBaseSerializer : JsonContentPolymorphicSerializer<Service>(Service::class) {
//    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Service> {
//        return when (element) {
//            is JsonObject -> ServiceBlock.serializer()
//            is JsonArray -> ServiceBlockList.serializer()
//            else -> throw SerializationException("Invalid Service type")
//        }
//    }
//}

private val reservedKeys = listOf(
    "id",
    "type",
    "serviceEndpoint",
)

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceBlockSerializer::class)
data class ServiceBlock(
    val id: String,
    val type: ServiceType,
    val serviceEndpoint: ServiceEndpoint,
    val customProperties: Map<String, JsonElement>? = null,
) {

    init {
        require(id.isNotBlank()) { "Service property id cannot be blank" }
        customProperties?.forEach {
            require(!reservedKeys.contains(it.key)) {
                "Invalid attempt to override reserved Service property with key ${it.key} via customProperties map"
            }
        }
    }
}

object ServiceBlockSerializer : KSerializer<ServiceBlock> {
    override val descriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): ServiceBlock {
        val jsonObject = decoder.decodeSerializableValue(JsonObject.serializer())
        reservedKeys.forEach {
            require(jsonObject.contains(it))
        }
        return ServiceBlock(
            id = Json.decodeFromJsonElement(jsonObject["id"]!!),
            type = Json.decodeFromJsonElement(jsonObject["type"]!!),
            serviceEndpoint = Json.decodeFromJsonElement(jsonObject["serviceEndpoint"]!!),
            customProperties = jsonObject.filterNot { reservedKeys.contains(it.key) },
        )
    }

    override fun serialize(encoder: Encoder, value: ServiceBlock) {
        encoder.encodeSerializableValue(
            JsonObject.serializer(),
            buildJsonObject {
                put("id", value.id)
                put("type", value.type.toString())
                put("serviceEndpoint", Json.encodeToJsonElement(value.serviceEndpoint))
                value.customProperties?.forEach {
                    put(it.key, it.value)
                }
            }
        )
    }
}

//@OptIn(ExperimentalJsExport::class)
//@JsExport
//@Serializable(with = ServiceBlockListSerializer::class)
//data class ServiceBlockList(val serviceBlocks: List<ServiceBlock>) : Service() {
//
//    init {
//        require(serviceBlocks.isNotEmpty()) { "Service blocks cannot be empty" }
//    }
//}
//
//object ServiceBlockListSerializer : KSerializer<ServiceBlockList> {
//    private val internalSerializer = ListSerializer(ServiceBlock.serializer())
//    override val descriptor: SerialDescriptor = internalSerializer.descriptor
//
//    override fun serialize(encoder: Encoder, value: ServiceBlockList) =
//        encoder.encodeSerializableValue(
//            ListSerializer(ServiceBlock.serializer()),
//            value.serviceBlocks,
//        )
//
//
//    override fun deserialize(decoder: Decoder): ServiceBlockList =
//        ServiceBlockList(
//            decoder.decodeSerializableValue(
//                ListSerializer(ServiceBlock.serializer()),
//            )
//        )
//}