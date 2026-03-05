@file:OptIn(ExperimentalSerializationApi::class)

import MdocIssuanceTest.Companion.holderKeyInit
import MdocIssuanceTest.Companion.issuerCertCose
import MdocIssuanceTest.Companion.issuerKeyInit
import MdocIssuanceTest.Companion.makeDocument
import MdocIssuanceTest.Companion.verifyIssued
import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseVerifier
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.schema.MdocsSchema
import id.walt.mdoc.schema.MdocsSchema.MdocsDatatype.*
import id.walt.mdoc.schema.MdocsSchema.MdocsSchemaType
import id.walt.mdoc.schema.MdocsSchemaMappingFunction
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.time.Clock

class MdocSchemaTest {

    val docType = "walt.id.credential"

    val ns1 = "walt.id.credential.ns1"
    val ns2 = "waltid.credential.nsspecial"

    val schema = MdocsSchema(
        mapOf(
            docType to mapOf(
                ns1 to mapOf(
                    "name" to MdocsSchemaType(STRING),
                    "portrait" to MdocsSchemaType(BYTES),
                    "measurement" to MdocsSchemaType(INT),
                    "count" to MdocsSchemaType(LONG),
                    "height" to MdocsSchemaType(UINT),
                    "flag" to MdocsSchemaType(BOOLEAN),
                    "birth_date" to MdocsSchemaType(DATE),
                    "issue_date" to MdocsSchemaType(DATETIME)
                ),
                ns2 to mapOf(
                    "nationalities" to MdocsSchemaType(ARRAY, STRING),
                    "organization_location" to MdocsSchemaType(MAP, STRING),
                    "driving_privileges" to MdocsSchemaType(ARRAY, MdocsSchemaType(MAP, STRING))
                )
            )
        )
    )

    @Test
    fun testIssuanceWithSchema() = runTest {
        val issuerKey = issuerKeyInit()
        val holderKey = holderKeyInit().getPublicKey().getCosePublicKey()
        val issuerPublicKey = issuerKey.getPublicKey()
        val issuerPublicCoseVerifier = issuerPublicKey.toCoseVerifier()


        val credentialData = MdocIssuer.MdocUniversalIssuanceData(
            namespaces = mapOf(
                "walt.id.credential.ns1" to buildJsonObject {
                    put("name", "Max Mustermann") // tstr
                    put("portrait", JsonArray(byteArrayOf(1, 2, 3).map { JsonPrimitive(it) })) // bstr
                    put("measurement", 5)
                    put("count", 123L)
                    put("height", JsonPrimitive(80u)) // uint
                    put("flag", true) // bool
                    put("birth_date", LocalDate(2000, 12, 30).toString()) // full-date
                    put("issue_date", Clock.System.now().toString()) // tdate
                },
                "waltid.credential.nsspecial" to buildJsonObject {
                    putJsonArray("nationalities") {
                        add("AT")
                        add("DE")
                    }
                    putJsonObject("organization_location") {
                        put("address", "Abc St. 1")
                        put("city", "Stadt")
                        put("state", "Staat")
                        put("postal_code", "A1234")
                    }
                    putJsonArray("driving_privileges") {
                        add(buildJsonObject {
                            put("vehicle_category_code", "B")
                            put("number_plate", "ABC123")
                        })
                    }
                })
        )

        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            issuerCertificate = issuerCertCose,
            holderKey = holderKey,
            docType = docType,
            data = credentialData,
            valueMappingFunction = MdocsSchemaMappingFunction.schemaAwareValueMappingFunction(schema)
        )

        println("Issued: $issuerSigned")

        val document = makeDocument(docType, issuerSigned)
        val documentHex = coseCompliantCbor.encodeToHexString(document)
        println("Document (\"sending\"): $documentHex")

        val decodedDocument = coseCompliantCbor.decodeFromHexString<Document>(documentHex)
        println("Decoded:  $decodedDocument")

        verifyIssued(
            document = decodedDocument,
            docType = docType,
            issuerPublicCoseVerifier = issuerPublicCoseVerifier,
            namespacesToCheck = listOf(ns1, ns2),
            holderKey = holderKey,
            namespaces = credentialData.namespaces
        )
    }

}
