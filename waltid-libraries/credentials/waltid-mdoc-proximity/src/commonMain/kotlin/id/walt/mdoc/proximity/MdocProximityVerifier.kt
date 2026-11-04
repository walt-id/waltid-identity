package id.walt.mdoc.proximity

import id.walt.cose.CoseKey
import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.engagement.DeviceEngagement
import id.walt.mdoc.verification.MdocVerifier
import id.walt.mdoc.verification.VerificationResult
import id.walt.mdoc.verification.validateDocumentSignerCertificateChain
import id.walt.x509.CertificateDer
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlin.io.encoding.Base64

/** Checks a decrypted response without owning the reader's session keys or retrieval channel. */
@OptIn(ExperimentalSerializationApi::class)
object MdocProximityVerifier {
    data class Submission(val response: DeviceResponse, val transcript: SessionTranscript)

    /** Parse before creating a session. Preserve transcript inputs verbatim, including CBOR extensions. */
    fun parse(
        deviceResponse: ByteArray,
        deviceEngagement: ByteArray,
        eReaderKey: ByteArray,
        handover: MdocSessionHandover,
    ): Submission {
        coseCompliantCbor.decodeFromByteArray<DeviceEngagement>(deviceEngagement)
        val readerKey = coseCompliantCbor.decodeFromByteArray<CoseKey>(eReaderKey)
        require(readerKey.d == null) { "eReaderKey must contain public key material only" }
        val response = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(deviceResponse)
        require(response.version == "1.0") { "Unsupported DeviceResponse version" }
        // Decode embedded signed structures now too: malformed CBOR must not create a session.
        response.documents.orEmpty().forEach { document ->
            document.issuerSigned.decodeMobileSecurityObject()
        }
        return Submission(response, handover.createTranscript(ByteString(deviceEngagement), ByteString(eReaderKey)))
    }

    /** Every document is checked. Trust anchoring is evaluated by the configured IACA/VICAL policies. */
    suspend fun verify(submission: Submission): List<VerificationResult> {
        require(submission.response.status == 0u) { "DeviceResponse status is ${submission.response.status}" }
        require(submission.response.documentErrors.isNullOrEmpty()) { "DeviceResponse contains document errors" }
        val documents = submission.response.documents.orEmpty()
        require(documents.isNotEmpty()) { "DeviceResponse contains no documents" }
        return documents.map { document ->
            val result = MdocVerifier.verify(document, submission.transcript)
            require(result.valid) { result.errors.joinToString("; ") }
            require(document.docType == document.issuerSigned.decodeMobileSecurityObject().docType) {
                "Document docType differs from the signed MSO docType"
            }
            validateDocumentSignerCertificateChain(requireNotNull(result.x5c).map { CertificateDer(Base64.decode(it)) })
            result
        }
    }
}
