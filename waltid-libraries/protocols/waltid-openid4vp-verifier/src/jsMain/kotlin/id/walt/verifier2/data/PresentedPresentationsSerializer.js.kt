package id.walt.verifier2.data

import kotlinx.serialization.encoding.Decoder

/** MongoDB's `bson-kotlinx` codec exists for the JVM only. */
internal actual fun Decoder.isBsonDecoder(): Boolean = false
