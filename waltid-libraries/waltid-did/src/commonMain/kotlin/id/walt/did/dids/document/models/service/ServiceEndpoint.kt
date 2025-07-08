package id.walt.did.dids.document.models.service

import id.walt.crypto.utils.JsonUtils.toJsonElement
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Base class for representing a service endpoint according to the respective section of the [DID Core](https://www.w3.org/TR/did-core/#dfn-serviceendpoint) specification.
 * This can be either a URL (refer to [ServiceEndpointURL] ) or an arbitrary JSON object (refer to [ServiceEndpointObject]).
 * @see ServiceEndpointURL
 * @see ServiceEndpointObject
 * */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceEndpointBaseSerializer::class)
sealed class ServiceEndpoint

object ServiceEndpointBaseSerializer : JsonContentPolymorphicSerializer<ServiceEndpoint>(ServiceEndpoint::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<ServiceEndpoint> {
        return when {
            element is JsonPrimitive && element.isString -> ServiceEndpointURL.serializer()
            element is JsonObject -> ServiceEndpointObject.serializer()
            else -> throw SerializationException("Invalid ServiceEndpoint encoded value, must be either a string or an object")
        }
    }
}

/**
 * Service endpoint represented as a single URL.
 * @property url The URL of the service endpoint (cannot be empty)
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@Serializable(with = ServiceEndpointURLSerializer::class)
data class ServiceEndpointURL(val url: String) : ServiceEndpoint() {

    init {
        require(url.isNotBlank()) { "Service endpoint URL cannot be blank." }
    }
}

object ServiceEndpointURLSerializer : KSerializer<ServiceEndpointURL> {
    override val descriptor = JsonPrimitive.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ServiceEndpointURL) =
        encoder.encodeSerializableValue(JsonElement.serializer(), value.url.toJsonElement())

    override fun deserialize(decoder: Decoder): ServiceEndpointURL =
        ServiceEndpointURL(decoder.decodeString())
}

/**
 * Service endpoint represented as a JSON object.
 * @property jsonObject User-defined JSON object representing a service endpoint
 */
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
