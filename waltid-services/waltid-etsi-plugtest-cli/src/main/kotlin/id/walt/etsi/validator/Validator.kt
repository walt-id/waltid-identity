package id.walt.etsi.validator

import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseVerifier
import id.walt.credentials.CredentialParser
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.mdoc.objects.document.IssuerSigned
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
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

data class ValidationResult(
    val vendorId: String,
    val fileName: String,
    val testCaseId: String,
    val status: ValidationStatus,
    val verifiedAt: String,
    val hash: String,
    val details: String? = null,
    val errorMessage: String? = null,
    val contentValidation: ContentValidationResult? = null
) {
    enum class ValidationStatus {
        VALID,
        INVALID,
        INDETERMINATE
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
                
                vendorFiles.add(VendorFile(
                    vendorId = vendorId,
                    fileName = fileName,
                    testCaseId = testCaseId,
                    format = format,
                    content = content
                ))
                
                log.debug { "Found file: $vendorId/$fileName (format: $format, testCase: $testCaseId)" }
            }
        }
        
        log.info { "Parsed ${vendorFiles.size} files from ${vendorFiles.map { it.vendorId }.distinct().size} vendors" }
        return vendorFiles
    }

    private fun extractTestCaseId(fileName: String): String? {
        val nameWithoutExtension = fileName.substringBeforeLast(".")
        val pattern = Regex("(SJV|MDL|MDOC)-(QEAA|PuBEAA|EAA)-\\d+.*", RegexOption.IGNORE_CASE)
        return if (pattern.matches(nameWithoutExtension)) nameWithoutExtension else null
    }
}

object CredentialValidator {

    suspend fun validate(vendorFile: VendorFile): ValidationResult {
        val verifiedAt = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        val hash = computeSha1Hash(vendorFile.content)
        
        return try {
            when (vendorFile.format) {
                VendorFile.FileFormat.SD_JWT_VC -> validateSdJwtVc(vendorFile, verifiedAt, hash)
                VendorFile.FileFormat.MDOC -> validateMdoc(vendorFile, verifiedAt, hash)
            }
        } catch (e: Exception) {
            log.error(e) { "Error validating ${vendorFile.fileName}" }
            ValidationResult(
                vendorId = vendorFile.vendorId,
                fileName = vendorFile.fileName,
                testCaseId = vendorFile.testCaseId,
                status = ValidationResult.ValidationStatus.INDETERMINATE,
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
        
        val signerKey: Key? = try {
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
                status = ValidationResult.ValidationStatus.INDETERMINATE,
                verifiedAt = verifiedAt,
                hash = hash,
                errorMessage = "Could not retrieve issuer key from credential"
            )
        }
        
        val verificationResult = credential.verify(signerKey)
        
        return ValidationResult(
            vendorId = vendorFile.vendorId,
            fileName = vendorFile.fileName,
            testCaseId = vendorFile.testCaseId,
            status = if (verificationResult.isSuccess) {
                ValidationResult.ValidationStatus.VALID
            } else {
                ValidationResult.ValidationStatus.INVALID
            },
            verifiedAt = verifiedAt,
            hash = hash,
            details = "Detection: ${detectionResult.credentialPrimaryType}/${detectionResult.credentialSubType}",
            errorMessage = verificationResult.exceptionOrNull()?.message
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun validateMdoc(
        vendorFile: VendorFile,
        verifiedAt: String,
        hash: String
    ): ValidationResult {
        log.debug { "Validating mdoc: ${vendorFile.fileName}" }
        
        val cborBytes = vendorFile.content
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
            
            val signerKey: Key? = try {
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
                    status = ValidationResult.ValidationStatus.INDETERMINATE,
                    verifiedAt = verifiedAt,
                    hash = hash,
                    errorMessage = "Could not retrieve issuer key from mdoc"
                )
            }
            
            val verificationResult = credential.verify(signerKey)
            
            ValidationResult(
                vendorId = vendorFile.vendorId,
                fileName = vendorFile.fileName,
                testCaseId = vendorFile.testCaseId,
                status = if (verificationResult.isSuccess) {
                    ValidationResult.ValidationStatus.VALID
                } else {
                    ValidationResult.ValidationStatus.INVALID
                },
                verifiedAt = verifiedAt,
                hash = hash,
                details = "Detection: ${detectionResult.credentialPrimaryType}/${detectionResult.credentialSubType}",
                errorMessage = verificationResult.exceptionOrNull()?.message
            )
        } catch (e: Exception) {
            log.debug { "CredentialParser failed, trying IssuerSigned format (ETSI plugtest format): ${e.message}" }
            
            // ETSI plugtest mdocs are in IssuerSigned format per the test case notes:
            // "ISO-mdoc QEAA shall be an instance of IssuerSigned type as per section 10.3.3 of ISO 18013-5"
            try {
                val issuerSigned = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(cborBytes)
                log.debug { "Successfully parsed as IssuerSigned" }
                
                // Get the MSO to extract docType
                val mso = issuerSigned.decodeMobileSecurityObject()
                log.debug { "MSO docType: ${mso.docType}" }
                
                // Verify the issuer signature
                val issuerAuth = issuerSigned.issuerAuth
                val x5c = issuerAuth.unprotected.x5chain
                
                if (x5c.isNullOrEmpty()) {
                    return ValidationResult(
                        vendorId = vendorFile.vendorId,
                        fileName = vendorFile.fileName,
                        testCaseId = vendorFile.testCaseId,
                        status = ValidationResult.ValidationStatus.INDETERMINATE,
                        verifiedAt = verifiedAt,
                        hash = hash,
                        details = "Parsed as IssuerSigned (docType: ${mso.docType})",
                        errorMessage = "Missing x5chain certificate in issuerAuth"
                    )
                }
                
                val signerCertificateBytes = x5c.first().rawBytes
                
                val issuerKey = JWKKey.importFromDerCertificate(signerCertificateBytes).getOrElse { certError ->
                    return ValidationResult(
                        vendorId = vendorFile.vendorId,
                        fileName = vendorFile.fileName,
                        testCaseId = vendorFile.testCaseId,
                        status = ValidationResult.ValidationStatus.INDETERMINATE,
                        verifiedAt = verifiedAt,
                        hash = hash,
                        details = "Parsed as IssuerSigned (docType: ${mso.docType})",
                        errorMessage = "Failed to import issuer key from certificate: ${certError.message}"
                    )
                }
                
                log.debug { "Verifying issuerAuth signature..." }
                val signatureValid = issuerAuth.verify(issuerKey.toCoseVerifier())
                
                ValidationResult(
                    vendorId = vendorFile.vendorId,
                    fileName = vendorFile.fileName,
                    testCaseId = vendorFile.testCaseId,
                    status = if (signatureValid) {
                        ValidationResult.ValidationStatus.VALID
                    } else {
                        ValidationResult.ValidationStatus.INVALID
                    },
                    verifiedAt = verifiedAt,
                    hash = hash,
                    details = "IssuerSigned format (docType: ${mso.docType}, x5c certs: ${x5c.size})",
                    errorMessage = if (!signatureValid) "IssuerAuth COSE_Sign1 signature verification failed" else null
                )
            } catch (issuerSignedError: Exception) {
                log.error(issuerSignedError) { "IssuerSigned parsing also failed" }
                
                // Return detailed error with structure analysis
                ValidationResult(
                    vendorId = vendorFile.vendorId,
                    fileName = vendorFile.fileName,
                    testCaseId = vendorFile.testCaseId,
                    status = ValidationResult.ValidationStatus.INDETERMINATE,
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
            // Try to decode as a generic CBOR map to see the top-level keys
            val firstByte = cborBytes.firstOrNull()?.toInt()?.and(0xFF) ?: return "Empty data"
            
            val majorType = (firstByte shr 5) and 0x07
            val additionalInfo = firstByte and 0x1F
            
            val typeDescription = when (majorType) {
                0 -> "Unsigned integer"
                1 -> "Negative integer"
                2 -> "Byte string"
                3 -> "Text string"
                4 -> "Array"
                5 -> "Map"
                6 -> "Tagged value (tag: $additionalInfo)"
                7 -> "Simple/float"
                else -> "Unknown"
            }
            
            // For maps, try to extract key names
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
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString(":") { "%02X".format(it) }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}

object XmlReportGenerator {

    fun generateReport(result: ValidationResult): String {
        val rootElement = when (result.status) {
            ValidationResult.ValidationStatus.VALID -> "VALID"
            ValidationResult.ValidationStatus.INVALID -> "INVALID"
            ValidationResult.ValidationStatus.INDETERMINATE -> "INDETERMINATE"
        }
        
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("<$rootElement>")
            appendLine("  <VerifiedAt>${escapeXml(result.verifiedAt)}</VerifiedAt>")
            appendLine("  <Hash>${escapeXml(result.hash)}</Hash>")
            appendLine("  <VerifiedFile>${escapeXml(result.fileName)}</VerifiedFile>")
            appendLine("  <Vendor>${escapeXml(result.vendorId)}</Vendor>")
            appendLine("  <TestCaseId>${escapeXml(result.testCaseId)}</TestCaseId>")
            if (result.details != null) {
                appendLine("  <Details>${escapeXml(result.details)}</Details>")
            }
            if (result.errorMessage != null) {
                appendLine("  <ErrorMessage>${escapeXml(result.errorMessage)}</ErrorMessage>")
            }
            
            // Add content validation results if present
            result.contentValidation?.let { cv ->
                appendLine("  <ContentValidation>")
                appendLine("    <TestCaseFound>${cv.testCaseFound}</TestCaseFound>")
                appendLine("    <OverallValid>${cv.overallValid}</OverallValid>")
                appendLine("    <Summary>${escapeXml(cv.summary)}</Summary>")
                
                cv.headerValidation?.let { hv ->
                    appendLine("    <HeaderValidation valid=\"${hv.valid}\">")
                    if (hv.missingFields.isNotEmpty()) {
                        appendLine("      <MissingFields>${escapeXml(hv.missingFields.joinToString(", "))}</MissingFields>")
                    }
                    if (hv.presentFields.isNotEmpty()) {
                        appendLine("      <PresentFields>${escapeXml(hv.presentFields.joinToString(", "))}</PresentFields>")
                    }
                    appendLine("    </HeaderValidation>")
                }
                
                cv.payloadValidation?.let { pv ->
                    appendLine("    <PayloadValidation valid=\"${pv.valid}\">")
                    if (pv.missingFields.isNotEmpty()) {
                        appendLine("      <MissingFields>${escapeXml(pv.missingFields.joinToString(", "))}</MissingFields>")
                    }
                    if (pv.presentFields.isNotEmpty()) {
                        appendLine("      <PresentFields>${escapeXml(pv.presentFields.joinToString(", "))}</PresentFields>")
                    }
                    appendLine("    </PayloadValidation>")
                }
                
                cv.namespaceValidation?.let { nv ->
                    appendLine("    <NamespaceValidation valid=\"${nv.valid}\">")
                    if (nv.missingFields.isNotEmpty()) {
                        appendLine("      <MissingFields>${escapeXml(nv.missingFields.joinToString(", "))}</MissingFields>")
                    }
                    if (nv.presentFields.isNotEmpty()) {
                        appendLine("      <PresentFields>${escapeXml(nv.presentFields.joinToString(", "))}</PresentFields>")
                    }
                    appendLine("    </NamespaceValidation>")
                }
                
                appendLine("  </ContentValidation>")
            }
            
            appendLine("</$rootElement>")
        }
    }

    fun generateReportFileName(result: ValidationResult, verifierName: String = "WALTID"): String {
        return "Verification_of_${result.vendorId}_${result.testCaseId}.xml"
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
