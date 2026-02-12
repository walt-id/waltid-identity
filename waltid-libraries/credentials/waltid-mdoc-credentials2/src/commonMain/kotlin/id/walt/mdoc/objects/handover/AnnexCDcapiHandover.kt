@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.handover

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.CborArray

/** * Helper class to structure the data before hashing.
 * This corresponds to the `dcapiInfo` structure in the spec.
 */
@Serializable
@CborArray
data class AnnexCDcapiHandoverInfo(
    /** The raw Base64url string sent in the JS request 'encryptionInfo' field */
    val base64EncryptionInfo: String,

    /** The origin string (e.g. "https://example.com") - NO trailing slash! */
    val origin: String
)
