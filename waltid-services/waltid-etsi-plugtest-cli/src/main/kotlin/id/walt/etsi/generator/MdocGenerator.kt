package id.walt.etsi.generator

import id.walt.cose.CoseCertificate
import id.walt.cose.CoseMac0
import id.walt.cose.CoseHeaders
import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.etsi.TestCase
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.elements.DeviceNameSpaces
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
        val document: Document
    )

    private val defaultValueMappingFunction: (docType: String, namespace: String, elementIdentifier: String, elementValueJson: JsonElement) -> CborElement? =
        { _, _, _, elementValueJson ->
            if (elementValueJson is JsonPrimitive || elementValueJson is JsonNull) {
                elementValueJson.jsonToCborElement()
            } else {
                elementValueJson.jsonToCborElement()
            }
        }

    suspend fun generate(
        testCase: TestCase,
        issuerKey: Key,
        issuerCertificatePem: String,
        holderKey: Key,
        sampleData: Map<String, JsonObject>? = null
    ): MdocGenerationResult {
        log.info { "Generating mdoc for test case: ${testCase.id}" }

        val issuerCertBytes = parsePemCertificate(issuerCertificatePem)
        val issuerCertCose = listOf(CoseCertificate(issuerCertBytes))
        val holderCoseKey = (holderKey as JWKKey).getPublicKey().getCosePublicKey()

        val docType = determineDocType(testCase)
        val namespaces = sampleData ?: buildNamespacesFromTestCase(testCase)

        log.debug { "DocType: $docType" }
        log.debug { "Namespaces: ${namespaces.keys}" }

        val data = MdocIssuer.MdocUniversalIssuanceData(namespaces = namespaces)

        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            issuerCertificate = issuerCertCose,
            holderKey = holderCoseKey,
            docType = docType,
            data = data,
            valueMappingFunction = defaultValueMappingFunction
        )

        val document = Document(
            docType = docType,
            issuerSigned = issuerSigned,
            deviceSigned = DeviceSigned(
                ByteStringWrapper(DeviceNameSpaces(mapOf())),
                DeviceAuth(deviceMac = CoseMac0(ByteArray(0), CoseHeaders(), ByteArray(0), ByteArray(0)))
            )
        )

        val cborBytes = coseCompliantCbor.encodeToByteArray(document)
        val cborHex = cborBytes.toHexString()

        return MdocGenerationResult(
            testCaseId = testCase.id,
            cborHex = cborHex,
            cborBytes = cborBytes,
            document = document
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

        val namespace = when {
            docType == "org.iso.18013.5.1.mDL" -> "org.iso.18013.5.1"
            docType.contains("23220") -> "org.iso.23220.1"
            else -> "org.etsi.01947201.010101"
        }

        val namespaceSection = testCase.getNamespace()
        
        val attributes = buildJsonObject {
            put("family_name", "Mustermann")
            put("given_name", "Max")
            put("birth_date", "1990-01-15")
            put("issue_date", "2024-01-01")
            put("expiry_date", "2034-01-01")
            put("issuing_country", "DE")
            put("issuing_authority", "Test Authority")
            put("document_number", "DOC123456789")

            if (testCase.id.contains("QEAA")) {
                put("category", "urn:etsi:esi:eaa:eu:qualified")
            }

            if (testCase.isOneTime) {
                put("oneTime", true)
            }

            if (testCase.isShortLived) {
                put("shortLived", true)
            }

            namespaceSection?.items?.forEach { item ->
                val cleanItem = item.trim().split(" ").first().split("(").first()
                if (!this@buildJsonObject.toString().contains("\"$cleanItem\"") && !cleanItem.contains("namespace", ignoreCase = true)) {
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

        return mapOf(namespace to attributes)
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
