package id.walt.etsi.validator

import id.walt.cose.CoseHeaders
import id.walt.cose.coseCompliantCbor
import id.walt.credentials.CredentialParser
import id.walt.credentials.UnreachableDisclosuresException
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto.utils.Base64Utils.matchesBase64Url
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.policies2.vc.ExpirationDatePolicyException
import id.walt.policies2.vc.NotBeforePolicyException
import id.walt.policies2.vc.SerializableRuntimeException
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vc.policies.ExpirationDatePolicy
import id.walt.policies2.vc.policies.NotBeforePolicy
import id.walt.policies2.vc.policies.PolicyExecutionContext
import id.walt.policies2.vc.policies.VctIntegrityException
import id.walt.policies2.vc.policies.VctIntegrityPolicy
import id.walt.policies2.vp.policies.IssuerAuthMdocVpPolicy
import id.walt.policies2.vp.policies.IssuerSignedDataMdocVpPolicy
import id.walt.policies2.vp.policies.MsoVerificationMdocVpPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.zip.ZipFile

private val log = KotlinLogging.logger {}

data class VendorFile(
    val vendorId: String,
    val fileName: String,
    val testCaseId: String,
    val format: FileFormat,
    val content: ByteArray
) {
    enum class FileFormat {
        SD_JWT_VC,  // .json files
        MDOC        // .cbor files
    }
}

/**
 * Result of a single named policy check.
 * Used to populate the <PolicyResults> block in the XML report.
 */
data class PolicyCheckResult(
    val policyId: String,
    val status: ValidationResult.ValidationStatus,
    val errorMessage: String? = null,
    /** Rich detail fields produced by the policy (e.g. signer key, digest info, timestamps). */
    val results: Map<String, JsonElement> = emptyMap()
)

data class ValidationResult(
    val vendorId: String,
    val fileName: String,
    val testCaseId: String,
    val signatureStatus: ValidationStatus,
    val verifiedAt: String,
    val hash: String,
    val details: String? = null,
    val errorMessage: String? = null,
    val contentValidation: ContentValidationResult? = null,
    /** Per-policy check results, populated for SD-JWT-VC credentials. */
    val policyResults: List<PolicyCheckResult> = emptyList()
) {
    enum class ValidationStatus {
        VALID,
        INVALID,
        INDETERMINATE
    }

    /**
     * Overall status considers both signature validation AND content validation.
     * - If signature is INVALID or INDETERMINATE, that takes precedence
     * - If signature is VALID but content validation exists and failed, overall is INVALID
     * - If signature is VALID and content validation passed (or wasn't performed), overall is VALID
     */
    val overallStatus: ValidationStatus
        get() = when {
            signatureStatus != ValidationStatus.VALID -> signatureStatus
            contentValidation != null && !contentValidation.overallValid -> ValidationStatus.INVALID
            else -> ValidationStatus.VALID
        }
}

object ZipParser {

    fun parseZipFile(zipFile: File): List<VendorFile> {
        log.info { "Parsing zip file: ${zipFile.absolutePath}" }

        val vendorFiles = mutableListOf<VendorFile>()

        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach

                val pathParts = entry.name.split("/").filter { it.isNotBlank() }
                if (pathParts.size < 2) {
                    log.debug { "Skipping file not in vendor folder: ${entry.name}" }
                    return@forEach
                }

                val vendorId = pathParts[0]
                val fileName = pathParts.last()

                if (fileName.startsWith("Verification_of_")) {
                    log.debug { "Skipping existing verification report: $fileName" }
                    return@forEach
                }

                val format = when {
                    fileName.endsWith(".json", ignoreCase = true) -> VendorFile.FileFormat.SD_JWT_VC
                    fileName.endsWith(".cbor", ignoreCase = true) -> VendorFile.FileFormat.MDOC
                    else -> {
                        log.debug { "Skipping unknown file format: $fileName" }
                        return@forEach
                    }
                }

                val testCaseId = extractTestCaseId(fileName)
                if (testCaseId == null) {
                    log.warn { "Could not extract test case ID from: $fileName" }
                    return@forEach
                }

                val content = zip.getInputStream(entry).readBytes()

                vendorFiles.add(
                    VendorFile(
                        vendorId = vendorId,
                        fileName = fileName,
                        testCaseId = testCaseId,
                        format = format,
                        content = content
                    )
                )

                log.debug { "Found file: $vendorId/$fileName (format: $format, testCase: $testCaseId)" }
            }
        }

        log.info { "Parsed ${vendorFiles.size} files from ${vendorFiles.map { it.vendorId }.distinct().size} vendors" }
        return vendorFiles
    }

    private fun extractTestCaseId(fileName: String): String? {
        val nameWithoutExtension = fileName.substringBeforeLast(".")
        // Matches both positive test cases (EAA, QEAA, PuBEAA) and
        // ETSI negative test cases (EAAN, QEAAN, PuBEAAN — the N suffix marks negative/invalid)
        val pattern = Regex("(SJV|MDL|MDOC)-(QEAA|PuBEAA|EAA)N?-\\d+.*", RegexOption.IGNORE_CASE)
        return if (pattern.matches(nameWithoutExtension)) nameWithoutExtension else null
    }
}

object CredentialValidator {

    private val signaturePolicy = CredentialSignaturePolicy()
    private val expirationPolicy = ExpirationDatePolicy()
    private val notBeforePolicy = NotBeforePolicy()
    /** Offline plugtest: skip value verification (requires fetching Type Metadata from network). */
    private val vctIntegrityPolicy = VctIntegrityPolicy(skipValueVerification = true)

    // mdoc VP policies — used directly on MdocsCredential.document + documentMso
    private val issuerAuthPolicy = IssuerAuthMdocVpPolicy()
    // strictEtsiPrecision=true: enforce EAA-6.2.7.1-04/05 (ETSI TS 119 472-1) — no fractional seconds in validFrom/validUntil
    private val msoPolicy = MsoVerificationMdocVpPolicy(strictEtsiPrecision = true)
    private val integrityPolicy = IssuerSignedDataMdocVpPolicy()

    /** Convert a VP PolicyRunResult to a plugtest PolicyCheckResult, preserving all detail fields. */
    private fun id.walt.policies2.vp.policies.VPPolicy2.PolicyRunResult.toCheckResult() =
        PolicyCheckResult(
            policyId = policyExecuted.id,
            status = if (success) ValidationResult.ValidationStatus.VALID
                     else ValidationResult.ValidationStatus.INVALID,
            errorMessage = errors.firstOrNull()?.message,
            results = results
        )

    /** Convert a CredentialVerificationPolicy2 Result<JsonElement> to a PolicyCheckResult. */
    private fun policyResult(
        policyId: String,
        result: Result<JsonElement>,
        status: ValidationResult.ValidationStatus
    ): PolicyCheckResult {
        val ex = result.exceptionOrNull()
        // Typed policy exceptions carry their data as fields — serialize them to get rich results.
        // Plain exceptions fall back to message + empty results.
        val results: Map<String, JsonElement> = when {
            ex is SerializableRuntimeException ->
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val serializer = kotlinx.serialization.serializer(ex::class, emptyList(), false)
                        as kotlinx.serialization.KSerializer<Any>
                    (Json.encodeToJsonElement(serializer, ex) as? JsonObject)?.toMap().orEmpty()
                }.getOrElse { emptyMap() }
            else ->
                (result.getOrNull() as? JsonObject)?.toMap().orEmpty()
        }
        return PolicyCheckResult(
            policyId = policyId,
            status = status,
            errorMessage = ex?.message,
            results = results
        )
    }

    suspend fun validate(vendorFile: VendorFile): ValidationResult {
        val verifiedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val hash = computeSha1Hash(vendorFile.content)

        return try {
            when (vendorFile.format) {
                VendorFile.FileFormat.SD_JWT_VC -> validateSdJwtVc(vendorFile, verifiedAt, hash)
                VendorFile.FileFormat.MDOC -> validateMdoc(vendorFile, verifiedAt, hash)
            }
        } catch (e: UnreachableDisclosuresException) {
            // Per RFC 9901 §7.1 step 5: disclosures not reachable from payload hashes → MUST reject (INVALID)
            log.warn { "INVALID (unreachable disclosures): ${vendorFile.fileName}: ${e.message}" }
            ValidationResult(
                vendorId = vendorFile.vendorId,
                fileName = vendorFile.fileName,
                testCaseId = vendorFile.testCaseId,
                signatureStatus = ValidationResult.ValidationStatus.INVALID,
                verifiedAt = verifiedAt,
                hash = hash,
                errorMessage = e.message
            )
        } catch (e: Throwable) {
            log.error(e) { "Error validating ${vendorFile.fileName}" }
            ValidationResult(
                vendorId = vendorFile.vendorId,
                fileName = vendorFile.fileName,
                testCaseId = vendorFile.testCaseId,
                signatureStatus = ValidationResult.ValidationStatus.INDETERMINATE,
                verifiedAt = verifiedAt,
                hash = hash,
                errorMessage = "Validation error: ${e.message}"
            )
        }
    }

    private suspend fun validateSdJwtVc(
        vendorFile: VendorFile,
        verifiedAt: String,
        hash: String
    ): ValidationResult {
        val sdJwtString = vendorFile.content.decodeToString().trim()

        log.debug { "Validating SD-JWT-VC: ${vendorFile.fileName}" }

        val (detectionResult, credential) = CredentialParser.detectAndParse(sdJwtString)

        val policyResults = mutableListOf<PolicyCheckResult>()
        val context = PolicyExecutionContext.Empty

        // --- vct#integrity policy ---
        // Checks presence (EAA-5.2.1.2-03 SHALL) and format.
        // Value correctness (requires fetching Type Metadata from vct URL) is CRIT-13 / out of scope offline.
        val vctResult = runCatching { vctIntegrityPolicy.verify(credential, context) }.getOrElse { Result.failure(it) }
        val vctStatus = when {
            vctResult.isSuccess -> ValidationResult.ValidationStatus.VALID
            vctResult.exceptionOrNull() is VctIntegrityException &&
            (vctResult.exceptionOrNull() as VctIntegrityException).reason.let {
                it == VctIntegrityException.Reason.MISSING || it == VctIntegrityException.Reason.MALFORMED ||
                it == VctIntegrityException.Reason.VALUE_MISMATCH
            } -> ValidationResult.ValidationStatus.INVALID
            else -> ValidationResult.ValidationStatus.INDETERMINATE
        }
        policyResults += policyResult(vctIntegrityPolicy.id, vctResult, vctStatus)

        // --- Signature policy (via CredentialSignaturePolicy) ---
        val sigResult = signaturePolicy.verify(credential, context)
        val sigStatus = if (sigResult.isSuccess) ValidationResult.ValidationStatus.VALID
                        else ValidationResult.ValidationStatus.INVALID
        policyResults += policyResult(signaturePolicy.id, sigResult, sigStatus)

        // --- Expiration policy ---
        // ExpirationDatePolicyException = credential is expired → INVALID (MUST reject per RFC 9901 §7.1 step 6)
        val expResult = runCatching { expirationPolicy.verify(credential, context) }.getOrElse { Result.failure(it) }
        val expStatus = when {
            expResult.isSuccess -> ValidationResult.ValidationStatus.VALID
            expResult.exceptionOrNull() is ExpirationDatePolicyException -> ValidationResult.ValidationStatus.INVALID
            else -> ValidationResult.ValidationStatus.INDETERMINATE
        }
        policyResults += policyResult(expirationPolicy.id, expResult, expStatus)

        // --- Not-before policy ---
        // NotBeforePolicyException = credential not yet valid → INVALID
        val nbfResult = runCatching { notBeforePolicy.verify(credential, context) }.getOrElse { Result.failure(it) }
        val nbfStatus = when {
            nbfResult.isSuccess -> ValidationResult.ValidationStatus.VALID
            nbfResult.exceptionOrNull() is NotBeforePolicyException -> ValidationResult.ValidationStatus.INVALID
            else -> ValidationResult.ValidationStatus.INDETERMINATE
        }
        policyResults += policyResult(notBeforePolicy.id, nbfResult, nbfStatus)

        // Overall signatureStatus: INVALID if any mandatory policy fails, VALID only if all pass.
        // Expiration and not-before are mandatory validity checks — a failed credential MUST be rejected.
        val overallSigStatus = policyResults
            .map { it.status }
            .firstOrNull { it == ValidationResult.ValidationStatus.INVALID }
            ?: policyResults.map { it.status }.firstOrNull { it == ValidationResult.ValidationStatus.INDETERMINATE }
            ?: ValidationResult.ValidationStatus.VALID

        val firstError = policyResults.firstOrNull { it.status != ValidationResult.ValidationStatus.VALID }

        return ValidationResult(
            vendorId = vendorFile.vendorId,
            fileName = vendorFile.fileName,
            testCaseId = vendorFile.testCaseId,
            signatureStatus = overallSigStatus,
            verifiedAt = verifiedAt,
            hash = hash,
            details = "Detection: ${detectionResult.credentialPrimaryType}/${detectionResult.credentialSubType}",
            errorMessage = firstError?.errorMessage,
            policyResults = policyResults
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun validateMdoc(
        vendorFile: VendorFile,
        verifiedAt: String,
        hash: String
    ): ValidationResult {
        log.debug { "Validating mdoc: ${vendorFile.fileName}" }

        val cborBytes = vendorFile.content.let { raw ->
            // Some participants (e.g. SITA) submit their .cbor files as base64url-encoded text
            // rather than raw binary CBOR. Detect and decode transparently.
            val asText = raw.decodeToString().trim()
            if (asText.matchesBase64Url()) {
                log.debug { "Detected base64url-encoded CBOR in ${vendorFile.fileName}, decoding" }
                asText.decodeFromBase64Url()
            } else raw
        }
        val hexString = cborBytes.toHexString()

        // First, try to understand the CBOR structure
        val structureInfo = try {
            analyzeCborStructure(cborBytes)
        } catch (e: Exception) {
            "Unknown structure: ${e.message}"
        }
        log.debug { "CBOR structure analysis: $structureInfo" }

        // Try parsing with CredentialParser (handles DeviceResponse and Document)
        return try {
            val (detectionResult, credential) = CredentialParser.detectAndParse(hexString)

            val signerKey = try {
                credential.getSignerKey()
            } catch (e: Exception) {
                log.warn { "Could not retrieve signer key: ${e.message}" }
                null
            }

            if (signerKey == null) {
                return ValidationResult(
                    vendorId = vendorFile.vendorId,
                    fileName = vendorFile.fileName,
                    testCaseId = vendorFile.testCaseId,
                    signatureStatus = ValidationResult.ValidationStatus.INDETERMINATE,
                    verifiedAt = verifiedAt,
                    hash = hash,
                    errorMessage = "Could not retrieve issuer key from mdoc"
                )
            }

            val verificationResult = credential.verify(signerKey)
            val sigStatus = if (verificationResult.isSuccess) ValidationResult.ValidationStatus.VALID
                            else ValidationResult.ValidationStatus.INVALID

            // If parsed as MdocsCredential, run the actual mdoc VP policies for rich per-policy results
            val policyResults = if (credential is id.walt.credentials.formats.MdocsCredential) {
                val document = credential.document
                val mso = credential.documentMso

                // Run issuer_auth policy to get certificate_chain, signer_jwk, signer_pem etc.
                // The signature was already verified above via credential.verify(); use that status
                // but take the rich results from the policy run.
                val issuerAuthRun = issuerAuthPolicy.runPolicy(document, mso, null).toCheckResult()
                val issuerAuthResult = issuerAuthRun.copy(status = sigStatus,
                    errorMessage = issuerAuthRun.errorMessage ?: verificationResult.exceptionOrNull()?.message)

                listOf(
                    issuerAuthResult,
                    // mso_validity: validity timestamps + digest algorithm
                    msoPolicy.runPolicy(document, mso, null).toCheckResult(),
                    // issuer_signed_integrity: each data element digest matches MSO
                    integrityPolicy.runPolicy(document, mso, null).toCheckResult(),
                )
            } else emptyList()

            val overallStatus = (policyResults.map { it.status } + sigStatus)
                .firstOrNull { it == ValidationResult.ValidationStatus.INVALID }
                ?: (policyResults.map { it.status } + sigStatus)
                    .firstOrNull { it == ValidationResult.ValidationStatus.INDETERMINATE }
                ?: ValidationResult.ValidationStatus.VALID

            ValidationResult(
                vendorId = vendorFile.vendorId,
                fileName = vendorFile.fileName,
                testCaseId = vendorFile.testCaseId,
                signatureStatus = overallStatus,
                verifiedAt = verifiedAt,
                hash = hash,
                details = "Detection: ${detectionResult.credentialPrimaryType}/${detectionResult.credentialSubType}",
                errorMessage = (policyResults.firstOrNull { it.status != ValidationResult.ValidationStatus.VALID }
                    ?.errorMessage) ?: verificationResult.exceptionOrNull()?.message,
                policyResults = policyResults
            )
        } catch (e: Exception) {
            log.debug { "CredentialParser failed, trying IssuerSigned format (ETSI plugtest format): ${e.message}" }

            // ETSI plugtest mdocs are in IssuerSigned format:
            // "ISO-mdoc QEAA shall be an instance of IssuerSigned type as per section 10.3.3 of ISO 18013-5"
            try {
                val issuerSigned = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(cborBytes)
                log.debug { "Successfully parsed as IssuerSigned" }

                val mso = issuerSigned.decodeMobileSecurityObject()
                log.debug { "MSO docType: ${mso.docType}" }

                // Build a stub Document so we can run the actual mdoc VP policy objects
                val document = Document(docType = mso.docType, issuerSigned = issuerSigned)

                val policyResults = listOf(
                    // issuer_auth: COSE_Sign1 + x5chain (protected header fallback via getParsedIssuerAuth)
                    issuerAuthPolicy.runPolicy(document, mso, null).toCheckResult(),
                    // mso_validity: validity timestamps + supported digest algorithm
                    msoPolicy.runPolicy(document, mso, null).toCheckResult(),
                    // issuer_signed_integrity: each IssuerSignedItem digest matches MSO
                    integrityPolicy.runPolicy(document, mso, null).toCheckResult(),
                )

                // Signature status is driven by issuer_auth
                val sigStatus = policyResults.first { it.policyId == issuerAuthPolicy.id }.status
                val firstError = policyResults.firstOrNull { it.status != ValidationResult.ValidationStatus.VALID }

                ValidationResult(
                    vendorId = vendorFile.vendorId,
                    fileName = vendorFile.fileName,
                    testCaseId = vendorFile.testCaseId,
                    signatureStatus = sigStatus,
                    verifiedAt = verifiedAt,
                    hash = hash,
                    details = "IssuerSigned format (docType: ${mso.docType}, x5c certs: ${
                        issuerSigned.issuerAuth.unprotected.x5chain?.size
                            ?: runCatching { coseCompliantCbor.decodeFromByteArray<CoseHeaders>(issuerSigned.issuerAuth.protected).x5chain?.size }.getOrNull()
                            ?: 0
                    })",
                    errorMessage = firstError?.errorMessage,
                    policyResults = policyResults
                )
            } catch (issuerSignedError: Exception) {
                log.error(issuerSignedError) { "IssuerSigned parsing also failed" }

                ValidationResult(
                    vendorId = vendorFile.vendorId,
                    fileName = vendorFile.fileName,
                    testCaseId = vendorFile.testCaseId,
                    signatureStatus = ValidationResult.ValidationStatus.INDETERMINATE,
                    verifiedAt = verifiedAt,
                    hash = hash,
                    details = "Structure: $structureInfo",
                    errorMessage = "Failed to parse mdoc. Tried Document, DeviceResponse, and IssuerSigned formats. IssuerSigned error: ${issuerSignedError.message}"
                )
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun analyzeCborStructure(cborBytes: ByteArray): String {
        return try {
            val firstByte = cborBytes.firstOrNull()?.toInt()?.and(0xFF) ?: return "Empty data"
            val majorType = (firstByte shr 5) and 0x07
            val additionalInfo = firstByte and 0x1F
            val typeDescription = when (majorType) {
                0 -> "Unsigned integer"; 1 -> "Negative integer"; 2 -> "Byte string"
                3 -> "Text string"; 4 -> "Array"; 5 -> "Map"
                6 -> "Tagged value (tag: $additionalInfo)"; 7 -> "Simple/float"; else -> "Unknown"
            }
            if (majorType == 5) {
                val mapSize = when {
                    additionalInfo < 24 -> additionalInfo
                    additionalInfo == 24 -> cborBytes.getOrNull(1)?.toInt()?.and(0xFF) ?: -1
                    else -> -1
                }
                "CBOR Map with ~$mapSize entries. First byte: 0x${firstByte.toString(16)}"
            } else {
                "$typeDescription. First byte: 0x${firstByte.toString(16)}, size: ${cborBytes.size} bytes"
            }
        } catch (e: Exception) {
            "Analysis failed: ${e.message}"
        }
    }

    private fun computeSha1Hash(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-1")
        return digest.digest(data).joinToString(":") { "%02X".format(it) }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}

object XmlReportGenerator {

    fun generateReport(result: ValidationResult): String {
        val rootElement = when (result.overallStatus) {
            ValidationResult.ValidationStatus.VALID -> "VALID"
            ValidationResult.ValidationStatus.INVALID -> "INVALID"
            ValidationResult.ValidationStatus.INDETERMINATE -> "INDETERMINATE"
        }

        val signatureStatusText = result.signatureStatus.name

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("<$rootElement>")
            appendLine("  <VerifiedAt>${escapeXml(result.verifiedAt)}</VerifiedAt>")
            appendLine("  <Hash>${escapeXml(result.hash)}</Hash>")
            appendLine("  <VerifiedFile>${escapeXml(result.fileName)}</VerifiedFile>")
            appendLine("  <Vendor>${escapeXml(result.vendorId)}</Vendor>")
            appendLine("  <TestCaseId>${escapeXml(result.testCaseId)}</TestCaseId>")
            appendLine("  <SignatureStatus>$signatureStatusText</SignatureStatus>")
            if (result.details != null) {
                appendLine("  <Details>${escapeXml(result.details)}</Details>")
            }
            if (result.errorMessage != null) {
                appendLine("  <ErrorMessage>${escapeXml(result.errorMessage)}</ErrorMessage>")
            }

            // Per-policy results with full detail fields
            if (result.policyResults.isNotEmpty()) {
                appendLine("  <PolicyResults>")
                for (pr in result.policyResults) {
                    val hasContent = pr.errorMessage != null || pr.results.isNotEmpty()
                    if (hasContent) {
                        appendLine("    <PolicyResult policy=\"${escapeXml(pr.policyId)}\" status=\"${pr.status.name}\">")
                        if (pr.errorMessage != null) {
                            appendLine("      <ErrorMessage>${escapeXml(pr.errorMessage)}</ErrorMessage>")
                        }
                        for ((key, value) in pr.results) {
                            val raw = value.toString()
                                .trim('"')
                                // Simplify JsonPath toString: JsonPath(tokens=[ObjectAccessorToken(key=exp)]) -> $.exp
                                .replace(Regex("JsonPath\\(tokens=\\[.*?AccessorToken\\(key=(\\w+)\\).*?\\)"), "\\\$.$1")
                            // Use CDATA only when the value contains XML-special characters;
                            // use plain text for simple values (booleans, numbers, plain strings)
                            val needsCdata = raw.any { it == '<' || it == '>' || it == '&' || it == '"' || it == '\'' }
                            val rendered = if (needsCdata) "<![CDATA[$raw]]>" else escapeXml(raw)
                            appendLine("      <Result key=\"${escapeXml(key)}\">$rendered</Result>")
                        }
                        appendLine("    </PolicyResult>")
                    } else {
                        appendLine("    <PolicyResult policy=\"${escapeXml(pr.policyId)}\" status=\"${pr.status.name}\"/>")
                    }
                }
                appendLine("  </PolicyResults>")
            }

            // Content validation results
            result.contentValidation?.let { cv ->
                appendLine("  <ContentValidation>")
                appendLine("    <TestCaseFound>${cv.testCaseFound}</TestCaseFound>")
                appendLine("    <OverallValid>${cv.overallValid}</OverallValid>")
                appendLine("    <Summary>${escapeXml(cv.summary)}</Summary>")

                cv.headerValidation?.let { hv ->
                    appendLine("    <HeaderValidation valid=\"${hv.valid}\">")
                    if (hv.missingFields.isNotEmpty())
                        appendLine("      <MissingFields>${escapeXml(hv.missingFields.joinToString(", "))}</MissingFields>")
                    if (hv.presentFields.isNotEmpty())
                        appendLine("      <PresentFields>${escapeXml(hv.presentFields.joinToString(", "))}</PresentFields>")
                    appendLine("    </HeaderValidation>")
                }

                cv.payloadValidation?.let { pv ->
                    appendLine("    <PayloadValidation valid=\"${pv.valid}\">")
                    if (pv.missingFields.isNotEmpty())
                        appendLine("      <MissingFields>${escapeXml(pv.missingFields.joinToString(", "))}</MissingFields>")
                    if (pv.presentFields.isNotEmpty())
                        appendLine("      <PresentFields>${escapeXml(pv.presentFields.joinToString(", "))}</PresentFields>")
                    appendLine("    </PayloadValidation>")
                }

                cv.namespaceValidation?.let { nv ->
                    appendLine("    <NamespaceValidation valid=\"${nv.valid}\">")
                    if (nv.missingFields.isNotEmpty())
                        appendLine("      <MissingFields>${escapeXml(nv.missingFields.joinToString(", "))}</MissingFields>")
                    if (nv.presentFields.isNotEmpty())
                        appendLine("      <PresentFields>${escapeXml(nv.presentFields.joinToString(", "))}</PresentFields>")
                    appendLine("    </NamespaceValidation>")
                }

                appendLine("  </ContentValidation>")
            }

            appendLine("</$rootElement>")
        }
    }

    fun generateReportFileName(result: ValidationResult): String =
        "Verification_of_${result.vendorId}_${result.testCaseId}.xml"

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
