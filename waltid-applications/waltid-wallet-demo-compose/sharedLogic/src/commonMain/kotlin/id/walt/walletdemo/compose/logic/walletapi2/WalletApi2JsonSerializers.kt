package id.walt.walletdemo.compose.logic.walletapi2

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Wasm/JS cannot look up the internal JsonLiteral serializer. Encode and decode
 * JSON trees through the JsonEncoder/JsonDecoder APIs instead.
 */
internal object JsonObjectKtorSerializer : KSerializer<JsonObject> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: JsonObject) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("JsonObject can only be serialized as JSON")
        jsonEncoder.encodeJsonElement(value)
    }

    override fun deserialize(decoder: Decoder): JsonObject {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("JsonObject can only be deserialized from JSON")
        return jsonDecoder.decodeJsonElement().jsonObject
    }
}

internal object JsonElementKtorSerializer : KSerializer<JsonElement> {
    override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

    override fun serialize(encoder: Encoder, value: JsonElement) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: error("JsonElement can only be serialized as JSON")
        jsonEncoder.encodeJsonElement(value)
    }

    override fun deserialize(decoder: Decoder): JsonElement {
        val jsonDecoder = decoder as? JsonDecoder
            ?: error("JsonElement can only be deserialized from JSON")
        return jsonDecoder.decodeJsonElement()
    }
}
