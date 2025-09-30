package id.walt.mdoc.objects.elements

import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.mdoc.objects.MdocsCborSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject

/**
 * A top-level container that represents the `DeviceNameSpaces` structure from the specification.
 * It maps namespace identifiers to their corresponding lists of device-signed items.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3
 */
@Serializable(with = NamespacedDeviceNameSpacesSerializer::class)
data class DeviceNameSpaces(
    val entries: Map<String, @Contextual DeviceSignedItemList>,
) {
    /**
     * A utility function to convert the structured, CBOR-oriented `entries` map into a
     * developer-friendly `JsonObject`. This is useful for application-level logic.
     */
    fun namespacesToJson() = buildJsonObject {
        entries.forEach { (namespace, deviceSignedList) ->
            putJsonObject(namespace) {
                deviceSignedList.entries.forEach { item: DeviceSignedItem ->
                    val serialized: JsonElement = MdocsCborSerializer.lookupSerializer(namespace, item.key)
                        ?.runCatching {
                            Json.encodeToJsonElement(this as KSerializer<Any?>, item.value)
                        }?.getOrElse { println("Error encoding with custom serializer: ${it.stackTraceToString()}"); null }
                        ?: item.value.toJsonElement()

                    put(item.key, serialized)
                }

            }
        }
    }
}

/**
 * A convenience class that represents the `DeviceSignedItems` map for a single namespace as a list
 * of [DeviceSignedItem] objects.
 *
 * The on-the-wire CBOR format is a map, but this class provides a `List`-based API in Kotlin.
 * The transformation is handled by the `DeviceSignedItemListSerializer`.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3
 */
data class DeviceSignedItemList(
    val entries: List<DeviceSignedItem>,
)

/**
 * Represents a single device-signed data element, corresponding to one entry in the `DeviceSignedItems` map.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (DeviceResponse CDDL)
 *
 * @property key The `DataElementIdentifier` (e.g., "family_name").
 * @property value The `DataElementValue`, which can be of any type supported by CBOR. The use of `Any`
 * reflects the `any` keyword in the CDDL specification.
 */
data class DeviceSignedItem(
    val key: String,
    val value: Any,
) {

    /**
     * Note: A custom `equals` implementation is required because the `value` property is of type `Any`
     * and may contain arrays. The default `equals` for `data class` would use reference equality
     * for arrays, leading to incorrect comparisons. This implementation correctly uses content-based
     * equality for all array types.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DeviceSignedItem) return false
        if (key != other.key) return false

        val otherValue = other.value
        return when (value) {
            is ByteArray -> otherValue is ByteArray && value.contentEquals(otherValue)
            is IntArray -> otherValue is IntArray && value.contentEquals(otherValue)
            is BooleanArray -> otherValue is BooleanArray && value.contentEquals(otherValue)
            is CharArray -> otherValue is CharArray && value.contentEquals(otherValue)
            is ShortArray -> otherValue is ShortArray && value.contentEquals(otherValue)
            is LongArray -> otherValue is LongArray && value.contentEquals(otherValue)
            is FloatArray -> otherValue is FloatArray && value.contentEquals(otherValue)
            is DoubleArray -> otherValue is DoubleArray && value.contentEquals(otherValue)
            is Array<*> -> otherValue is Array<*> && value.contentDeepEquals(otherValue)
            else -> value == otherValue
        }
    }

    /**
     * Note: A custom `hashCode` implementation is required to match the custom `equals` logic.
     * The default `hashCode` would be incorrect for arrays. This implementation correctly uses
     * content-based hashing for all array types.
     */
    override fun hashCode(): Int {
        val valueHash = when (value) {
            is ByteArray -> value.contentHashCode()
            is IntArray -> value.contentHashCode()
            is BooleanArray -> value.contentHashCode()
            is CharArray -> value.contentHashCode()
            is ShortArray -> value.contentHashCode()
            is LongArray -> value.contentHashCode()
            is FloatArray -> value.contentHashCode()
            is DoubleArray -> value.contentHashCode()
            is Array<*> -> value.contentDeepHashCode()
            else -> value.hashCode()
        }
        return 31 * key.hashCode() + valueHash
    }
}
