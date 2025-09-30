@file:OptIn(ExperimentalTime::class)

package id.walt.mdoc.dataelement.json

import id.walt.mdoc.dataelement.*
import kotlinx.serialization.json.*
import kotlin.time.ExperimentalTime

fun JsonElement.toDataElement(): AnyDataElement = when (this) {
    is JsonObject -> this.mapValues { it.value.toDataElement() }.toDataElement()
    is JsonArray -> this.map { it.toDataElement() }.toDataElement()
    is JsonNull -> NullElement()
    is JsonPrimitive -> {
        when {
            this.isString -> StringElement(this.content)

            this.booleanOrNull != null -> BooleanElement(this.boolean)

            (this.intOrNull ?: this.longOrNull ?: this.floatOrNull ?: this.doubleOrNull) != null  -> {
                NumberElement((this.intOrNull ?: this.longOrNull ?: this.floatOrNull ?: this.double))
            }

            else -> {
                NullElement()
            }
        }
    }

}

fun DataElement.toJsonElement(): JsonElement = when (this) {
    is NumberElement -> JsonPrimitive(this.value)
    is StringElement -> JsonPrimitive(this.value)
    is BooleanElement -> JsonPrimitive(this.value)
    is ByteStringElement -> JsonArray(this.value.map { JsonPrimitive(it) })
    is ListElement -> JsonArray(this.value.map { it.toJsonElement() })
    is MapElement -> JsonObject(this.value.mapKeys { it.key.toString() }.mapValues { it.value.toJsonElement() })
    is NullElement -> JsonNull
    is DateTimeElement -> JsonPrimitive(this.value.epochSeconds)
    is FullDateElement -> JsonPrimitive(this.value.toEpochDays() * 24 * 60 * 60)
    is EncodedCBORElement -> this.decode().toJsonElement()
    else -> throw Exception("Unsupported data type")
}

fun DataElement.toUIJson(): JsonElement = when (this) {
    is MapElement -> buildJsonObject {
        this@toUIJson.value.forEach { (key, value) ->
            when(key.type) {
                MapKeyType.int -> put(key.int.toString(), value.toUIJson())
                MapKeyType.string -> put(key.str, value.toUIJson())
            }
        }
    }

    is EncodedCBORElement -> this.decode().toUIJson()
    else -> this.toJsonElement()
}
