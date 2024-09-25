package id.walt.did.dids.document.models.service

import id.walt.commons.exceptions.EmptyServiceEndpointException
import id.walt.commons.exceptions.InvalidServiceIdException
import id.walt.commons.exceptions.InvalidServiceTypeException
import id.walt.commons.exceptions.ReservedKeyOverrideException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Class for representing the service property of a DID document according to the respective section of the [DID Core](https://www.w3.org/TR/did-core/#services) specification
 * @property serviceMaps A set of [ServiceMap] object instances.
 * @see ServiceMap
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceSerializer::class)
data class Service(val serviceMaps: Set<ServiceMap>)

object ServiceSerializer : KSerializer<Service> {
    private val internalSerializer = SetSerializer(ServiceMap.serializer())
    override val descriptor: SerialDescriptor = internalSerializer.descriptor

    override fun serialize(encoder: Encoder, value: Service) =
        encoder.encodeSerializableValue(
            SetSerializer(ServiceMap.serializer()),
            value.serviceMaps,
        )


    override fun deserialize(decoder: Decoder): Service =
        Service(
            decoder.decodeSerializableValue(
                SetSerializer(ServiceMap.serializer()),
            )
        )
}

private val reservedKeys = listOf(
    "id",
    "type",
    "serviceEndpoint",
)

/**
 * Class for representing a service map of a DID Document's service property according to the respective section of the [DID Core](https://www.w3.org/TR/did-core/#services:~:text=is%20described%20below%3A-,service,-The%20service%20property) specification
 * @property id The identifier of this [ServiceMap] instance (cannot be empty).
 * @property type A set of (non-empty) strings that reflect the type(s) of this [ServiceMap]. Use of the [RegisteredServiceType] entries is recommended for the sake of interoperability, however, users are free to introduce their own custom types.
 * @property serviceEndpoint A set of [ServiceEndpoint] instances with which this [ServiceMap] instance is associated.
 * @property customProperties Optional user-defined custom properties that can be included in this [ServiceMap] instance.
 * @see ServiceEndpoint
 * @see RegisteredServiceType
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceMapSerializer::class)
data class ServiceMap(
    val id: String,
    val type: Set<String>,
    val serviceEndpoint: Set<ServiceEndpoint>,
    val customProperties: Map<String, JsonElement>? = null,
) {

    init {
        require(id.isNotBlank()) { throw InvalidServiceIdException("Service property id cannot be blank") }
        type.forEach {
            require(it.isNotBlank()) { throw InvalidServiceTypeException("Service type strings cannot be blank") }
        }
        require(serviceEndpoint.isNotEmpty()) { throw EmptyServiceEndpointException("Service endpoint set cannot be empty") }
        customProperties?.forEach {
            require(!reservedKeys.contains(it.key)) {
                throw ReservedKeyOverrideException("Invalid attempt to override reserved Service property with key ${it.key} via customProperties map")
            }
        }
    }
}

object ServiceMapSerializer : KSerializer<ServiceMap> {
    override val descriptor = JsonObject.serializer().descriptor

    override fun deserialize(decoder: Decoder): ServiceMap {
        val jsonObject = decoder.decodeSerializableValue(JsonObject.serializer())
        reservedKeys.forEach {
            require(jsonObject.contains(it))
        }
        return ServiceMap(
            id = Json.decodeFromJsonElement(jsonObject["id"]!!),
            type = getType(jsonObject["type"]!!),
            serviceEndpoint = getServiceEndpoint(jsonObject["serviceEndpoint"]!!),
            customProperties = getCustomProperties(jsonObject),
        )
    }

    private fun getType(element: JsonElement) = when {
        element is JsonPrimitive && element.isString -> {
            setOf(Json.decodeFromJsonElement<String>(element))
        }

        else -> {
            Json.decodeFromJsonElement<Set<String>>(element)
        }
    }

    private fun getServiceEndpoint(element: JsonElement) = when {
        (element is JsonPrimitive && element.isString) ||
                (element is JsonObject) -> {
            setOf(Json.decodeFromJsonElement<ServiceEndpoint>(element))
        }

        else -> {
            Json.decodeFromJsonElement<Set<ServiceEndpoint>>(element)
        }
    }

    private fun getCustomProperties(jsonObject: JsonObject) = jsonObject
        .filterNot {
            reservedKeys.contains(it.key)
        }.let {
            it.ifEmpty { null }
        }

    override fun serialize(encoder: Encoder, value: ServiceMap) {
        encoder.encodeSerializableValue(
            JsonObject.serializer(),
            buildJsonObject {
                put("id", value.id)
                putType(value)
                putEndpoint(value.serviceEndpoint)
                putCustomProperties(value.customProperties)
            }
        )
    }

    private fun JsonObjectBuilder.putType(value: ServiceMap) =
        when (value.type.size) {
            1 -> {
                Json.encodeToJsonElement(value.type.first())
            }

            else -> {
                Json.encodeToJsonElement(value.type)
            }
        }.let {
            put("type", it)
        }

    private fun JsonObjectBuilder.putEndpoint(value: Set<ServiceEndpoint>) =
        when (value.size) {
            1 -> {
                Json.encodeToJsonElement(value.first())
            }

            else -> {
                Json.encodeToJsonElement(value)
            }
        }.let {
            put("serviceEndpoint", it)
        }

    private fun JsonObjectBuilder.putCustomProperties(value: Map<String, JsonElement>?) = value?.forEach {
        put(it.key, it.value)
    }
}
