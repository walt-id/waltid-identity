package id.walt.verifier2.data

import kotlinx.serialization.encoding.Decoder

/**
 * Looked up by name, so this library does not depend on the MongoDB driver. `org.bson.codecs.kotlinx.BsonDecoder`
 * is the codec's public decoder interface, which every decoder it creates implements.
 */
private const val BSON_DECODER = "org.bson.codecs.kotlinx.BsonDecoder"

private val implementsBsonDecoder = object : ClassValue<Boolean>() {
    override fun computeValue(type: Class<*>): Boolean = type.implements(BSON_DECODER)
}

private fun Class<*>.implements(interfaceName: String): Boolean =
    name == interfaceName ||
        interfaces.any { it.implements(interfaceName) } ||
        superclass?.implements(interfaceName) == true

internal actual fun Decoder.isBsonDecoder(): Boolean = implementsBsonDecoder.get(this::class.java)
