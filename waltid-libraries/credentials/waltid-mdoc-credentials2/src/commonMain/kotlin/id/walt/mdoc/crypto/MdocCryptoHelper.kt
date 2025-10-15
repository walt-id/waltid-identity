package id.walt.mdoc.crypto

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.document.DeviceAuthentication
import id.walt.mdoc.objects.document.DeviceAuthentication.Companion.DEVICE_AUTHENTICATION_TYPE
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.handover.OpenID4VPHandover
import id.walt.mdoc.objects.handover.OpenID4VPHandoverInfo
import id.walt.mdoc.objects.sha256
import id.walt.mdoc.verification.VerificationContext
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.hash.sha2.SHA384
import org.kotlincrypto.hash.sha2.SHA512

/**
 * A helper object for cryptographic and CBOR operations required for Mdoc verification.
 */
@OptIn(ExperimentalSerializationApi::class)
object MdocCryptoHelper {

    private val log = KotlinLogging.logger { }

    /**
     * Reconstructs the SessionTranscript for an OID4VP flow using redirects.
     * As per OpenID for Verifiable Presentations 1.0, Appendix B.2.6.1.
     */
    fun reconstructOid4vpSessionTranscript(context: VerificationContext): SessionTranscript {
        // Step 1: Create the OpenID4VPHandoverInfo structure
        val handoverInfo = OpenID4VPHandoverInfo(
            clientId = context.expectedAudience,
            responseUri = context.responseUri,
            nonce = context.expectedNonce,
            jwkThumbprint = null // jwkThumbprint is null when not using JWE for the response
        )
        log.trace { "Reconstructed OpenID4VPHandoverInfo: $handoverInfo" }


        // Step 2: CBOR-encode and hash the HandoverInfo
        val handoverInfoBytes = coseCompliantCbor.encodeToByteArray(handoverInfo)
        log.trace { "Reconstructed CBOR handoverInfoBytes (hex): ${handoverInfoBytes.toHexString()}" }

        val infoHash = handoverInfoBytes.sha256()
        log.trace { "Handover info SHA-256 hash (hex): ${infoHash.toHexString()}" }

        // Step 3: Create the OpenID4VPHandover structure
        val handover = OpenID4VPHandover(
            identifier = "OpenID4VPHandover",
            infoHash = infoHash
        )

        // Step 4: Create the final SessionTranscript
        return SessionTranscript.forOpenId(handover)
    }

    /**
     * Builds and serializes the DeviceAuthentication structure to be used as the detached payload for device signature verification.
     * As per ISO/IEC 18013-5:2021, 9.1.3.4.
     */
    fun buildDeviceAuthenticationBytes(
        transcript: SessionTranscript,
        docType: String,
        namespaces: ByteStringWrapper<DeviceNameSpaces>
    ): ByteArray {
        val deviceAuth = DeviceAuthentication(
            type = DEVICE_AUTHENTICATION_TYPE,
            sessionTranscript = transcript,
            docType = docType,
            namespaces = namespaces
        )
        log.trace { "Built DeviceAuthentication: $deviceAuth" }

        // 1. Encode the DeviceAuthentication object into its raw CBOR array bytes.
        // This produces the bytes starting with 0x84...
        val deviceAuthCborArrayBytes = coseCompliantCbor.encodeToByteArray(deviceAuth)

        // 2. Wrap the resulting array bytes inside a CBOR byte string.
        // kotlinx.serialization does this automatically when encoding a ByteArray.
        val cborByteString = coseCompliantCbor.encodeToByteArray(deviceAuthCborArrayBytes)

        // 3. Prepend the CBOR Tag for #6.24 (which is 0xd8 0x18).
        return byteArrayOf(0xd8.toByte(), 24.toByte()) + cborByteString
    }

    /**
     * Calculates the digest of a serialized IssuerSignedItem.
     * As per ISO/IEC 18013-5:2021, 9.1.2.5.
     */
    fun calculateDigest(serializedItem: ByteArray, algorithm: String): ByteArray {
        return when (algorithm.uppercase()) {
            "SHA-256" -> SHA256().digest(serializedItem)
            "SHA-384" -> SHA384().digest(serializedItem)
            "SHA-512" -> SHA512().digest(serializedItem)
            else -> throw IllegalArgumentException("Unsupported digest algorithm: $algorithm")
        }
    }
}
