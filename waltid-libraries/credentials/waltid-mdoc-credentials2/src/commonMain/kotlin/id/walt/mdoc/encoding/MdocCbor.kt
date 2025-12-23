@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.encoding

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor

/**
 * A centrally configured, ISO 18013-5 compliant CBOR instance for the mdoc library.
 *
 * This instance is configured to:
 * - `ignoreUnknownKeys = true`: For forward compatibility.
 * - `encodeDefaults = true`: Important for creating canonical representations for signing.
 * - `alwaysUseByteString = true`: Ensures all `ByteArray` properties are encoded as CBOR
 * byte strings (major type 2) instead of arrays of integers, as required by the spec.
 */
val MdocCbor = Cbor {
    ignoreUnknownKeys = true
    encodeDefaults = true
    alwaysUseByteString = true
}
