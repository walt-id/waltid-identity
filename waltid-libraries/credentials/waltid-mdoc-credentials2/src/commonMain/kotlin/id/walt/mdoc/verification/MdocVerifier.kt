@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.verification

import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.jwk.JWKKey.Companion.convertDerCertificateToPemCertificate
import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.namespaces.MdocSignedMerger
import id.walt.mdoc.namespaces.MdocSignedMerger.MdocDuplicatesMergeStrategy
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.parser.MdocParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64
import kotlin.time.ExperimentalTime

/**
 * Verifies an MSO Mdoc presentation according to ISO/IEC 18013-5 and OID4VP profiles.
 * The verification process is broken down into auditable steps, returning a detailed result.
 */
@OptIn(ExperimentalTime::class)
object MdocVerifier {

    private val log = KotlinLogging.logger {}

    fun buildSessionTranscriptForContext(context: MdocVerificationContext): SessionTranscript = when {
        context.isDcApi -> MdocCryptoHelper.reconstructDcApiOid4vpSessionTranscript(context)
        else -> MdocCryptoHelper.reconstructOid4vpSessionTranscript(context)
    }

    /**
     * OID4VP-specific verification entry point.
     * This is a convenient overload that constructs the SessionTranscript from OID4VP context.
     */
    suspend fun verify(mdocString: String, context: MdocVerificationContext): VerificationResult {
        val document = MdocParser.parseToDocument(mdocString)

        val sessionTranscript = buildSessionTranscriptForContext(context)

        log.trace { "SessionTranscript: $sessionTranscript" }
        log.trace { "SessionTranscript (hex): ${coseCompliantCbor.encodeToHexString(sessionTranscript)}" }
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
        log.trace { "Decoded MSO (MobileSecurityObject): $mso" }

        // Execute subsequent verification steps, collecting results and errors
        val issuerAuthResult = runCatching { verifyIssuerAuthentication(document) }
        log.trace { "Issuer auth result: $issuerAuthResult" }

        val msoTimestampsResult = runCatching { verifyMso(mso) }
        log.trace { "MSO Timestamp result: $msoTimestampsResult" }

        val deviceAuthResult = runCatching { verifyDeviceAuthentication(document, mso, sessionTranscript) }
        log.trace { "Device authentication result: $deviceAuthResult" }

        val dataIntegrityResult = runCatching { verifyIssuerSignedDataIntegrity(document, mso) }
        log.trace { "Issuer-signed data integrity result: $dataIntegrityResult" }

        val keyAuthzResult = runCatching { verifyDeviceKeyAuthorization(document, mso) }
        log.trace { "Key authorization result: $keyAuthzResult" }

        // Populate errors list from failed steps
        issuerAuthResult.exceptionOrNull()?.let { errors.add("Issuer Authentication failed: ${it.message}") }
        msoTimestampsResult.exceptionOrNull()?.let { errors.add("MSO Validation failed: ${it.message}") }
        deviceAuthResult.exceptionOrNull()?.let { errors.add("Device Authentication failed: ${it.message}") }
        dataIntegrityResult.exceptionOrNull()?.let { errors.add("Data Integrity verification: ${it.message}") }
        keyAuthzResult.exceptionOrNull()?.let { errors.add("Key Authorization verification failed: ${it.message}") }

        val isValid = errors.isEmpty()

        val docType = document.docType
        val credentialData = mergeMdocDataToJson(document)

        val issuerAuthOrNull = issuerAuthResult.getOrNull()
        val x5c = issuerAuthOrNull?.first?.map { Base64.encode(it.rawBytes) }
        val signerKey = issuerAuthOrNull?.second?.let { DirectSerializedKey(it) }


        return VerificationResult(
            valid = isValid,
            issuerSignatureValid = issuerAuthResult.isSuccess,
            dataIntegrityValid = dataIntegrityResult.isSuccess,
            msoValidityValid = msoTimestampsResult.isSuccess,
            deviceSignatureValid = deviceAuthResult.isSuccess,
            deviceKeyAuthorized = keyAuthzResult.isSuccess,

            docType = docType,
            x5c = x5c,
            signerKey = signerKey,
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

    suspend fun verifyIssuerAuthentication(document: Document): Pair<List<CoseCertificate>, JWKKey> {
        log.trace { "--- Verifying issuer authentication ---" }
        val issuerAuth = document.issuerSigned.issuerAuth
        val x5c = issuerAuth.unprotected.x5chain
        requireNotNull(x5c) { "Missing certificate chain in mdocs credential" }

        val signerCertificateBytes = x5c.first().rawBytes
            ?: throw IllegalArgumentException("Missing signer certificate in x5chain.")

        log.trace {
            val signerPem = convertDerCertificateToPemCertificate(signerCertificateBytes)
            "Signer PEM: $signerPem"
        }

        val issuerKey = JWKKey.importFromDerCertificate(signerCertificateBytes).getOrThrow()
        log.trace { "Signer key to be used: $issuerKey" }

        log.trace { "Verifying issuer auth signature with signer key..." }
        if (!issuerAuth.verify(issuerKey.toCoseVerifier())) {
            throw IllegalArgumentException("IssuerAuth COSE_Sign1 signature is invalid.")
        }
        return x5c to issuerKey
    }

    fun verifyMso(mso: MobileSecurityObject) {
        log.trace { "--- Verifying MSO ---" }
        val timestamps = mso.validityInfo
        timestamps.validate()

        require(MdocCrypto.isSupportedDigest(mso.digestAlgorithm)) {
            "MSO digest algorithm is not supported: ${mso.digestAlgorithm}"
        }

    }

    suspend fun verifyDeviceAuthentication(document: Document, mso: MobileSecurityObject, sessionTranscript: SessionTranscript) {
        log.trace { "--- Verifying device authentication ---" }

        val deviceSigned = document.deviceSigned ?: throw IllegalArgumentException("DeviceSigned structure is missing.")
        log.trace { "Device signed: $deviceSigned" }

        val devicePublicKey = JWKKey.importJWK(mso.deviceKeyInfo.deviceKey.toJWK().toString()).getOrThrow()
        log.trace { "Device public key: $devicePublicKey" }

        val deviceAuth = deviceSigned.deviceAuth
        log.trace { "Device auth (of device signed): $deviceAuth" }

        val deviceAuthBytes = MdocCryptoHelper.buildDeviceAuthenticationBytes(
            sessionTranscript,
            document.docType,
            deviceSigned.namespaces
        )
        log.trace { "Device auth bytes (hex): ${deviceAuthBytes.toHexString()}" }

        when {
            deviceAuth.deviceSignature != null -> {
                log.trace { "Device auth contains device signature: ${deviceAuth.deviceSignature}" }
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

                log.trace { "  ${issuerSignedItem.elementIdentifier} (digestId=${issuerSignedItem.digestId}): ${issuerSignedItem.elementValue} (${issuerSignedItem.elementValue::class.simpleName ?: "?"}) (random hex=${issuerSignedItem.random.toHexString()}) => serialized hex = ${serialized.toHexString()}" }

                val issuerSignedItemValueDigest = ValueDigest.fromIssuerSignedItem(issuerSignedItem, namespace, mso.digestAlgorithm)

                val issuerSignedItemHash = issuerSignedItemValueDigest.value
                log.trace { "  Issuer signed item value digest: DigestID = ${issuerSignedItemValueDigest.key}, hash (hex) = ${issuerSignedItemHash.toHexString()}" }



                log.trace { "Finding matching digest in MSO Digests for namespace: ${msoDigestsForNamespace.entries.mapIndexed { idx, msoDigest -> "Option $idx: DigestID=${msoDigest.key} Hash=${msoDigest.value.toHexString()}" }.joinToString()}" }
                val matchingDigest = msoDigestsForNamespace.entries.find { (digestId, digest) -> issuerSignedItem.digestId == digestId }
                    ?: throw IllegalArgumentException("MSO does not contain value digest for this signed item!")

                log.trace { "Matching MSO Digest (${matchingDigest.key}): ${matchingDigest.value.toHexString()}" }
                log.trace { "IssuerSignedItem (${issuerSignedItemValueDigest.key}):    ${issuerSignedItemHash.toHexString()}" }

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
