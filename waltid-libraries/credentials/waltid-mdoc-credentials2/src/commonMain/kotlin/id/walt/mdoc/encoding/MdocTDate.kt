package id.walt.mdoc.encoding

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Instant

/**
 * RFC 3339 date-time formatting for CBOR tag 0 (tdate) per ISO/IEC 18013-5 and RFC 8943.
 *
 * Fractional seconds are not permitted in mdoc date-time strings; values are truncated to whole seconds.
 */
fun Instant.toMdocTDateString(): String =
    Instant.fromEpochSeconds(epochSeconds).toString()

/**
 * Normalizes an RFC 3339 date-time string to second-level precision without fractional seconds.
 */
fun String.toMdocTDateString(): String =
    Instant.parse(this).toMdocTDateString()

/**
 * Serializes [Instant] values as CBOR tag-0 date-time strings without fractional seconds.
 */
object MdocTDateInstantSerializer : TransformingSerializerTemplate<Instant, String>(
    parent = String.serializer(),
    encodeAs = { it.toMdocTDateString() },
    decodeAs = { Instant.parse(it) },
    serialName = "MdocTDateInstant",
)
