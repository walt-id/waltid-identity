package id.walt.vical

import id.walt.cose.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

/**
 * Main class for handling a VICAL (Verified Issuer Certificate Authority List).
 * It encapsulates a COSE_Sign1 object and the decoded VicalData payload.
 *
 * Use the companion object to create or decode instances.
 *
 * @property coseSign1 The raw COSE_Sign1 structure.
 * @property vicalData The decoded VICAL payload data.
 */
@Serializable
data class Vical(
    val coseSign1: CoseSign1,
    val vicalData: VicalData
) {

    /**
     * Verifies the signature of the VICAL.
     *
     * @param verifier A CoseVerifier instance created from the VICAL provider's public key.
     * @return True if the signature is valid, false otherwise.
     */
    suspend fun verify(verifier: CoseVerifier): Boolean {
        // The external_aad is an empty byte string for VICAL as per the spec.
        return coseSign1.verify(verifier, externalAad = byteArrayOf())
    }

    fun getCertificateChain() = coseSign1.unprotected.x5chain

    /**
     * Encodes the VICAL back into its tagged CBOR byte array representation.
     *
     * @return The tagged COSE_Sign1 byte array.
     */
    fun toTaggedCbor(): ByteArray = coseSign1.toTagged()

    companion object {
        /**
         * Decodes a VICAL from its tagged COSE_Sign1 CBOR representation.
         *
         * @param taggedCbor The byte array containing the tagged COSE_Sign1 VICAL.
         * @return A parsed Vical instance.
         * @throws IllegalArgumentException if the payload is missing or decoding fails.
         */
        @OptIn(ExperimentalSerializationApi::class)
        fun decode(taggedCbor: ByteArray): Vical {
            val cose = CoseSign1.fromTagged(taggedCbor)
            val payload = cose.payload
                ?: throw IllegalArgumentException("VICAL COSE_Sign1 payload cannot be null")
            val vicalData = coseCompliantCbor.decodeFromByteArray<VicalData>(payload)
            return Vical(cose, vicalData)
        }

        /**
         * Creates a new VICAL and signs it.
         *
         * @param vicalData The VICAL data to be signed.
         * @param signer The CoseSigner to use for signing.
         * @param algorithmId The COSE algorithm identifier (e.g., -7 for ES256).
         * @param signerCertificateChain The certificate chain of the signer, to be included in the x5chain header.
         * @return A new, signed Vical instance.
         */
        @OptIn(ExperimentalSerializationApi::class)
        suspend fun createAndSign(
            vicalData: VicalData,
            signer: CoseSigner,
            algorithmId: Int,
            signerCertificateChain: List<CoseCertificate>
        ): Vical {
            val payload = coseCompliantCbor.encodeToByteArray(vicalData)

            val protectedHeaders = CoseHeaders(algorithm = algorithmId)
            //val protectedHeaders = AltCoseHeader(algorithm = CoseAlgorithm.entries.first { it.coseValue == algorithmId })
            val unprotectedHeaders = CoseHeaders(x5chain = signerCertificateChain)
            //val unprotectedHeaders = AltCoseHeader(certificateChain = signerCertificateChain)

            val cose = CoseSign1.createAndSign(
                protectedHeaders = protectedHeaders,
                unprotectedHeaders = unprotectedHeaders,
                payload = payload,
                signer = signer,
                externalAad = byteArrayOf() // external_aad is empty for VICAL
            )
            return Vical(cose, vicalData)
        }
    }
}

