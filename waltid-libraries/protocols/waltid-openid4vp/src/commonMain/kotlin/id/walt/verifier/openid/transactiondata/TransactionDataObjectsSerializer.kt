package id.walt.verifier.openid.transactiondata

import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/** JsonPrimitive's default encoder converts non-integral numbers through Double. Keep their tokens. */
@OptIn(ExperimentalSerializationApi::class)
object TransactionDataObjectsSerializer : KSerializer<List<JsonObject>> {
    override val descriptor = ListSerializer(JsonObject.serializer()).descriptor

    override fun serialize(encoder: Encoder, value: List<JsonObject>) {
        require(encoder is JsonEncoder) { "Transaction data requires JSON" }
        encoder.encodeJsonElement(JsonArray(value.map(::exactNumbers)))
    }

    override fun deserialize(decoder: Decoder): List<JsonObject> {
        require(decoder is JsonDecoder) { "Transaction data requires JSON" }
        return decoder.decodeJsonElement().jsonArray.map { it.jsonObject }
    }

    private fun exactNumbers(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.mapValues { exactNumbers(it.value) })
        is JsonArray -> JsonArray(value.map(::exactNumbers))
        is JsonPrimitive -> if (!value.isString && value != JsonNull && value.booleanOrNull == null) {
            // Reject non-JSON tokens instead of permitting raw-literal injection.
            require(JSON_NUMBER.matches(value.content)) { "Transaction data contains an invalid numeric token" }
            JsonUnquotedLiteral(value.content)
        } else value
    }

    private val JSON_NUMBER = Regex("-?(0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
}
