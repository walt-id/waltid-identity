package id.walt.did.dids.document.models.service

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceSerializer::class)
data class Service(val serviceBlocks: Set<ServiceBlock>)

object ServiceSerializer : KSerializer<Service> {
    private val internalSerializer = SetSerializer(ServiceBlock.serializer())
    override val descriptor: SerialDescriptor = internalSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Service) =
        encoder.encodeSerializableValue(
            SetSerializer(ServiceBlock.serializer()),
            value.serviceBlocks,
        )


    override fun deserialize(decoder: Decoder): Service =
        Service(
            decoder.decodeSerializableValue(
                SetSerializer(ServiceBlock.serializer()),
            )
        )
}

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
    val type: Set<String>,
    val serviceEndpoint: Set<ServiceEndpoint>,
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
            type = jsonObject["type"]!!.let { element ->
                when {
                    element is JsonPrimitive && element.isString -> {
                        setOf(Json.decodeFromJsonElement<String>(element))
                    }
                    else -> {
                        Json.decodeFromJsonElement<Set<String>>(element)
                    }
                }

            },
            serviceEndpoint = jsonObject["serviceEndpoint"]!!.let { element ->
                when {
                    (element is JsonPrimitive && element.isString) ||
                            (element is JsonObject) -> {
                        setOf(Json.decodeFromJsonElement<ServiceEndpoint>(element))
                    }

                    else -> {
                        Json.decodeFromJsonElement<Set<ServiceEndpoint>>(element)
                    }
                }
            },
            customProperties = jsonObject.filterNot { reservedKeys.contains(it.key) }.let {
                it.ifEmpty { null }
            },
        )
    }

    override fun serialize(encoder: Encoder, value: ServiceBlock) {
        encoder.encodeSerializableValue(
            JsonObject.serializer(),
            buildJsonObject {
                put("id", value.id)
                when (value.type.size) {
                    1 -> {
                        put("type", value.type.first().toString())
                    }

                    else -> {
                        put("type", Json.encodeToJsonElement(value.type))
                    }
                }
                when (value.serviceEndpoint.size) {
                    1 -> {
                        put("serviceEndpoint", Json.encodeToJsonElement(value.serviceEndpoint.first()))
                    }

                    else -> {
                        put("serviceEndpoint", Json.encodeToJsonElement(value.serviceEndpoint))
                    }
                }
                value.customProperties?.forEach {
                    put(it.key, it.value)
                }
            }
        )
    }
}
