package cbor

/*
 * Copyright 2017-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

/**
 * Specifies that a [ByteArray] shall be encoded/decoded as CBOR major type 2: a byte string.
 * For types other than [ByteArray], [ByteString] will have no effect.
 *
 * Example usage:
 *
 * ```
 * @Serializable
 * data class Data(
 *     @ByteString
 *     val a: ByteArray, // CBOR major type 2: a byte string.
 *
 *     val b: ByteArray  // CBOR major type 4: an array of data items.
 * )
 * ```
 *
 * See [RFC 7049 2.1. Major Types](https://tools.ietf.org/html/rfc7049#section-2.1).
 */
@SerialInfo
@ExperimentalSerializationApi
@Target(AnnotationTarget.PROPERTY)
annotation class ByteString
