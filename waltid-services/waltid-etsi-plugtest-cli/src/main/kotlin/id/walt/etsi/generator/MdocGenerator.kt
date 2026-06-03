package id.walt.etsi.generator

import id.walt.cose.CoseCertificate
import id.walt.cose.CoseCertHash
import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.crypto.utils.ShaUtils
import id.walt.etsi.TestCase
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.mso.Status
import id.walt.mdoc.objects.mso.UniformResourceIdentifier
import id.walt.mdoc.schema.MdocsSchemaMappingFunction.jsonToCborElement
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalSerializationApi::class)
object MdocGenerator {

    data class MdocGenerationResult(
        val testCaseId: String,
        val cborHex: String,
        val cborBytes: ByteArray,
        val issuerSigned: IssuerSigned
    )

    private val defaultValueMappingFunction: (docType: String, namespace: String, elementIdentifier: String, elementValueJson: JsonElement) -> CborElement? =
        { _, _, _, elementValueJson ->
            elementValueJson.jsonToCborElement()
        }

    /**
     * Generates an mdoc in IssuerSigned format as required by ETSI plugtest.
     * 
     * Per the test case notes:
     * "ISO-mdoc QEAA shall be an instance of IssuerSigned type as per section 10.3.3 of ISO 18013-5, clause 8.3.2.1.2.2"
     * 
     * This means we output just the IssuerSigned structure, NOT the full Document wrapper.
     */
    suspend fun generate(
        testCase: TestCase,
        issuerKey: Key,
        issuerCertificatePem: String,
        holderKey: Key,
        sampleData: Map<String, JsonObject>? = null,
        /**
         * URL where the issuer (signing) certificate can be retrieved. Used to populate the COSE
         * `x5u` protected-header parameter for QEAA/PuB-EAA (QEAA-6.6.2-02 / PuB-EAA-6.6.3-02).
         */
        x5u: String = "https://raw.githubusercontent.com/walt-id/etsi-plugtest-static-files/refs/heads/main/cert.pem"
    ): MdocGenerationResult {
        log.info { "Generating mdoc (IssuerSigned format) for test case: ${testCase.id}" }

        val issuerCertBytes = parsePemCertificate(issuerCertificatePem)
        val issuerCertCose = listOf(CoseCertificate(issuerCertBytes))
        val holderCoseKey = (holderKey as JWKKey).getPublicKey().getCosePublicKey()

        val docType = determineDocType(testCase)
        val namespaces = sampleData ?: buildNamespacesFromTestCase(testCase)

        log.debug { "DocType: $docType" }
        log.debug { "Namespaces: ${namespaces.keys}" }

        val data = MdocIssuer.MdocUniversalIssuanceData(namespaces = namespaces)

        // ISO 18013-5 §9.1.2.6: some test cases require MSO.status.identifier_list
        // (MDL-EAA-6, MDOC-EAA-9). The uri points to the identifier list revocation token.
        // Additionally, per QEAA-6.2.10.2-01 / PuB-EAA-6.2.10.3-01 a QEAA/PuBEAA SHALL include the
        // status component when it does not contain the short-lived component.
        // We host a minimal (empty, no revocations) identifier list at the static files repo.
        val isQeaaOrPubEaa = testCase.id.contains("QEAA", ignoreCase = true) ||
                testCase.id.contains("PuBEAA", ignoreCase = true)
        val requiresStatus =
            (testCase.id.contains("-6") && testCase.id.startsWith("MDL-EAA")) ||
            (testCase.id.contains("-9") && testCase.id.startsWith("MDOC-EAA")) ||
            (isQeaaOrPubEaa && !testCase.isShortLived)
        val msoStatus: Status? = if (requiresStatus) {
            Status(identifierList = Status.IdentifierListInfo(
                id = testCase.id,
                uri = UniformResourceIdentifier(
                    "https://raw.githubusercontent.com/walt-id/etsi-plugtest-static-files/refs/heads/main/identifier-list.cwt"
                )
            ))
        } else null

        // QEAA-6.6.2-02 / PuB-EAA-6.6.3-02: the CB-AdES protected header of an ISO-mdoc QEAA/PuB-EAA
        // SHALL contain x5u and x5t (RFC 9360); the x5t digest SHALL be SHA-256 (QEAA-6.6.2-03).
        val protectedX5u: String?
        val protectedX5t: CoseCertHash?
        if (isQeaaOrPubEaa) {
            protectedX5u = x5u
            protectedX5t = CoseCertHash(
                hashAlgorithm = CoseCertHash.SHA_256,
                hashValue = ShaUtils.sha256(issuerCertBytes)
            )
        } else {
            protectedX5u = null
            protectedX5t = null
        }

        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            issuerCertificate = issuerCertCose,
            holderKey = holderCoseKey,
            docType = docType,
            data = data,
            valueMappingFunction = defaultValueMappingFunction,
            status = msoStatus,
            protectedHeaderX5u = protectedX5u,
            protectedHeaderX5t = protectedX5t
        )

        // ETSI plugtest expects IssuerSigned format, NOT the full Document wrapper
        val cborBytes = coseCompliantCbor.encodeToByteArray(issuerSigned)
        val cborHex = cborBytes.toHexString()

        log.debug { "Generated IssuerSigned CBOR (${cborBytes.size} bytes)" }

        return MdocGenerationResult(
            testCaseId = testCase.id,
            cborHex = cborHex,
            cborBytes = cborBytes,
            issuerSigned = issuerSigned
        )
    }

    private fun determineDocType(testCase: TestCase): String {
        val namespaceSection = testCase.getNamespace()
        val description = testCase.description.lowercase()

        return when {
            testCase.id.startsWith("MDL") -> "org.iso.18013.5.1.mDL"
            description.contains("mdl") -> "org.iso.18013.5.1.mDL"
            namespaceSection?.items?.any { it.contains("org.iso.18013.5.1") } == true -> "org.iso.18013.5.1.mDL"
            namespaceSection?.items?.any { it.contains("org.iso.23220") } == true -> "org.iso.23220.photoid.1"
            testCase.id.contains("QEAA") -> "org.etsi.qeaa.1"
            testCase.id.contains("PuBEAA") -> "org.etsi.pubeaa.1"
            else -> "org.etsi.eaa.1"
        }
    }

    private fun buildNamespacesFromTestCase(testCase: TestCase): Map<String, JsonObject> {
        val docType = determineDocType(testCase)

        return when {
            // mDL uses a single ISO 18013-5 namespace
            docType == "org.iso.18013.5.1.mDL" -> {
                val ns = buildJsonObject {
                    put("family_name", "Mustermann")
                    put("given_name", "Max")
                    put("birth_date", "1990-01-15")
                    put("issue_date", "2024-01-01")
                    put("expiry_date", "2034-01-01")
                    put("issuing_country", "DE")
                    put("issuing_authority", "Test Authority")
                    put("document_number", "DOC123456789")
                    put("portrait", "")
                    put("driving_privileges", "")
                    put("distinguishing_sign", "DE")

                    // Extra optional elements from the test case description
                    val namespaceSection = testCase.getNamespace()
                    namespaceSection?.items?.forEach { item ->
                        val cleanItem = item.trim().split(" ").first().split("(").first()
                        if (cleanItem.isNotBlank() && !cleanItem.contains("namespace", ignoreCase = true) &&
                            !cleanItem.contains("namespaces", ignoreCase = true) &&
                            !this@buildJsonObject.toString().contains("\"$cleanItem\"")) {
                            when {
                                cleanItem.contains("date", ignoreCase = true) -> put(cleanItem, "2024-01-01")
                                cleanItem.contains("country", ignoreCase = true) -> put(cleanItem, "DE")
                                cleanItem.contains("authority", ignoreCase = true) -> put(cleanItem, "Test Authority")
                                cleanItem.contains("number", ignoreCase = true) -> put(cleanItem, "123456789")
                                cleanItem.contains("name", ignoreCase = true) -> put(cleanItem, "Test Name")
                                else -> put(cleanItem, "test_value")
                            }
                        }
                    }
                }
                mapOf("org.iso.18013.5.1" to ns)
            }

            // MDOC-EAA/QEAA/PuBEAA: two namespaces per ETSI TS 119 472-1
            else -> {
                // ISO 23220-1 namespace: base personal attributes
                val isoNs = buildJsonObject {
                    put("family_name", "Mustermann")
                    put("given_name", "Max")
                    put("birth_date", "1990-01-15")
                    put("issue_date", "2024-01-01")
                    put("expiry_date", "2034-01-01")
                    put("issuing_country", "DE")
                    // ISO 23220-2 requires the unicode variant for issuing_authority
                    put("issuing_authority_unicode", "Test Authority")
                    put("document_number", "DOC123456789")
                }

                // ETSI namespace: category and ETSI-specific extensions
                val etsiNs = buildJsonObject {
                    // category is mandatory for QEAA and PuBEAA; absent for plain EAA
                    when {
                        testCase.id.contains("QEAA", ignoreCase = true) ->
                            put("category", "urn:etsi:esi:eaa:eu:qualified")
                        testCase.id.contains("PuBEAA", ignoreCase = true) ->
                            put("category", "urn:etsi:esi:eaa:eu:pub")
                        // plain EAA: no category claim
                    }

                    if (testCase.isOneTime) put("oneTime", true)
                    if (testCase.isShortLived) put("shortLived", true)
                    if (testCase.isPseudonym) put("also_known_as", "pseudonym:user123")

                    // Add any remaining ETSI-specific fields from the test case
                    val namespaceSection = testCase.getNamespace()
                    namespaceSection?.items?.forEach { item ->
                        val cleanItem = item.trim().split(" ").first().split("(").first()
                        // Only add items that look like ETSI-specific field names
                        val etsiFields = setOf("iss_reg_id", "status_service", "also_known_as", "oneTime", "shortLived", "category")
                        if (cleanItem in etsiFields && !this@buildJsonObject.toString().contains("\"$cleanItem\"")) {
                            when (cleanItem) {
                                // ETSI EN 319 412-1 §5.1.4 registration identifier format:
                                // <3-letter scheme><2-letter ISO 3166-1 country>-<reference>.
                                "iss_reg_id" -> put("iss_reg_id", "VATAT-U12345678")
                                "status_service" -> put("status_service", "https://status.example.com")
                                else -> {} // already handled above
                            }
                        }
                    }
                }

                mapOf(
                    "org.iso.23220.1" to isoNs,
                    "org.etsi.01947201.010101" to etsiNs
                )
            }
        }
    }

    private fun parsePemCertificate(pem: String): ByteArray {
        val base64Content = pem
            .replace("-----BEGIN CERTIFICATE-----", "")
            .replace("-----END CERTIFICATE-----", "")
            .replace("\n", "")
            .replace("\r", "")
            .trim()
        return base64Content.decodeFromBase64()
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }
}
