package id.walt.openid4vp.verifier.verification

import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseVerifier
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.presentations.formats.MsoMdocPresentation
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.keys.jwk.JWKKey.Companion.convertDerCertificateToPemCertificate
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.isocred.*
import id.walt.isocred.handover.OpenID4VPHandover
import id.walt.isocred.handover.OpenID4VPHandoverInfo
import id.walt.mdoc.MdocSignedMerger
import id.walt.mdoc.MdocSignedMerger.MdocDuplicatesMergeStrategy
import id.walt.openid4vp.verifier.verification.Verifier2PresentationValidator.PresentationValidationResult
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object MdocPresentationValidator {

    private val log = KotlinLogging.logger("MdocPresentationValidator")

    @OptIn(ExperimentalTime::class, ExperimentalSerializationApi::class)
    suspend fun validateMsoMdocPresentation(
        mdocBase64UrlString: String,
        // These three (expectedNonce, expectedAudience, responseUri) are required to reconstruct the SessionTranscript
        expectedNonce: String,
        expectedAudience: String, // This is the client_id
        responseUri: String?
    ): Result<PresentationValidationResult> = runCatching {
        log.trace { "-- Mdoc validation --" }

        requireNotNull(responseUri) { "Response uri is required for mdoc presentation validation" }

        log.trace { "Mdoc validation - 1. Decode and parse the DeviceResponse" }
        val deviceResponseBytes = mdocBase64UrlString.decodeFromBase64Url()
        val deviceResponse = coseCompliantCbor.decodeFromByteArray<DeviceResponse>(deviceResponseBytes)
        log.trace { "Device response: $deviceResponse" }
        val document = deviceResponse.documents?.firstOrNull()
            ?: throw Exception("Mdoc DeviceResponse is missing document")
        log.trace { "Document: $document" }

        requireNotNull(document.deviceSigned) { "Mdoc DeviceSigned is missing in document" }

        log.trace { "Mdoc validation - 2. Decode the MSO from the issuerAuth payload" }
        val mso = document.issuerSigned.issuerAuth.decodeIsoPayload<MobileSecurityObject>()
        log.trace { "MSO: $mso" }

        log.trace { "Mdoc validation - 2.1. Verify MSO (IssuerAuth)" }

        val signerCertificate = document.issuerSigned.issuerAuth.unprotected.x5chain?.first()?.rawBytes
        requireNotNull(signerCertificate) { "Missing signer certificate" }
        val signerPem = convertDerCertificateToPemCertificate(signerCertificate)
        log.trace { "Signer PEM: $signerPem" }

        log.trace { "Reading signer key from signer certificate..." }
        val issuerKey = JWKKey.importFromDerCertificate(signerCertificate).getOrThrow()
        log.trace { "Signer key to be used: $issuerKey" }
        val issuerVirtualDid = LocalRegistrar().createByKey(issuerKey, DidJwkCreateOptions()).did

        log.trace { "Verifying issuerAuth signature with issuer key..." }
        val issuerAuthSignatureValid = document.issuerSigned.issuerAuth.verify(issuerKey.toCoseVerifier())

        require(issuerAuthSignatureValid) { "IssuerAuth signature is invalid!" }

        log.trace { "Issuer signature on MSO valid." }
        val timestamps = mso.validityInfo

        val msoSigned = timestamps.signed
        val msoValidFrom = timestamps.validFrom
        val msoValidUntil = timestamps.validUntil

        log.trace { "MSO signed:     $msoSigned" }
        log.trace { "MSO until from: $msoValidFrom" }
        log.trace { "MSO until to:   $msoValidUntil" }

        val now = Clock.System.now()
        require(msoValidFrom <= now) { "MSO is not yet valid" }
        require(msoValidUntil >= now) { "MSO is no longer valid" }

        log.trace { "MSO uses digest algorithm: ${mso.digestAlgorithm}" }
        // TEMPORARY vvv - CHANGE WITH MdocsCrypto
        require(mso.digestAlgorithm == "SHA-256") { "Only supporting SHA-256 as MSO digest algorithm right now" }

        log.trace { "MSO validated." }


        log.trace { "Mdoc validation - Get the device's public key" }

        val devicePublicKey = JWKKey.importJWK(mso.deviceKeyInfo.deviceKey.toJWK().toString()).getOrThrow()
        log.trace { "Device key: $devicePublicKey" }


        // ---

        log.trace { "Mdoc validation - 3. Reconstruct the SessionTranscript on the verifier's side following OID4VP Appendix B.2.6.1" }
        val handoverInfo = OpenID4VPHandoverInfo(
            clientId = expectedAudience,
            nonce = expectedNonce,
            jwkThumbprint = null, // Not using JWE in DIRECT_POST
            responseUri = responseUri
        )
        log.trace { "Handover info: $handoverInfo" }
        val handover = OpenID4VPHandover(
            infoHash = coseCompliantCbor.encodeToByteArray(handoverInfo).sha256()
        )
        log.trace { "Handover: $handover" }
        val sessionTranscript = SessionTranscript.forOpenId(handover)
        log.trace { "Session transcript: $sessionTranscript" }

        log.trace { "Mdoc validation - 4. Reconstruct the DeviceAuthentication payload that SHOULD have been signed" }
        val disclosedNamespaces = document.deviceSigned!!.namespaces
        val reconstructedDeviceAuth = DeviceAuthentication(
            type = "DeviceAuthentication",
            sessionTranscript = sessionTranscript,
            docType = document.docType,
            namespaces = disclosedNamespaces
        )
        log.trace { "Reconstructed device auth: $reconstructedDeviceAuth" }
        val reconstructedPayloadBytes = coseCompliantCbor.encodeToByteArray(reconstructedDeviceAuth).wrapInCborTag(24)

        log.trace { "Mdoc validation - 5. Verify the deviceAuth signature against the reconstructed detached payload" }
        val deviceSignature = document.deviceSigned!!.deviceAuth.deviceSignature
            ?: throw Exception("DeviceSignature not found in DeviceAuth")
        log.trace { "Device signature: $deviceSignature" }

        val isSignatureValid = deviceSignature.verifyDetached(
            verifier = devicePublicKey.toCoseVerifier(),
            detachedPayload = reconstructedPayloadBytes
        )
        log.trace { "Is signature valid: $isSignatureValid" }
        require(isSignatureValid) { "Mdoc device authentication signature failed to verify. The nonce might be correct but the transcript hashes could not be fully validated." }


        log.trace { "Mdoc validation - Data Element Integrity Validation" }


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
                val issuerSignedItemValueDigest = ValueDigest.fromIssuerSignedItem(issuerSignedItem, namespace)
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

        // Merge credentialData
        val issuerSignedNamespacesJson = document.issuerSigned.namespacesToJson()
        val deviceSignedNamespacesJson = document.deviceSigned?.namespaces?.value?.namespacesToJson()

        val mdocCredentialData = if (deviceSignedNamespacesJson == null) {
            issuerSignedNamespacesJson
        } else {
            MdocSignedMerger.merge(issuerSignedNamespacesJson, deviceSignedNamespacesJson, strategy = MdocDuplicatesMergeStrategy.CLASH)
        }

        log.trace { "Mdoc validation - Successful: wrap verified data in generic credential objects..." }
        val mdocsCredential = MdocsCredential(
            credentialData = mdocCredentialData,
            signed = mdocBase64UrlString,
            docType = document.docType,
            issuer = issuerVirtualDid
        )
        log.trace { "Mdoc credential: $mdocsCredential" }



        PresentationValidationResult(
            presentation = MsoMdocPresentation(mdocsCredential),
            credentials = listOf(mdocsCredential)
        )
    }

}
