package id.walt.mdoc.dataelement.json

import id.walt.mdoc.dataelement.AnyDataElement
import id.walt.mdoc.dataelement.BooleanElement
import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.DateTimeElement
import id.walt.mdoc.dataelement.EncodedCBORElement
import id.walt.mdoc.dataelement.FullDateElement
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKeyType
import id.walt.mdoc.dataelement.NullElement
import id.walt.mdoc.dataelement.NumberElement
import id.walt.mdoc.dataelement.StringElement
import id.walt.mdoc.dataelement.toDataElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlin.collections.component1
import kotlin.collections.component2

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