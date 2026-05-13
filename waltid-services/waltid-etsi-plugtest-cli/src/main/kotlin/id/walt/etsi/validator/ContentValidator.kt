package id.walt.etsi.validator

import id.walt.cose.coseCompliantCbor
import id.walt.etsi.ScrapedData
import id.walt.etsi.TestCase
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.sdjwt.SDJwt
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*

private val log = KotlinLogging.logger {}

data class ContentValidationResult(
    val testCaseId: String,
    val testCaseFound: Boolean,
    val headerValidation: FieldValidation?,
    val payloadValidation: FieldValidation?,
    val namespaceValidation: FieldValidation?,
    val overallValid: Boolean,
    val summary: String
)

data class FieldValidation(
    val sectionName: String,
    val requiredFields: List<String>,
    val presentFields: List<String>,
    val missingFields: List<String>,
    val valid: Boolean
)

object ContentValidator {

    fun findTestCase(scrapedData: ScrapedData, testCaseId: String): TestCase? {
        for (format in scrapedData.formats) {
            for (profile in format.profiles) {
                val testCase = profile.testCases.find { 
                    it.id.equals(testCaseId, ignoreCase = true) 
                }
                if (testCase != null) return testCase
            }
        }
        return null
    }

    fun validateSdJwtVcContent(
        sdJwtString: String,
        testCase: TestCase
    ): ContentValidationResult {
        log.debug { "Validating SD-JWT-VC content against test case: ${testCase.id}" }
        
        return try {
            val sdJwt = SDJwt.parse(sdJwtString)
            val header = sdJwt.header
            val payload = sdJwt.fullPayload
            
            val headerValidation = testCase.getProtectedHeader()?.let { section ->
                validateFields(
                    sectionName = "Protected Header",
                    requiredItems = section.items,
                    actualFields = header.keys.toList(),
                    actualObject = header
                )
            }
            
            val payloadValidation = testCase.getPayload()?.let { section ->
                validateFields(
                    sectionName = "Payload",
                    requiredItems = section.items,
                    actualFields = payload.keys.toList(),
                    actualObject = payload
                )
            }
            
            val allValid = (headerValidation?.valid ?: true) && (payloadValidation?.valid ?: true)
            
            val summary = buildString {
                append("Test case ${testCase.id}: ")
                if (allValid) {
                    append("All required fields present")
                } else {
                    val missing = mutableListOf<String>()
                    headerValidation?.missingFields?.let { missing.addAll(it.map { f -> "header.$f" }) }
                    payloadValidation?.missingFields?.let { missing.addAll(it.map { f -> "payload.$f" }) }
                    append("Missing fields: ${missing.joinToString(", ")}")
                }
            }
            
            ContentValidationResult(
                testCaseId = testCase.id,
                testCaseFound = true,
                headerValidation = headerValidation,
                payloadValidation = payloadValidation,
                namespaceValidation = null,
                overallValid = allValid,
                summary = summary
            )
        } catch (e: Exception) {
            log.error(e) { "Failed to parse SD-JWT for content validation" }
            ContentValidationResult(
                testCaseId = testCase.id,
                testCaseFound = true,
                headerValidation = null,
                payloadValidation = null,
                namespaceValidation = null,
                overallValid = false,
                summary = "Failed to parse SD-JWT: ${e.message}"
            )
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun validateMdocContent(
        cborBytes: ByteArray,
        testCase: TestCase
    ): ContentValidationResult {
        log.debug { "Validating mdoc content against test case: ${testCase.id}" }
        
        return try {
            val issuerSigned = coseCompliantCbor.decodeFromByteArray<IssuerSigned>(cborBytes)
            val mso = issuerSigned.decodeMobileSecurityObject()
            
            // Extract namespace data elements
            val namespaceFields = mutableListOf<String>()
            issuerSigned.namespaces?.forEach { (namespace, items) ->
                items.entries.forEach { item ->
                    namespaceFields.add("${namespace}:${item.value.elementIdentifier}")
                    namespaceFields.add(item.value.elementIdentifier)
                }
            }
            
            // Get protected header fields from issuerAuth
            val protectedHeaderFields = mutableListOf<String>()
            try {
                val protectedBytes = issuerSigned.issuerAuth.protected
                if (protectedBytes.isNotEmpty()) {
                    val protectedHeaders = coseCompliantCbor.decodeFromByteArray<Map<Int, Any>>(protectedBytes)
                    protectedHeaders.keys.forEach { key ->
                        protectedHeaderFields.add(coseHeaderKeyToName(key))
                    }
                }
            } catch (e: Exception) {
                log.debug { "Could not decode protected headers: ${e.message}" }
            }
            
            // Add unprotected header fields
            issuerSigned.issuerAuth.unprotected.let { unprotected ->
                if (unprotected.algorithm != null) protectedHeaderFields.add("alg")
                if (unprotected.x5chain != null) protectedHeaderFields.add("x5chain")
            }
            
            val headerValidation = testCase.getProtectedHeader()?.let { section ->
                validateMdocFields(
                    sectionName = "Protected Header",
                    requiredItems = section.items,
                    actualFields = protectedHeaderFields
                )
            }
            
            val namespaceValidation = testCase.getNamespace()?.let { section ->
                validateMdocFields(
                    sectionName = "NameSpace",
                    requiredItems = section.items,
                    actualFields = namespaceFields
                )
            }
            
            // Also check MSO payload if specified
            val msoValidation = testCase.getMsoPayload()?.let { section ->
                val msoFields = listOf(
                    "version", "digestAlgorithm", "docType", "valueDigests", 
                    "deviceKeyInfo", "validityInfo"
                )
                validateMdocFields(
                    sectionName = section.title,
                    requiredItems = section.items,
                    actualFields = msoFields
                )
            }
            
            val allValid = (headerValidation?.valid ?: true) && 
                          (namespaceValidation?.valid ?: true) &&
                          (msoValidation?.valid ?: true)
            
            val summary = buildString {
                append("Test case ${testCase.id} (docType: ${mso.docType}): ")
                if (allValid) {
                    append("All required fields present")
                } else {
                    val missing = mutableListOf<String>()
                    headerValidation?.missingFields?.let { missing.addAll(it.map { f -> "header.$f" }) }
                    namespaceValidation?.missingFields?.let { missing.addAll(it.map { f -> "ns.$f" }) }
                    msoValidation?.missingFields?.let { missing.addAll(it.map { f -> "mso.$f" }) }
                    append("Missing fields: ${missing.joinToString(", ")}")
                }
            }
            
            ContentValidationResult(
                testCaseId = testCase.id,
                testCaseFound = true,
                headerValidation = headerValidation,
                payloadValidation = msoValidation,
                namespaceValidation = namespaceValidation,
                overallValid = allValid,
                summary = summary
            )
        } catch (e: Exception) {
            log.error(e) { "Failed to parse mdoc for content validation" }
            ContentValidationResult(
                testCaseId = testCase.id,
                testCaseFound = true,
                headerValidation = null,
                payloadValidation = null,
                namespaceValidation = null,
                overallValid = false,
                summary = "Failed to parse mdoc: ${e.message}"
            )
        }
    }

    private fun validateFields(
        sectionName: String,
        requiredItems: List<String>,
        actualFields: List<String>,
        actualObject: JsonObject
    ): FieldValidation {
        val normalizedRequired = requiredItems.map { normalizeFieldName(it) }
        val normalizedActual = actualFields.map { it.lowercase() }
        
        val presentFields = mutableListOf<String>()
        val missingFields = mutableListOf<String>()
        
        for ((index, required) in normalizedRequired.withIndex()) {
            val originalName = requiredItems[index]
            
            // Check if field is present (with various matching strategies)
            val isPresent = normalizedActual.any { actual ->
                actual == required ||
                actual.contains(required) ||
                required.contains(actual)
            } || checkSpecialField(originalName, actualObject)
            
            if (isPresent) {
                presentFields.add(originalName)
            } else {
                missingFields.add(originalName)
            }
        }
        
        return FieldValidation(
            sectionName = sectionName,
            requiredFields = requiredItems,
            presentFields = presentFields,
            missingFields = missingFields,
            valid = missingFields.isEmpty()
        )
    }

    private fun validateMdocFields(
        sectionName: String,
        requiredItems: List<String>,
        actualFields: List<String>
    ): FieldValidation {
        val presentFields = mutableListOf<String>()
        val missingFields = mutableListOf<String>()
        
        for (required in requiredItems) {
            val normalizedRequired = normalizeMdocFieldName(required)
            
            val isPresent = actualFields.any { actual ->
                val normalizedActual = actual.lowercase()
                normalizedActual == normalizedRequired ||
                normalizedActual.contains(normalizedRequired) ||
                normalizedRequired.contains(normalizedActual) ||
                matchesCoseLabel(required, actual)
            }
            
            if (isPresent) {
                presentFields.add(required)
            } else {
                missingFields.add(required)
            }
        }
        
        return FieldValidation(
            sectionName = sectionName,
            requiredFields = requiredItems,
            presentFields = presentFields,
            missingFields = missingFields,
            valid = missingFields.isEmpty()
        )
    }

    private fun normalizeFieldName(field: String): String {
        // Remove annotations like "(=dc+sd-jwt)" or "(contains a jwk element)"
        return field
            .replace(Regex("\\s*\\(.*\\)"), "")
            .replace(Regex("#.*"), "")
            .trim()
            .lowercase()
    }

    private fun normalizeMdocFieldName(field: String): String {
        // Handle COSE labels like "alg (1)" or "x5chain (33)"
        return field
            .replace(Regex("\\s*\\(\\d+\\)"), "")
            .replace(Regex("\\s*\\(.*\\)"), "")
            .trim()
            .lowercase()
    }

    private fun matchesCoseLabel(required: String, actual: String): Boolean {
        // Match COSE header labels: "alg (1)" matches "alg", "x5chain (33)" matches "x5chain"
        val labelMatch = Regex("(\\w+)\\s*\\((\\d+)\\)").find(required)
        if (labelMatch != null) {
            val name = labelMatch.groupValues[1].lowercase()
            return actual.lowercase() == name
        }
        return false
    }

    private fun checkSpecialField(fieldName: String, obj: JsonObject): Boolean {
        val normalized = normalizeFieldName(fieldName)
        
        // Handle special cases
        return when {
            normalized == "cnf" && obj.containsKey("cnf") -> true
            normalized.contains("cnf") && normalized.contains("jwk") -> {
                val cnf = obj["cnf"]?.jsonObject
                cnf?.containsKey("jwk") == true
            }
            normalized == "typ" -> obj.containsKey("typ")
            normalized == "alg" -> obj.containsKey("alg")
            normalized == "x5c" -> obj.containsKey("x5c")
            normalized.contains("vct") && normalized.contains("integrity") -> obj.containsKey("vct#integrity")
            else -> false
        }
    }

    private fun coseHeaderKeyToName(key: Int): String {
        return when (key) {
            1 -> "alg"
            2 -> "crit"
            3 -> "content_type"
            4 -> "kid"
            5 -> "iv"
            6 -> "partial_iv"
            33 -> "x5chain"
            34 -> "x5t"
            35 -> "x5u"
            else -> "cose_$key"
        }
    }
}
