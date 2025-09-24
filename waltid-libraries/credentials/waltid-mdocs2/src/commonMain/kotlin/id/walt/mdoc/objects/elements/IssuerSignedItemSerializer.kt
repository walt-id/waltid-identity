@file:OptIn(ExperimentalTime::class)

package id.walt.mdoc.objects.elements

import id.walt.mdoc.objects.MdocsCborSerializer
import id.walt.mdoc.credsdata.CredentialManager
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.cbor.ValueTags
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.*
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A custom serializer for the [IssuerSignedItem] class.
 *
 * This serializer is necessary because the `elementValue` property is of type `Any`,
 * meaning its concrete type is only known at runtime. This serializer dynamically looks up the
 * appropriate `KSerializer` for the `elementValue` based on its `namespace` and `elementIdentifier`
 * using a central `CborCredentialSerializer`.
 *
 * It also correctly applies CBOR tags for date and time types as required by RFC 8943 and RFC 8949.
 *
 * @see ISO/IEC 18013-5:xxxx(E), 8.3.2.1.2.3 (for the IssuerSignedItem structure)
 * @see RFC 8943 (for CBOR tag 1004 for "full-date")
 * @see RFC 8949 (for CBOR tag 0 for "date-time")
 *
 * @param namespace The namespace of the data element being serialized.
 * @param elementIdentifier The identifier of the data element being serialized.
 */
@OptIn(ExperimentalSerializationApi::class)
open class IssuerSignedItemSerializer(
    private val namespace: String,
    private val elementIdentifier: String
) : KSerializer<IssuerSignedItem> {

    companion object {
        private val log = KotlinLogging.logger { }

        init {
            CredentialManager.init()
        }
    }

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("IssuerSignedItem") {
        element(IssuerSignedItem.PROP_DIGEST_ID, Long.serializer().descriptor)
        element(IssuerSignedItem.PROP_RANDOM, ByteArraySerializer().descriptor)
        element(IssuerSignedItem.PROP_ELEMENT_ID, String.serializer().descriptor)
        element(IssuerSignedItem.PROP_ELEMENT_VALUE, String.serializer().descriptor)
    }

    override fun serialize(encoder: Encoder, value: IssuerSignedItem) {
        encoder.encodeStructure(descriptor) {
            encodeLongElement(descriptor, 0, value.digestId.toLong())
            encodeSerializableElement(descriptor, 1, ByteArraySerializer(), value.random)
            encodeStringElement(descriptor, 2, value.elementIdentifier)
            encodeAnything(value, 3)
        }
    }

    private fun CompositeEncoder.encodeAnything(value: IssuerSignedItem, index: Int) {
        val elementValueSerializer = buildElementValueSerializer(namespace, value.elementValue, value.elementIdentifier)
        val descriptor = buildClassSerialDescriptor("IssuerSignedItem") {
            element(IssuerSignedItem.PROP_DIGEST_ID, Long.serializer().descriptor)
            element(IssuerSignedItem.PROP_RANDOM, ByteArraySerializer().descriptor)
            element(IssuerSignedItem.PROP_ELEMENT_ID, String.serializer().descriptor)
            element(IssuerSignedItem.PROP_ELEMENT_VALUE, elementValueSerializer.descriptor, value.elementValue.annotations())
        }

        when (val it = value.elementValue) {
            is String -> encodeStringElement(descriptor, index, it)
            is Int -> encodeIntElement(descriptor, index, it)
            is Long -> encodeLongElement(descriptor, index, it)
            is LocalDate -> encodeSerializableElement(descriptor, index, LocalDate.serializer(), it)
            is Instant -> encodeSerializableElement(descriptor, index, InstantStringSerializer, it)
            is Boolean -> encodeBooleanElement(descriptor, index, it)
            is ByteArray -> encodeSerializableElement(descriptor, index, ByteArraySerializer(), it)
            else -> MdocsCborSerializer.encode(namespace, value.elementIdentifier, descriptor, index, this, it)
        }
    }

    /**
     * Tags date time elements correctly,
     * see [RFC 8949 3.4.1](https://datatracker.ietf.org/doc/html/rfc8949#name-standard-date-time-string) for [Instant]
     * (or "date-time"), see [RFC 8943](https://datatracker.ietf.org/doc/html/rfc8943) for [LocalDate] (or "full-date")
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    private fun Any.annotations() =
        when (this) {
            is LocalDate -> listOf(ValueTags(1004uL))
            is Instant -> listOf(ValueTags(0uL))
            else -> emptyList()
        }

    private inline fun <reified T> buildElementValueSerializer(
        namespace: String,
        elementValue: T,
        elementIdentifier: String
    ) = when (elementValue) {
        is String -> String.serializer()
        is Int -> Int.serializer()
        is Long -> Long.serializer()
        is LocalDate -> LocalDate.serializer()
        is Instant -> InstantStringSerializer
        is Boolean -> Boolean.serializer()
        is ByteArray -> ByteArraySerializer()
        is Any -> MdocsCborSerializer.lookupSerializer(namespace, elementIdentifier)
            ?: error("serializer not found for $elementIdentifier, with value $elementValue")

        else -> error("serializer not found for $elementIdentifier, with value $elementValue")
    }


    override fun deserialize(decoder: Decoder): IssuerSignedItem {
        var digestId = 0U
        var random: ByteArray? = null
        var elementValue: Any? = null

        decoder.decodeStructure(descriptor) {
            while (true) {
                val name = decodeStringElement(descriptor, 0)
                // Don't call decodeElementIndex, as it would check for tags. this would break decodeAnything
                val index = descriptor.getElementIndex(name)
                when (name) {
                    IssuerSignedItem.PROP_DIGEST_ID -> digestId = decodeLongElement(descriptor, index).toUInt()
                    IssuerSignedItem.PROP_RANDOM -> random = decodeSerializableElement(descriptor, index, ByteArraySerializer())
                    IssuerSignedItem.PROP_ELEMENT_ID -> if (elementIdentifier != decodeStringElement(descriptor, index))
                        throw IllegalArgumentException("Element identifier mismatch")

                    IssuerSignedItem.PROP_ELEMENT_VALUE -> elementValue = decodeAnything(index, elementIdentifier).also { log.trace { "Decoded: $it (${it::class.simpleName})"} }
                }
                if (random != null && elementValue != null) break
            }
        }
        return IssuerSignedItem(
            digestId = digestId,
            random = random!!,
            elementIdentifier = elementIdentifier,
            elementValue = elementValue!!,
        ).also {
            log.trace { "Deserialized IssuerSignedItem: $it" }
        }
    }

    private fun CompositeDecoder.decodeAnything(index: Int, elementIdentifier: String?): Any {
        log.trace { "-- Decoding for: $elementIdentifier" }

        if (namespace.isBlank())
            log.warn { "Warning: This decoder is not namespace-aware!" }

        // Tags are not read out here but skipped because `decodeElementIndex` is never called, so we cannot
        // discriminate technically, this should be a good thing though, because otherwise we'd consume more from the
        // input
        elementIdentifier?.let {
            MdocsCborSerializer.decode(descriptor, index, this, elementIdentifier, namespace)
                ?.also { log.trace { "Custom serializer decoded: $it" } }
                ?.let { return it }
                ?: log.trace {
                    "Falling back to defaults for namespace $namespace and elementIdentifier $elementIdentifier"
                }
        }

        // These are the ones that map to different CBOR data types, the rest don't, so if it is not registered, we'll
        // lose type information. No others must be added here, as they could consume data from the underlying bytes
        runCatching { return decodeStringElement(descriptor, index) }
        runCatching { return decodeLongElement(descriptor, index) }
        runCatching { return decodeDoubleElement(descriptor, index) }
        runCatching { return decodeBooleanElement(descriptor, index) }

        throw IllegalArgumentException("Could not decode value at $index")
    }
}
