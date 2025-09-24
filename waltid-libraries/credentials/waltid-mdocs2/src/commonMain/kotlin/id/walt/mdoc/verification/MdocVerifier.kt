package id.walt.mdoc.verification

import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.jwk.JWKKey.Companion.convertDerCertificateToPemCertificate
import id.walt.mdoc.namespaces.MdocSignedMerger
import id.walt.mdoc.namespaces.MdocSignedMerger.MdocDuplicatesMergeStrategy
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.parser.MdocParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonObject
import kotlin.time.ExperimentalTime

/**
 * Verifies an MSO Mdoc presentation according to ISO/IEC 18013-5 and OID4VP profiles.
 * The verification process is broken down into auditable steps, returning a detailed result.
 */
@OptIn(ExperimentalTime::class)
object MdocVerifier {

    private val log = KotlinLogging.logger {}

    /**
     * OID4VP-specific verification entry point.
     * This is a convenient overload that constructs the SessionTranscript from OID4VP context.
     */
    suspend fun verify(mdocString: String, context: VerificationContext): VerificationResult {
        val document = MdocParser.parseToDocument(mdocString)
        val sessionTranscript = MdocCryptoHelper.reconstructOid4vpSessionTranscript(context)
        return verify(document, sessionTranscript)
    }

    /**
     * Generic verification entry point. Verifies a credential with a pre-constructed SessionTranscript.
     * This makes the verifier reusable in contexts other than OID4VP.
     */
    suspend fun verify(document: Document, sessionTranscript: SessionTranscript): VerificationResult {
        val errors = mutableListOf<String>()

        // Step 1: Parse Document from credential payload
        val mso = document.issuerSigned.decodeMobileSecurityObject()

        // Execute subsequent verification steps, collecting results and errors
        val issuerAuthResult = runCatching { verifyIssuerAuthentication(document) }
        val msoTimestampsResult = runCatching { verifyMso(mso) }
        val deviceAuthResult = runCatching { verifyDeviceAuthentication(document, mso, sessionTranscript) }
        val dataIntegrityResult = runCatching { verifyIssuerSignedDataIntegrity(document, mso) }
        val keyAuthzResult = runCatching { verifyDeviceKeyAuthorization(document, mso) }

        // Populate errors list from failed steps
        issuerAuthResult.exceptionOrNull()?.let { errors.add("Issuer Authentication failed: ${it.message}") }
        msoTimestampsResult.exceptionOrNull()?.let { errors.add("MSO Validation failed: ${it.message}") }
        deviceAuthResult.exceptionOrNull()?.let { errors.add("Device Authentication failed: ${it.message}") }
        dataIntegrityResult.exceptionOrNull()?.let { errors.add("Data Integrity verification: ${it.message}") }
        keyAuthzResult.exceptionOrNull()?.let { errors.add("Key Authorization verification failed: ${it.message}") }

        val isValid = errors.isEmpty()

        val docType = document.docType
        val credentialData = mergeMdocDataToJson(document)

        return VerificationResult(
            valid = isValid,
            issuerSignatureValid = issuerAuthResult.isSuccess,
            dataIntegrityValid = dataIntegrityResult.isSuccess,
            msoValidityValid = msoTimestampsResult.isSuccess,
            deviceSignatureValid = deviceAuthResult.isSuccess,
            deviceKeyAuthorized = keyAuthzResult.isSuccess,

            docType = docType,
            issuerKey = DirectSerializedKey(issuerAuthResult.getOrThrow()),
            credentialData = credentialData,

            errors = errors
        )
    }

    // --- Verification Step Implementations (each throws an exception on failure) ---


    fun mergeMdocDataToJson(document: Document, strategy: MdocDuplicatesMergeStrategy = MdocDuplicatesMergeStrategy.CLASH): JsonObject {
        val issuerSignedNamespacesJson = document.issuerSigned.namespacesToJson()
        val deviceSignedNamespacesJson = document.deviceSigned?.namespaces?.value?.namespacesToJson()

        val mdocCredentialData = if (deviceSignedNamespacesJson == null) {
            issuerSignedNamespacesJson
        } else {
            MdocSignedMerger.merge(issuerSignedNamespacesJson, deviceSignedNamespacesJson, strategy = strategy)
        }

        return mdocCredentialData
    }

    suspend fun verifyIssuerAuthentication(document: Document): JWKKey {
        val issuerAuth = document.issuerSigned.issuerAuth
        val signerCertificateBytes = issuerAuth.unprotected.x5chain?.first()?.rawBytes
            ?: throw IllegalArgumentException("Missing signer certificate in x5chain.")

        log.trace {
            val signerPem = convertDerCertificateToPemCertificate(signerCertificateBytes)
            "Signer PEM: $signerPem"
        }

        val issuerKey = JWKKey.importFromDerCertificate(signerCertificateBytes).getOrThrow()
        log.trace { "Signer key to be used: $issuerKey" }

        if (!issuerAuth.verify(issuerKey.toCoseVerifier())) {
            throw IllegalArgumentException("IssuerAuth COSE_Sign1 signature is invalid.")
        }
        return issuerKey
    }

    fun verifyMso(mso: MobileSecurityObject) {
        val timestamps = mso.validityInfo
        timestamps.validate()

        require(MdocCrypto.isSupportedDigest(mso.digestAlgorithm)) {
            "MSO digest algorithm is not supported: ${mso.digestAlgorithm}"
        }

    }

    suspend fun verifyDeviceAuthentication(document: Document, mso: MobileSecurityObject, sessionTranscript: SessionTranscript) {
        val deviceSigned = document.deviceSigned ?: throw IllegalArgumentException("DeviceSigned structure is missing.")
        val devicePublicKey = JWKKey.importJWK(mso.deviceKeyInfo.deviceKey.toJWK().toString()).getOrThrow()


        val deviceAuth = deviceSigned.deviceAuth

        val deviceAuthBytes = MdocCryptoHelper.buildDeviceAuthenticationBytes(
            sessionTranscript,
            document.docType,
            deviceSigned.namespaces
        )

        when {
            deviceAuth.deviceSignature != null -> {
                require(
                    MdocCrypto.verifyDeviceSignature(
                        payloadToVerify = deviceAuthBytes,
                        deviceSignature = deviceAuth.deviceSignature,
                        sDevicePublicKey = devicePublicKey
                    )
                ) { "Device authentication signature failed to verify." }
            }

            deviceAuth.deviceMac != null -> {
                TODO("Device MAC is not yet validated")
                /*val eReaderKeyBytes = MdocCrypto.parseSessionTranscript(sessionTranscript)?.eReaderKeyBytes
                    ?: return false
                val eReaderPublicKey = MdocCrypto.decodeCoseKey(eReaderKeyBytes)

                MdocCrypto.verifyDeviceMac(
                    deviceAuthBytes = deviceAuthBytes,
                    deviceMac = deviceAuth.deviceMac,
                    sessionTranscript = sessionTranscript,
                    eReaderPrivateKey,
                    sDevicePublicKey = devicePublicKey
                )*/
            }

            else -> { // No device authentication provided
                throw IllegalArgumentException("No device authentication provided ")
            }
        }
    }

    fun verifyIssuerSignedDataIntegrity(document: Document, mso: MobileSecurityObject) {
        log.trace { "--- MDOC DATA - ISSUER VERIFIED DATA ---" }
        val issuerSignedNamespaces = document.issuerSigned.namespaces

        if (issuerSignedNamespaces == null) {
            log.trace { "No issuer-verified data in this mdoc" }
        }

        issuerSignedNamespaces?.forEach { (namespace, issuerSignedItems) ->
            log.trace { "Namespace: $namespace" }
            val msoDigestsForNamespace = mso.valueDigests[namespace]
                ?: throw IllegalArgumentException("MSO is missing value digests for namespace: $namespace (only has digests for: ${mso.valueDigests.keys})")
            issuerSignedItems.entries.forEach { issuerSignedItemWrapped ->
                val issuerSignedItem = issuerSignedItemWrapped.value
                val serialized = issuerSignedItemWrapped.serialized
                val issuerSignedItemValueDigest = ValueDigest.fromIssuerSignedItem(issuerSignedItem, namespace, mso.digestAlgorithm)
                val issuerSignedItemHash = issuerSignedItemValueDigest.value

                log.trace { "  ${issuerSignedItem.elementIdentifier} (${issuerSignedItem.digestId}): ${issuerSignedItem.elementValue} (${issuerSignedItem.elementValue::class.simpleName ?: "?"}) (random hex=${issuerSignedItem.random.toHexString()}) => serialized hex = ${serialized.toHexString()}" }

                val matchingDigest = msoDigestsForNamespace.entries.find { (digestId, digest) -> issuerSignedItem.digestId == digestId }
                    ?: throw IllegalArgumentException("MSO does not contain value digest for this signed item!")
                log.trace { "Matching MSO Digest: ${matchingDigest.value.toHexString()}" }
                log.trace { "IssuerSignedItem:    ${issuerSignedItemHash.toHexString()}" }

                val hashesMatch = matchingDigest.value.contentEquals(issuerSignedItemHash)

                if (hashesMatch) {
                    log.trace { "Hashes match for $namespace - ${issuerSignedItem.elementIdentifier}" }
                } else {
                    val elementValueType = issuerSignedItem.elementValue::class.simpleName
                    if (elementValueType !in listOf("String", "Long", "Boolean", "UInt")) {
                        log.warn { "Hash does not match for non primitive type: $namespace - ${issuerSignedItem.elementIdentifier} has invalid hash for value: ${issuerSignedItem.elementValue} ($elementValueType). Does the Issuer support this non-primitive type?" }
                    } else {
                        throw IllegalArgumentException("Value digest does not match! Has data been tampered with? Matching digest from MSO: $matchingDigest, IssuerSignedItem: $issuerSignedItemWrapped")
                    }
                }

            }
        }
    }

    fun verifyDeviceKeyAuthorization(document: Document, mso: MobileSecurityObject) {
        log.trace { "--- MDOC DATA - HOLDER VERIFIED DATA ---" }

        val deviceSignedNamespaces = document.deviceSigned?.namespaces?.value?.entries
        if (deviceSignedNamespaces == null) {
            log.trace { "No holder-verified data in this mdoc (no namespaces in DeviceSigned)." }
        } else {
            if (deviceSignedNamespaces.isEmpty()) {
                log.trace { "Namespace list in DeviceSigned exists, but is empty." }
            } else {
                val keyAuthorization = mso.deviceKeyInfo.keyAuthorizations
                    ?: throw IllegalArgumentException("Found holder-verified data, but KeyAuthorization is fully missing")

                keyAuthorization.namespaces?.forEach {
                    log.trace { "KeyAuthorization authorizes namespaces: $it" }
                }
                keyAuthorization.dataElements?.forEach { (namespace, elementIdentifiers) ->
                    log.trace { "KeyAuthorization data elements: $namespace - $elementIdentifiers" }
                }

                deviceSignedNamespaces.entries.forEach { (namespace, deviceSignedItems) ->
                    val isNamespaceFullAuthorized = keyAuthorization.namespaces?.contains(namespace) == true
                    log.trace { "Is full namespace authorized for $namespace: $isNamespaceFullAuthorized" }

                    deviceSignedItems.entries.forEach { deviceSignedItem ->
                        val elementIdentifier = deviceSignedItem.key

                        val isElementAuthorized = keyAuthorization.dataElements?.get(namespace)?.contains(elementIdentifier) == true
                        val elementValue = deviceSignedItem.value

                        log.trace { "  $namespace - $elementIdentifier -> $elementValue (${elementValue::class.simpleName})" }

                        require(isNamespaceFullAuthorized || isElementAuthorized) { "The holder-verified data $namespace - $elementIdentifier is not authorized in KeyAuthorization" }

                    }
                }
            }
        }
    }
}
