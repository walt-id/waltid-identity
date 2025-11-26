@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.cose

import io.github.oshai.kotlinlogging.KotlinLogging
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
        // DO NOT SET "encodeDefaults = true"
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
) {
    companion object {
        val SIGNATURE1_CONTEXT = "Signature1"
    }
}

private val log = KotlinLogging.logger { }

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
        context = "Signature1",
        protected = protectedBytes,
        externalAad = externalAad,
        payload = payload ?: byteArrayOf()
    )
    log.trace { "Signature1ToBeSigned: $tbs" }

    return cborForSigStructure.encodeToByteArray(tbs)
}

/**
 * Internal structure for the data that gets MACed/verified.
 * Per RFC 8152, Section 6.3:
 * MAC_structure = [ context, body_protected, external_aad, payload ]
 */
@Serializable
@CborArray
@OptIn(ExperimentalSerializationApi::class)
internal data class Mac0ToBeMaced(
    val context: String, // = "MAC0"
    @ByteString val protected: ByteArray,
    @ByteString val externalAad: ByteArray,
    @ByteString val payload: ByteArray,
)

/**
 * Helper to build the canonical CBOR byte array to be MACed or verified.
 */
@OptIn(ExperimentalSerializationApi::class)
internal fun buildMacStructure(
    protectedBytes: ByteArray,
    payload: ByteArray?,
    externalAad: ByteArray
): ByteArray {
    val tbm = Mac0ToBeMaced(
        context = "MAC0",
        protected = protectedBytes,
        externalAad = externalAad,
        payload = payload ?: byteArrayOf()
    )
    // Use the same canonical Cbor instance as for signing
    return cborForSigStructure.encodeToByteArray(tbm)
}
