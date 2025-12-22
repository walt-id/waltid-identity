@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.objects.dcapi

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborArray

/**
 * Represents the `dcapiInfo` structure used in the `SessionTranscript` for a DCAPI flow.
 * It is serialized as a CBOR array.
 *
 * As per the specification, the [encryptionInfo] object within this structure is first
 * CBOR-encoded and then Base64URL-encoded into a string before being placed in the array.
 * This transformation is handled by the [EncryptionInfoBase64UrlSerializer].
 *
 * @see ISO/IEC TS 18013-7:2024(en), Annex C.5
 *
 * @property encryptionInfo The encryption details for the response.
 * @property serializedOrigin The ASCII-serialized origin of the mdoc reader's request.
 */
@Serializable
@CborArray
data class DCAPIInfo(
    /** Base64EncryptionInfo contains the cbor encoded EncryptionInfo as
     * a base64-url-without-padding string.
     */
    @Serializable(with = DCAPIEncryptionInfo.EncryptionInfoBase64UrlSerializer::class)
    val encryptionInfo: DCAPIEncryptionInfo,
    /** Serialized origin of the request as defined in
     * https://html.spec.whatwg.org/multipage/browsers.html#ascii-serialisation-of-an-origin
     */
    val serializedOrigin: String,
)
