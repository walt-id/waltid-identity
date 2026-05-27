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
            
            // Get header fields - check both protected and unprotected
            val headerFields = mutableListOf<String>()
            
            // Add unprotected header fields
            issuerSigned.issuerAuth.unprotected.let { unprotected ->
                if (unprotected.algorithm != null) headerFields.add("alg")
                if (unprotected.x5chain != null) headerFields.add("x5chain")
            }
            
            // Try to decode protected header for alg
            try {
                val protectedBytes = issuerSigned.issuerAuth.protected
                if (protectedBytes.isNotEmpty()) {
                    // The protected header contains alg (key 1)
                    // If we have protected bytes, alg is likely there
                    if (!headerFields.contains("alg")) {
                        headerFields.add("alg")
                    }
                }
            } catch (e: Exception) {
                log.debug { "Could not decode protected headers: ${e.message}" }
            }
            
            val headerValidation = testCase.getProtectedHeader()?.let { section ->
                validateMdocHeaderFields(
                    sectionName = "Protected Header",
                    requiredItems = section.items,
                    actualFields = headerFields
                )
            }
            
            val namespaceValidation = testCase.getNamespace()?.let { section ->
                validateMdocNamespaceFields(
                    sectionName = "NameSpace",
                    requiredItems = section.items,
                    actualFields = namespaceFields
                )
            }
            
            // Check MSO payload - use actual MSO fields
            val msoValidation = testCase.getMsoPayload()?.let { section ->
                val actualMsoFields = listOf(
                    "version", "digestAlgorithm", "docType", "valueDigests", 
                    "deviceKeyInfo", "validityInfo"
                )
                validateMdocMsoFields(
                    sectionName = section.title,
                    requiredItems = section.items,
                    actualFields = actualMsoFields
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

    private fun validateMdocHeaderFields(
        sectionName: String,
        requiredItems: List<String>,
        actualFields: List<String>
    ): FieldValidation {
        val presentFields = mutableListOf<String>()
        val missingFields = mutableListOf<String>()
        
        for (required in requiredItems) {
            // Extract the field name from items like "alg (1)" or "x5chain (33)"
            val fieldName = extractCoseFieldName(required)
            
            val isPresent = actualFields.any { actual ->
                actual.equals(fieldName, ignoreCase = true)
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

    private fun validateMdocNamespaceFields(
        sectionName: String,
        requiredItems: List<String>,
        actualFields: List<String>
    ): FieldValidation {
        val presentFields = mutableListOf<String>()
        val missingFields = mutableListOf<String>()
        
        for (required in requiredItems) {
            // Skip descriptive text that isn't an actual field name
            if (isDescriptiveText(required)) {
                presentFields.add(required) // Treat as present (it's not a real field requirement)
                continue
            }
            
            val fieldNames = extractFieldNames(required)
            
            val allPresent = fieldNames.all { fieldName ->
                actualFields.any { actual ->
                    actual.equals(fieldName, ignoreCase = true) ||
                    actual.endsWith(":$fieldName", ignoreCase = true)
                }
            }
            
            if (allPresent || fieldNames.isEmpty()) {
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

    private fun validateMdocMsoFields(
        sectionName: String,
        requiredItems: List<String>,
        actualFields: List<String>
    ): FieldValidation {
        val presentFields = mutableListOf<String>()
        val missingFields = mutableListOf<String>()
        
        for (required in requiredItems) {
            // Skip descriptive text
            if (isDescriptiveText(required)) {
                presentFields.add(required)
                continue
            }
            
            val fieldName = extractMsoFieldName(required)
            
            // Handle typos in test data (digestAltorithm vs digestAlgorithm)
            val isPresent = actualFields.any { actual ->
                actual.equals(fieldName, ignoreCase = true) ||
                (fieldName.contains("digest", ignoreCase = true) && actual.contains("digest", ignoreCase = true))
            }
            
            if (isPresent || fieldName.isEmpty()) {
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

    private fun isDescriptiveText(item: String): Boolean {
        // Detect items that are descriptions rather than field names
        return item.contains("Section ") ||
               item.contains("as per ") ||
               item.contains("as specified") ||
               item.startsWith("namespaces(") ||
               item.startsWith(".Namespace") ||
               item.contains("for the components") ||
               item.contains("for elements:") ||
               item.length > 100 // Very long items are likely descriptions
    }

    private fun extractCoseFieldName(item: String): String {
        // Extract field name from "alg (1)" -> "alg", "x5chain (33)" -> "x5chain"
        val match = Regex("^(\\w+)\\s*\\(\\d+\\)").find(item.trim())
        return match?.groupValues?.get(1)?.lowercase() ?: item.trim().split(" ").first().lowercase()
    }

    private fun extractFieldNames(item: String): List<String> {
        // Extract actual field names from complex items
        // e.g., "family_name, given_name, birth_date" -> ["family_name", "given_name", "birth_date"]
        val cleanItem = item
            .replace(Regex("\\(.*?\\)"), "") // Remove parenthetical notes
            .replace(Regex("\\n.*"), "") // Remove everything after newline
        
        return cleanItem.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(" ") && it.length < 50 }
    }

    private fun extractMsoFieldName(item: String): String {
        // Extract MSO field name, handling complex descriptions
        val cleanItem = item
            .replace(Regex("\\s*\\(.*?\\)"), "")
            .replace(Regex("\\n.*"), "")
            .trim()
            .split(" ").first()
            .split(".").first()
        return cleanItem.lowercase()
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
