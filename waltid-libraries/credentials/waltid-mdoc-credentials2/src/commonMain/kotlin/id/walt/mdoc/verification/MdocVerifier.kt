@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.mdoc.verification

import id.walt.cose.CoseCertificate
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.toPublicJwk
import id.walt.mdoc.crypto.MdocCrypto
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.namespaces.MdocSignedMerger
import id.walt.mdoc.namespaces.MdocSignedMerger.MdocDuplicatesMergeStrategy
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.parser.MdocParser
import id.walt.mdoc.verification.verifyDeviceAuthentication as verifyDeviceAuthenticationCrypto2
import id.walt.mdoc.verification.verifyIssuerAuthentication as verifyIssuerAuthenticationCrypto2
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64


/**
 * Verifies an MSO Mdoc presentation according to ISO/IEC 18013-5 and OID4VP profiles.
 * The verification process is broken down into auditable steps, returning a detailed result.
 */

object MdocVerifier {

    private val log = KotlinLogging.logger {}

    fun buildSessionTranscriptForContext(context: MdocVerificationContext): SessionTranscript = when {
        context.isAnnexC -> MdocCryptoHelper.reconstructAnnexCSessionTranscript(
            context,
            context.data?.get("data")?.jsonObject["encryptionInfo"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("No encryption info to verify Annex C for, custom data is: ${context.data}")
        )

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
        val issuerAuthResult = resultOfSuspend {
            verifyIssuerAuthenticationCrypto2(document, validateCertificateConstraints = false)
        }
        log.trace { "Issuer auth result: $issuerAuthResult" }

        val msoTimestampsResult = runCatching { verifyMso(mso) }
        log.trace { "MSO Timestamp result: $msoTimestampsResult" }

        val deviceAuthResult = resultOfSuspend {
            verifyDeviceAuthenticationCrypto2(document, mso, sessionTranscript)
        }
        log.trace { "Device authentication result: $deviceAuthResult" }

        val dataIntegrityResult = runCatching { verifyIssuerSignedItemDigests(document, mso) }
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
        val x5c = issuerAuthOrNull?.certificateChain?.map { Base64.encode(it.bytes.toByteArray()) }
        val signerKey = issuerAuthOrNull?.signerKey?.toCompatibilityJwkKey()?.let { DirectSerializedKey(it) }


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

    @Deprecated("Use the shared verifyIssuerAuthentication function returning crypto2 keys")
    suspend fun verifyIssuerAuthentication(document: Document): Pair<List<CoseCertificate>, JWKKey> {
        log.trace { "--- Verifying issuer authentication ---" }
        val verification = verifyIssuerAuthenticationCrypto2(
            document,
            validateCertificateConstraints = false,
        )
        val issuerKey = verification.signerKey.toCompatibilityJwkKey()
        return verification.certificateChain.map { CoseCertificate(it.bytes.toByteArray()) } to issuerKey
    }

    fun verifyMso(mso: MobileSecurityObject) {
        log.trace { "--- Verifying MSO ---" }
        val timestamps = mso.validityInfo
        timestamps.validate()

        require(MdocCrypto.isSupportedDigest(mso.digestAlgorithm)) {
            "MSO digest algorithm is not supported: ${mso.digestAlgorithm}"
        }

    }

    @Deprecated("Use the shared verifyDeviceAuthentication function returning crypto2 verification details")
    suspend fun verifyDeviceAuthentication(document: Document, mso: MobileSecurityObject, sessionTranscript: SessionTranscript) {
        log.trace { "--- Verifying device authentication ---" }
        val verification = verifyDeviceAuthenticationCrypto2(document, mso, sessionTranscript)
        log.trace { "Device public key: ${verification.deviceKey}" }
        log.trace { "Device auth bytes (hex): ${verification.deviceAuthenticationBytes.toHexString()}" }
    }

    @Deprecated("Use verifyIssuerSignedItemDigests")
    fun verifyIssuerSignedDataIntegrity(document: Document, mso: MobileSecurityObject) {
        log.trace { "--- MDOC DATA - ISSUER VERIFIED DATA ---" }
        if (document.issuerSigned.namespaces == null) {
            log.trace { "No issuer-verified data in this mdoc" }
        }
        verifyIssuerSignedItemDigests(document, mso).forEach { verification ->
            log.trace {
                "Hashes match for ${verification.namespace} - ${verification.item.elementIdentifier} " +
                    "(DigestID=${verification.item.digestId}, hash=${verification.calculatedDigest.toHexString()}, " +
                    "serialized=${verification.serialized.toHexString()})"
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

private suspend fun Crypto2Key.toCompatibilityJwkKey(): JWKKey {
    val publicJwk = requireNotNull(capabilities.publicKeyExporter)
        .exportPublicKey().toPublicJwk(spec)
    return JWKKey.importJWK(publicJwk.data.toByteArray().decodeToString()).getOrThrow()
}

private suspend fun <T> resultOfSuspend(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cause: CancellationException) {
    throw cause
} catch (cause: Throwable) {
    Result.failure(cause)
}
