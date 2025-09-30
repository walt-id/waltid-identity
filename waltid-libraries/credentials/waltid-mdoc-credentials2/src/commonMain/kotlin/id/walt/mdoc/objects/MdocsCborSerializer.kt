package id.walt.mdoc.objects

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.CompositeEncoder
import org.kotlincrypto.hash.sha2.SHA256

/**
 * A central registry and dispatcher for dynamic CBOR serialization and deserialization of mdoc data elements.
 *
 * This object holds maps of namespace/element-identifier to specific serialization functions. It is the
 * engine that enables the `Any` type in `IssuerSignedItem` and `DeviceSignedItem` to be handled
 * in a type-safe manner at runtime.
 *
 * ### Important: Global State
 * This MUST be initialized once at application
 * startup before any serialization or deserialization occurs to prevent race conditions and ensure
 * all necessary serializers are registered.
 */
object MdocsCborSerializer {

    private val log = KotlinLogging.logger {}

    private val decoderMap = mutableMapOf<String, Map<String, ItemValueDecoder>>()
    private val encoderMap = mutableMapOf<String, Map<String, ItemValueEncoder>>()
    private val serializerLookupMap = mutableMapOf<String, Map<String, KSerializer<*>>>()
    private val fallbackDecoderMap = mutableMapOf<String, Map<String, ItemValueDecoder>>()

    /**
     * Registers a map of serializers for a specific ISO namespace.
     *
     * @param serializerMap A map where the key is the `elementIdentifier` and the value is the `KSerializer` for that element.
     * @param isoNamespace The namespace to which these serializers apply (e.g., "org.iso.18013.5.1").
     */
    fun register(serializerMap: Map<String, KSerializer<*>>, isoNamespace: String) {
        decoderMap[isoNamespace] =
            serializerMap.map { (k, ser) -> k to createDecodeFunction(ser) }.toMap()
        encoderMap[isoNamespace] = serializerMap.map { (k, ser) ->
            @Suppress("UNCHECKED_CAST")
            k to createEncodeFunction(ser as KSerializer<Any>)
        }.toMap()
        serializerLookupMap[isoNamespace] = serializerMap
    }

    /**
     * Registers a map of fallback decoders for a namespace, to be used if the primary decoder fails.
     */
    fun registerFallbackDecoder(serializerMap: Map<String, KSerializer<*>>, isoNamespace: String) {
        fallbackDecoderMap[isoNamespace] =
            serializerMap.map { (k, ser) ->
                k to createDecodeFunction(ser)
            }.toMap()
    }

    private fun createDecodeFunction(ser: KSerializer<*>) =
        { descriptor: SerialDescriptor, index: Int, compositeDecoder: CompositeDecoder ->
            compositeDecoder.decodeSerializableElement(descriptor, index, ser)!!
        }

    private fun createEncodeFunction(ser: KSerializer<Any>) =
        { descriptor: SerialDescriptor, index: Int, compositeEncoder: CompositeEncoder, value: Any ->
            compositeEncoder.encodeSerializableElement(descriptor, index, ser, value)
        }

    /**
     * Looks up a registered [KSerializer] for a given namespace and element identifier.
     */
    fun lookupSerializer(namespace: String, elementIdentifier: String): KSerializer<*>? =
        serializerLookupMap[namespace]?.get(elementIdentifier)

    /**
     * Dispatches to a registered encoder function for a specific element.
     */
    fun encode(
        namespace: String,
        elementIdentifier: String,
        descriptor: SerialDescriptor,
        index: Int,
        compositeEncoder: CompositeEncoder,
        value: Any,
    ) {
        encoderMap[namespace]?.get(elementIdentifier)?.invoke(descriptor, index, compositeEncoder, value)
    }

    /**
     * Dispatches to a registered decoder function for a specific element, with fallback logic.
     */
    fun decode(
        descriptor: SerialDescriptor,
        index: Int,
        compositeDecoder: CompositeDecoder,
        elementIdentifier: String,
        isoNamespace: String,
    ): Any? =
        decoderMap[isoNamespace]?.get(elementIdentifier)?.runCatching {
            log.trace { "Custom decoder for: $isoNamespace - $elementIdentifier" }
            invoke(descriptor, index, compositeDecoder)
        }?.recoverCatching {
            val fallbackDecoder = fallbackDecoderMap[isoNamespace]?.get(elementIdentifier) ?: throw it
            log.trace { "Error with main decoder, trying fallback decoder" }
            fallbackDecoder.invoke(descriptor, index, compositeDecoder)
        }?.getOrElse {
            log.trace { "Error in custom decoder: ${it.stackTraceToString()}" }
            null
        }
}

private typealias ItemValueEncoder = (descriptor: SerialDescriptor, index: Int, compositeEncoder: CompositeEncoder, value: Any) -> Unit
private typealias ItemValueDecoder = (descriptor: SerialDescriptor, index: Int, compositeDecoder: CompositeDecoder) -> Any

/**
 * Wraps a ByteArray in a CBOR tag according to RFC 8949.
 * Handles single-byte (0-23) and multi-byte (>23) tag numbers correctly.
 *
 * @param tag The CBOR tag number (e.g., 24 for "Encoded CBOR Data Item").
 */
fun ByteArray.wrapInCborTag(tag: Byte) = byteArrayOf(0xd8.toByte()) + byteArrayOf(tag) + this


/**
 * Strips a CBOR tag from a ByteArray.
 * Handles single-byte and multi-byte tag numbers correctly.
 */
fun ByteArray.stripCborTag(tag: Byte): ByteArray {
    val tagBytes = byteArrayOf(0xd8.toByte(), tag)
    return if (this.take(tagBytes.size).toByteArray().contentEquals(tagBytes)) {
        this.drop(tagBytes.size).toByteArray()
    } else {
        this
    }
}

/**
 * Computes the SHA-256 hash of a ByteArray.
 */
fun ByteArray.sha256(): ByteArray = SHA256().digest(this)



