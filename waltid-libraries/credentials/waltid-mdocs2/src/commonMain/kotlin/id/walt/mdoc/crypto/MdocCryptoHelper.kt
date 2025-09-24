package id.walt.mdoc.crypto

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.document.DeviceAuthentication
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.handover.OpenID4VPHandover
import id.walt.mdoc.objects.handover.OpenID4VPHandoverInfo
import id.walt.mdoc.objects.sha256
import id.walt.mdoc.objects.wrapInCborTag
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.verification.VerificationContext
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

        // Step 2: CBOR-encode and hash the HandoverInfo
        val handoverInfoBytes = coseCompliantCbor.encodeToByteArray(handoverInfo)
        val infoHash = handoverInfoBytes.sha256()

        // Step 3: Create the OpenID4VPHandover structure
        val handover = OpenID4VPHandover(
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
            type = "DeviceAuthentication",
            sessionTranscript = transcript,
            docType = docType,
            namespaces = namespaces
        )
        // This payload is itself wrapped in a CBOR tag for the signature process
        return coseCompliantCbor.encodeToByteArray(deviceAuth).wrapInCborTag(24)
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
