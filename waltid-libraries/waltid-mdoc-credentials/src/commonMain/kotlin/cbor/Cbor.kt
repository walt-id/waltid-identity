package cbor

/*
 * Copyright 2017-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import cbor.internal.ByteArrayInput
import cbor.internal.ByteArrayOutput
import cbor.internal.decoding.CborDecoder
import cbor.internal.decoding.CborReader
import cbor.internal.encoding.CborEncoder
import cbor.internal.encoding.CborWriter
import kotlinx.serialization.*
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule

/**
 * Implements [encoding][encodeToByteArray] and [decoding][decodeFromByteArray] classes to/from bytes
 * using [CBOR](https://tools.ietf.org/html/rfc7049) specification.
 * It is typically used by constructing an application-specific instance, with configured behaviour, and,
 * if necessary, registered custom serializers (in [SerializersModule] provided by [serializersModule] constructor parameter).
 *
 * ### Known caveats and limitations:
 * Supports reading collections of both definite and indefinite lengths; however,
 * serialization always writes maps and lists as [indefinite-length](https://tools.ietf.org/html/rfc7049#section-2.2.1) ones.
 * Does not support [optional tags](https://tools.ietf.org/html/rfc7049#section-2.4) representing datetime, bignums, etc.
 * Fully support CBOR maps, which, unlike JSON ones, may contain keys of non-primitive types, and may produce such maps
 * from corresponding Kotlin objects. However, other 3rd-party parsers (e.g. `jackson-dataformat-cbor`) may not accept such maps.
 *
 * @param encodeDefaults specifies whether default values of Kotlin properties are encoded.
 *                       False by default; meaning that properties with values equal to defaults will be elided.
 * @param ignoreUnknownKeys specifies if unknown CBOR elements should be ignored (skipped) when decoding.
 */
@ExperimentalSerializationApi
sealed class Cbor(
    internal val encodeDefaults: Boolean,
    internal val ignoreUnknownKeys: Boolean,
    override val serializersModule: SerializersModule
) : BinaryFormat {

    /**
     * The default instance of [Cbor]
     */
    companion object Default : Cbor(false, false, EmptySerializersModule())

    override fun <T> encodeToByteArray(serializer: SerializationStrategy<T>, value: T): ByteArray {
        val output = ByteArrayOutput()
        val dumper = CborWriter(this, CborEncoder(output), true)
        dumper.encodeSerializableValue(serializer, value)
        return output.toByteArray()
    }

    override fun <T> decodeFromByteArray(deserializer: DeserializationStrategy<T>, bytes: ByteArray): T {
        val stream = ByteArrayInput(bytes)
        val reader = CborReader(this, CborDecoder(stream))
        return reader.decodeSerializableValue(deserializer)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private class CborImpl(encodeDefaults: Boolean, ignoreUnknownKeys: Boolean, serializersModule: SerializersModule) :
    Cbor(encodeDefaults, ignoreUnknownKeys, serializersModule)

/**
 * Creates an instance of [Cbor] configured from the optionally given [Cbor instance][from]
 * and adjusted with [builderAction].
 */
@ExperimentalSerializationApi
fun Cbor(from: Cbor = Cbor, builderAction: CborBuilder.() -> Unit): Cbor {
    val builder = CborBuilder(from)
    builder.builderAction()
    return CborImpl(builder.encodeDefaults, builder.ignoreUnknownKeys, builder.serializersModule)
}

/**
 * Builder of the [Cbor] instance provided by `Cbor` factory function.
 */
@ExperimentalSerializationApi
class CborBuilder internal constructor(cbor: Cbor) {

    /**
     * Specifies whether default values of Kotlin properties should be encoded.
     */
    var encodeDefaults: Boolean = cbor.encodeDefaults

    /**
     * Specifies whether encounters of unknown properties in the input CBOR
     * should be ignored instead of throwing [SerializationException].
     * `false` by default.
     */
    var ignoreUnknownKeys: Boolean = cbor.ignoreUnknownKeys

    /**
     * Module with contextual and polymorphic serializers to be used in the resulting [Cbor] instance.
     */
    var serializersModule: SerializersModule = cbor.serializersModule
}
