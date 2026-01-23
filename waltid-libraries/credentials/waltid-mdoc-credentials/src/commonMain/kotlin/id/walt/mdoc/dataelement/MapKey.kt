@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.dataelement

import cbor.internal.decoding.peek
import kotlinx.serialization.*
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

enum class MapKeyType {
    string, int
}

/**
 * Map key for CBOR map elements
 * Supports int or string keys
 */
@ConsistentCopyVisibility
@Serializable(with = MapKeySerializer::class)
data class MapKey private constructor(private val key: Any, val type: MapKeyType) {
    constructor(key: String) : this(key, MapKeyType.string)
    constructor(key: Int) : this(key, MapKeyType.int)

    val str
        get() = key as String
    val int
        get() = key as Int

    override fun equals(other: Any?): Boolean {
        return other is MapKey && other.key == key
    }

    override fun hashCode(): Int {
        return key.hashCode()
    }

    override fun toString(): String {
        return key.toString()
    }
}

@Serializer(forClass = MapKey::class)
internal object MapKeySerializer : KSerializer<MapKey> {

    override fun deserialize(decoder: Decoder): MapKey {
        val curHead = decoder.peek()
        return when (val majorType = curHead.shr(5)) {
            0, 1 -> return MapKey(decoder.decodeInt())
            3 -> return MapKey(decoder.decodeString())
            else -> throw SerializationException("Unsupported map key type: $majorType")
        }
    }

    override fun serialize(encoder: Encoder, value: MapKey) {
        when (value.type) {
            MapKeyType.string -> encoder.encodeString(value.str)
            MapKeyType.int -> encoder.encodeInt(value.int)
        }
    }
}
