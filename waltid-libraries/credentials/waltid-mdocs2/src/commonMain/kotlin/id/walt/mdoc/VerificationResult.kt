package id.walt.mdoc

import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.mso.MobileSecurityObject
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi

/**
 * A result class providing a detailed breakdown of the mdoc verification process.
 *
 * @property overallResult True if all mandatory verification checks passed, false otherwise.
 * @property issuerSignatureValid True if the issuer signature on the Mobile Security Object (MSO) is valid.
 * @property dataIntegrityValid True if the digests of all returned issuer-signed data elements match the digests in the MSO.
 * @property msoValidityValid True if the current time is within the MSO's validity period.
 * @property deviceSignatureValid True if the device authentication (signature or MAC) is valid.
 * @property deviceKeyAuthorized True if the device key was authorized by the issuer to sign the returned data elements.
 */
data class VerificationResult(
    val overallResult: Boolean,
    val issuerSignatureValid: Boolean? = null,
    val dataIntegrityValid: Boolean? = null,
    val msoValidityValid: Boolean? = null,
    val deviceSignatureValid: Boolean? = null,
    val deviceKeyAuthorized: Boolean? = null,
)

/**
 * The central class for verifying the authenticity and integrity of a mobile document (mdoc).
 * It performs a step-by-step validation according to the ISO/IEC 18013-5 standard.
 */
class MdocVerifier {

    /**
     * Verifies a received mdoc.
     *
     * @param mdoc The mdoc to be verified.
     * @param sessionTranscript The session transcript from the engagement phase, required for device authentication.
     * @param eReaderPrivateKey The ephemeral private key of the mdoc reader, required for deriving MAC keys.
     * @return A [VerificationResult] containing the outcome of all checks.
     */
    @OptIn(ExperimentalSerializationApi::class)
    suspend fun verify(mdoc: Mdoc, sessionTranscript: ByteArray, eReaderPrivateKey: Key): VerificationResult {
        // MSO is essential for all subsequent checks
        val mso = mdoc.mso ?: return VerificationResult(false, issuerSignatureValid = false)
        val document = mdoc.documents.first() // Assuming one document for simplicity

        // Step 1: Verify Issuer Signature on MSO
        val issuerSignatureValid = verifyIssuerAuth(document)
        if (!issuerSignatureValid) {
            return VerificationResult(false, issuerSignatureValid = false)
        }

        // Step 2: Check MSO Validity Period
        val msoValidityValid = verifyMsoValidity(mso)

        // Step 3: Verify Issuer-Signed Data Element Integrity
        val dataIntegrityValid = verifyIssuerSignedItems(document, mso)

        // Step 4: Verify Device Authentication (Signature or MAC)
        val deviceAuthValid = verifyDeviceAuth(document, sessionTranscript, eReaderPrivateKey, mso)

        // Step 5: Check Device Key Authorization
        val deviceKeyAuthorized = verifyDeviceKeyAuthorization(document, mso)

        val overallResult = msoValidityValid && dataIntegrityValid && deviceAuthValid && deviceKeyAuthorized

        return VerificationResult(
            overallResult = overallResult,
            issuerSignatureValid = true,
            dataIntegrityValid = dataIntegrityValid,
            msoValidityValid = msoValidityValid,
            deviceSignatureValid = deviceAuthValid,
            deviceKeyAuthorized = deviceKeyAuthorized
        )
    }

    /**
     * Step 1: Verifies the issuer's COSE_Sign1 signature on the MSO.
     * Extracts the public key from the first certificate in the x5chain unprotected header.
     */
    private suspend fun verifyIssuerAuth(doc: MdocDocument): Boolean {
        println("Verifying Issuer Authentication...")
        val issuerCertificate = doc.issuerAuth.unprotected.x5chain?.firstOrNull()
            ?: return false

        val issuerPublicKey = JWKKey.importFromDerCertificate(issuerCertificate.rawBytes).getOrThrow()
        val verifier = issuerPublicKey.toCoseVerifier()

        return doc.issuerAuth.verify(verifier)
    }

    /**
     * Step 2: Verifies that the current time is within the MSO's validity period.
     */
    private fun verifyMsoValidity(mso: MobileSecurityObject): Boolean {
        println("Verifying MSO validity period...")
        val now = Clock.System.now()
        val validFrom = Instant.parse(mso.validityInfo.validFrom)
        val validUntil = Instant.parse(mso.validityInfo.validUntil)

        return now in validFrom..validUntil
    }

    /**
     * Step 3: Verifies the digests of all issuer-signed items against the MSO.
     */
    private fun verifyIssuerSignedItems(doc: MdocDocument, mso: MobileSecurityObject): Boolean {
        println("Verifying data integrity of issuer-signed items...")
        val allDigestsMatch = doc.issuerSigned.nameSpaces?.all { (namespace, items) ->
            items.all { item ->
                val expectedDigest = mso.valueDigests[namespace]?.get(item.digestID)
                if (expectedDigest == null) {
                    println("Digest not found for ${item.elementIdentifier} in namespace $namespace")
                    return@all false
                }
                val actualDigest = MdocCrypto.digest(item, mso.digestAlgorithm)
                expectedDigest.contentEquals(actualDigest)
            }
        } ?: true // No issuer-signed items to check

        return allDigestsMatch
    }

    /**
     * Step 4: Dispatches device authentication verification to the appropriate method (signature or MAC).
     */
    private suspend fun verifyDeviceAuth(
        doc: MdocDocument,
        sessionTranscript: ByteArray,
        eReaderPrivateKey: Key,
        mso: MobileSecurityObject
    ): Boolean {
        println("Verifying Device Authentication...")
        val deviceAuth = doc.deviceSigned.deviceAuth
        val deviceNameSpaces = doc.deviceSigned.nameSpaces

        if (deviceNameSpaces == null) {
            println("Warning: DeviceSigned has no namespaces.")
            return false
        }

        val deviceAuthBytes = MdocCrypto.buildDeviceAuthenticationBytes(
            sessionTranscript,
            doc.docType,
            deviceNameSpaces
        )

        val sDevicePublicKey = MdocCrypto.coseKeyToJwkKey(mso.deviceKeyInfo.deviceKey)

        return when {
            deviceAuth.deviceSignature != null -> MdocCrypto.verifyDeviceSignature(
                deviceAuthBytes,
                deviceAuth.deviceSignature,
                sDevicePublicKey
            )

            deviceAuth.deviceMac != null -> {
                val eReaderKeyBytes = MdocCrypto.parseSessionTranscript(sessionTranscript)?.eReaderKeyBytes
                    ?: return false
                val eReaderPublicKey = MdocCrypto.decodeCoseKey(eReaderKeyBytes)

                MdocCrypto.verifyDeviceMac(
                    deviceAuthBytes,
                    deviceAuth.deviceMac,
                    sessionTranscript,
                    eReaderPrivateKey,
                    sDevicePublicKey
                )
            }

            else -> false // No device authentication provided
        }
    }

    /**
     * Step 5: Checks if the device key was authorized by the issuer to sign the returned device-signed data elements.
     */
    private fun verifyDeviceKeyAuthorization(doc: MdocDocument, mso: MobileSecurityObject): Boolean {
        println("Verifying device key authorization...")
        val keyAuth = mso.deviceKeyInfo.keyAuthorizations ?: return true // No restrictions
        val deviceDataElements = doc.deviceSigned.nameSpaces?.nameSpaces ?: return true // No data to check

        return deviceDataElements.all { (namespace, elements) ->
            if (keyAuth.nameSpaces?.contains(namespace) == true) {
                return@all true // Entire namespace is authorized
            }

            val authorizedElementsForNamespace = keyAuth.dataElements?.get(namespace)
            elements.keys.all { elementIdentifier ->
                authorizedElementsForNamespace?.contains(elementIdentifier) ?: false
            }
        }
    }
}
