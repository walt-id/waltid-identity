@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.mdoc.encoding

import id.walt.cose.coseCompliantCbor
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.cbor.CborByteString
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

internal fun <T> T.toCborElement(serializer: KSerializer<T>): CborElement =
    coseCompliantCbor.decodeFromByteArray(
        CborElement.serializer(),
        coseCompliantCbor.encodeToByteArray(serializer, this),
    )

internal fun <T> CborElement.fromCborElement(serializer: KSerializer<T>): T =
    coseCompliantCbor.decodeFromByteArray(
        serializer,
        coseCompliantCbor.encodeToByteArray(CborElement.serializer(), this),
    )

internal fun Decoder.decodeTextMap(structureName: String): Map<String, CborElement> {
    val map = decodeSerializableValue(CborElement.serializer()) as? CborMap
        ?: throw SerializationException("$structureName must be a CBOR map")
    return map.entries.associate { (key, value) ->
        ((key as? CborString)?.value
            ?: throw SerializationException("$structureName keys must be text")) to value
    }
}

internal fun Encoder.encodeTextMap(fields: Map<String, CborElement>) =
    encodeSerializableValue(
        CborElement.serializer(),
        CborMap(fields.mapKeysTo(linkedMapOf()) { (key, _) -> CborString(key) }),
    )

internal fun Map<String, CborElement>.extensionsExcluding(knownFields: Set<String>): Map<String, CborElement> =
    filterKeys { it !in knownFields }

internal fun <T> ByteStringWrapper<T>.toTaggedByteString(serializer: KSerializer<T>): CborByteString =
    CborByteString(
        serialized.takeIf(ByteArray::isNotEmpty) ?: coseCompliantCbor.encodeToByteArray(serializer, value),
        24u,
    )

internal fun <T> CborElement.fromTaggedByteString(
    serializer: KSerializer<T>,
    fieldName: String,
): ByteStringWrapper<T> {
    val encoded = (this as? CborByteString)?.also {
        if (24uL !in it.tags) throw SerializationException("$fieldName must use CBOR tag 24")
    }?.toByteArray() ?: throw SerializationException("$fieldName must be a byte string")
    return ByteStringWrapper(
        value = coseCompliantCbor.decodeFromByteArray(serializer, encoded),
        serialized = encoded,
    )
}

internal fun requireNoExtensionCollisions(
    extensions: Map<String, CborElement>,
    knownFields: Set<String>,
    structureName: String,
) {
    require(extensions.keys.none { it in knownFields }) {
        "$structureName extension collides with a standard field"
    }
}
