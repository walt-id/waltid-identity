package id.walt.wallet2.transactionlog

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransactionLogCodecTest {

    private val time = "2026-01-02T03:04:05"
    private val party = Identifier("urn:example", "id-1")
    private val name = "Example"
    private val names = listOf(name, "Example alternative")
    private val claim = ClaimInfo("urn:eudi:pid:1", listOf("family_name"))
    private val pseudonym = Pseudonym("abc")

    private fun allTransactions(): List<Transaction> = listOf(
        PresentationTransaction(
            "1", time, TransactionResult.Completed,
            Presentation(
                listOf(claim), listOf(claim),
                interactingPartyName = names,
                intermediaryName = listOf("Intermediary", "Intermediary alternative"),
                dpaName = listOf("Authority", "Authority alternative"),
                dpaCountry = "DE",
                purpose = listOf(MultiLangString("en", "Verification")),
            ),
        ),
        CredentialIssuanceTransaction(
            "2", time, TransactionResult.Completed,
            CredentialIssuance(
                1, 1, listOf("urn:eudi:pid:1"), true,
                interactingPartyName = names, interactingPartyType = IssuerType.PIDProvider,
            ),
        ),
        CredentialDeletionTransaction(
            "3", time, TransactionResult.Completed, CredentialDeletion("urn:eudi:pid:1", name),
        ),
        OtherOperationTransaction("4", time, TransactionResult.Completed, OtherTransaction(listOf("backup"))),
        W2WPresentationRequestTransaction(
            "5", time, TransactionResult.Completed, W2WPresentationRequest(listOf(claim), listOf(claim)),
        ),
        W2WPresentationTransaction(
            "6", time, TransactionResult.NotCompleted,
            W2WPresentation(listOf(claim), emptyList(), "declined"),
        ),
        CredentialReissuanceTransaction(
            "7", time, TransactionResult.Completed, CredentialIssuance(1, 1, listOf("x"), false),
        ),
        PseudonymGenerationTransaction("8", time, TransactionResult.Completed, PseudonymGeneration(pseudonym)),
        PseudonymDeletionTransaction("9", time, TransactionResult.Completed, PseudonymDeletion(pseudonym)),
        PseudonymousAuthenticationTransaction(
            "10", time, TransactionResult.Completed,
            PseudonymousAuthentication(party, PseudonymousAuthenticationPartyType.ServiceProvider, names, pseudonym),
        ),
        CertificateIssuanceTransaction(
            "11", time, TransactionResult.Completed,
            CertificateIssuance(party, CertificateIssuerType.QCertForESigProvider, names, listOf("DE"), "01"),
        ),
        CertificateDeletionTransaction(
            "12", time, TransactionResult.Completed, CertificateDeletion("01", party, name),
        ),
        SigningSealingTransaction(
            "13", time, TransactionResult.Completed,
            SigningSealing(party, names, listOf("DE"), "s1", "01", "dtbsr"),
        ),
        DataDeletionRequestTransaction(
            "14", time, TransactionResult.Completed, DataDeletionRequest(party, names, listOf(claim)),
        ),
        DPAReportTransaction(
            "15", time, TransactionResult.Completed, DPAReport(listOf("Authority", "Authority alternative"), "DE"),
        ),
    )

    @Test
    fun roundTripsEverySubtypeWithDiscriminator() {
        val log = allTransactions()
        val text = TransactionLogCodec.encode(log)
        assertEquals(log, TransactionLogCodec.decode(text))

        val types = (Json.parseToJsonElement(text) as JsonArray)
            .map { it.jsonObject["transactionType"]!!.jsonPrimitive.content }
        assertEquals(
            listOf(
                "Presentation", "CredentialIssuance", "CredentialDeletion", "OtherTransaction",
                "W2WPresentationRequest", "W2WPresentation", "CredentialReissuance", "PseudonymGeneration",
                "PseudonymDeletion", "PseudonymPresentation", "CertificateIssuance", "CertificateDeletion",
                "SigningSealing", "DataDeletionRequest", "DPAReport",
            ),
            types,
        )
    }

    @Test
    fun encodesNameListsAndSingleStringFields() {
        val entries = Json.parseToJsonElement(TransactionLogCodec.encode(allTransactions())) as JsonArray
        val expectedNames = JsonArray(names.map { JsonPrimitive(it) })
        val expectedDpaNames = JsonArray(listOf("Authority", "Authority alternative").map { JsonPrimitive(it) })
        val expectedFields = listOf(
            Triple(0, "presentation", mapOf(
                "interactingPartyName" to expectedNames,
                "intermediaryName" to JsonArray(
                    listOf("Intermediary", "Intermediary alternative").map { JsonPrimitive(it) },
                ),
                "dpaName" to expectedDpaNames,
                "dpaCountry" to JsonPrimitive("DE"),
            )),
            Triple(1, "credentialIssuance", mapOf("interactingPartyName" to expectedNames)),
            Triple(2, "credentialDeletion", mapOf("credentialIssuerName" to JsonPrimitive(name))),
            Triple(9, "pseudonymousAuthentication", mapOf("interactingPartyName" to expectedNames)),
            Triple(10, "certificateIssuance", mapOf("interactingPartyName" to expectedNames)),
            Triple(11, "certificateDeletion", mapOf("certificateIssuerName" to JsonPrimitive(name))),
            Triple(12, "signingSealing", mapOf("interactingPartyName" to expectedNames)),
            Triple(13, "dataDeletionRequest", mapOf("interactingPartyName" to expectedNames)),
            Triple(14, "dpaReport", mapOf("dpaName" to expectedDpaNames, "dpaCountry" to JsonPrimitive("DE"))),
        )
        expectedFields.forEach { (index, detailName, fields) ->
            val detail = entries[index].jsonObject[detailName]!!.jsonObject
            fields.forEach { (field, value) ->
                assertEquals(value, detail[field], "$detailName.$field")
            }
        }
        assertTrue(entries[0].jsonObject["presentation"]!!.jsonObject["purpose"] is JsonArray)
    }

    @Test
    fun encodesFixedPartyTypesAndDetailObjectNames() {
        val entries = Json.parseToJsonElement(TransactionLogCodec.encode(allTransactions())) as JsonArray
        val presentation = entries[0].jsonObject
        assertEquals(
            "ServiceProvider",
            presentation["presentation"]!!.jsonObject["interactingPartyType"]!!.jsonPrimitive.content,
        )
        assertEquals("Completed", presentation["transactionResult"]!!.jsonPrimitive.content)
        assertTrue(entries[12].jsonObject["signingSealing"] is JsonObject)
        assertEquals(
            "ESigESealCreationProvider",
            entries[12].jsonObject["signingSealing"]!!.jsonObject["interactingPartyType"]!!.jsonPrimitive.content,
        )
    }

    @Test
    fun emptyLogRoundTrips() {
        assertEquals("[]", TransactionLogCodec.encode(emptyList()))
        assertEquals(emptyList(), TransactionLogCodec.decode("[]"))
    }

    @Test
    fun decodeWrapsValidationFailuresWithEntryIndex() {
        val text = """
            [
              {"transactionType":"CredentialDeletion","transactionIdentifier":"1","time":"$time",
               "transactionResult":"Completed","credentialDeletion":{"credentialIdentifier":"x"}},
              {"transactionType":"CredentialDeletion","transactionIdentifier":"","time":"$time",
               "transactionResult":"Completed","credentialDeletion":{"credentialIdentifier":"x"}}
            ]
        """.trimIndent()
        val error = assertFailsWith<SerializationException> { TransactionLogCodec.decode(text) }
        assertTrue(error.message!!.contains("index 1"), error.message)
        assertFailsWith<SerializationException> { TransactionLogCodec.decode("{}") }
    }

    @Test
    fun decodeToleratesUnknownKeysAndUnknownOptionalEnumValues() {
        val text = """
            [{"transactionType":"CredentialIssuance","transactionIdentifier":"1","time":"$time",
              "transactionResult":"Completed","proprietary":true,
              "credentialIssuance":{"credentialNumberRequested":1,"credentialNumberIssued":1,
                "credentialIdentifier":["x"],"isUserTriggered":true,"interactingPartyType":"FutureProvider"}}]
        """.trimIndent()
        val decoded = TransactionLogCodec.decode(text).single() as CredentialIssuanceTransaction
        assertEquals(null, decoded.credentialIssuance.interactingPartyType)
    }
}
