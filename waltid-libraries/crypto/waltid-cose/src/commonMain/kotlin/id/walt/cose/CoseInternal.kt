@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.cose

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.cbor.CborArray
import kotlinx.serialization.encodeToByteArray

@OptIn(ExperimentalSerializationApi::class)

/**
 * A dedicated Cbor instance for serializing the Sig_structure.
 * It MUST encode defaults (like the "Signature1" context string) and use
 * definite-length encoding as required by the COSE specification.
 */
private val cborForSigStructure2 = Cbor {
    encodeDefaults = true
    useDefiniteLengthEncoding = true
}
private val cborForSigStructure = Cbor(from = Cbor.CoseCompliant) {
    encodeDefaults = true
}

val coseCompliantCbor by lazy {
    Cbor(from = Cbor.CoseCompliant) {
        ignoreUnknownKeys = true
        alwaysUseByteString = true
    }
}

/**
 * Internal structure for the data that gets signed/verified.
 * Sig_structure = [ context, body_protected, external_aad, payload ]
 */
@Serializable
@CborArray
@OptIn(ExperimentalSerializationApi::class)
internal data class Signature1ToBeSigned(
    val context: String = "Signature1",
    @ByteString val protected: ByteArray,
    @ByteString val externalAad: ByteArray,
    @ByteString val payload: ByteArray,
)

/**
 * Helper to build the canonical CBOR byte array to be signed or verified.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun buildSignatureStructure(
    protectedBytes: ByteArray,
    payload: ByteArray?,
    externalAad: ByteArray
): ByteArray {
    val tbs = Signature1ToBeSigned(
        protected = protectedBytes,
        externalAad = externalAad,
        payload = payload ?: byteArrayOf()
    )
    return cborForSigStructure.encodeToByteArray(tbs)
}
