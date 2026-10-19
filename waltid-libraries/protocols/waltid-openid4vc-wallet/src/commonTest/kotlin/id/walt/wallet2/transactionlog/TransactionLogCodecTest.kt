package id.walt.wallet2.transactionlog

import id.walt.wallet2.stores.inmemory.InMemoryTransactionLogStore
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
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

    private val time = LocalDateTime(2026, 1, 2, 3, 4, 5)
    private val name = "Example"
    private val names = listOf(name, "Example alternative")
    private val claim = ClaimInfo("urn:eudi:pid:1", listOf("family_name"))

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
                "Presentation", "CredentialIssuance", "CredentialDeletion",
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
        assertTrue(entries[2].jsonObject["credentialDeletion"] is JsonObject)
    }

    @Test
    fun timestampsRoundTripUsingBuiltInIsoForEverySubtype() {
        for ((value, expected) in listOf(
            time to "2026-01-02T03:04:05",
            LocalDateTime(2026, 1, 2, 3, 4) to "2026-01-02T03:04:00",
            LocalDateTime(2024, 2, 29, 0, 0) to "2024-02-29T00:00:00",
            LocalDateTime(0, 1, 1, 0, 0) to "0000-01-01T00:00:00",
            LocalDateTime(9999, 12, 31, 23, 59, 59) to "9999-12-31T23:59:59",
            LocalDateTime(2026, 1, 2, 3, 4, 5, 123456789) to "2026-01-02T03:04:05.123456789",
            LocalDateTime(-1, 1, 2, 3, 4, 5) to "-0001-01-02T03:04:05",
            LocalDateTime(10000, 1, 2, 3, 4, 5) to "+10000-01-02T03:04:05",
        )) {
            val log = allTransactions().map { transaction ->
                when (transaction) {
                    is PresentationTransaction -> transaction.copy(time = value)
                    is CredentialIssuanceTransaction -> transaction.copy(time = value)
                    is CredentialDeletionTransaction -> transaction.copy(time = value)
                }
            }
            val encoded = TransactionLogCodec.encode(log)
            (Json.parseToJsonElement(encoded) as JsonArray).forEach {
                assertEquals(expected, it.jsonObject.getValue("time").jsonPrimitive.content)
            }
            assertEquals(log, TransactionLogCodec.decode(encoded))
        }
    }

    @Test
    fun rejectsInvalidTimestampStrings() {
        val encoded = TransactionLogCodec.encode(allTransactions())
        for (invalid in listOf(
            "2026-01-02T03:04:05Z",
            "2026-01-02T03:04:05+01:00",
            "2026-01-02 03:04:05",
            "2026-1-2T3:4:5",
            "2026-02-29T03:04:05",
            "2026-02-30T03:04:05",
            "2026-01-02T24:00:00",
        )) {
            val error = assertFailsWith<SerializationException>(invalid) {
                TransactionLogCodec.decode(encoded.replace(time.toString(), invalid))
            }
            assertTrue(error.message!!.contains("index 0"), error.message)
        }
    }

    @Test
    fun acceptsBuiltInIsoTimestampVariants() {
        val encoded = TransactionLogCodec.encode(allTransactions())
        for (value in listOf(
            "2026-01-02T03:04",
            "2026-01-02T03:04:05.000",
            "2026-01-02T03:04:05.123",
            "+10000-01-02T03:04:05",
        )) {
            val decoded = TransactionLogCodec.decode(encoded.replace(time.toString(), value))
            decoded.forEach { assertEquals(LocalDateTime.parse(value), it.time) }
        }
    }

    @Test
    fun inMemoryStoreUsesCanonicalTimestampsForPagination() = runTest {
        val store = InMemoryTransactionLogStore()
        val transaction = allTransactions().last() as CredentialDeletionTransaction
        val entries = listOf(
            transaction.copy(transactionIdentifier = "1", time = LocalDateTime(2026, 1, 2, 3, 4)),
            transaction.copy(transactionIdentifier = "2", time = LocalDateTime(2026, 1, 2, 3, 4)),
            transaction.copy(transactionIdentifier = "3", time = LocalDateTime(2026, 1, 2, 3, 4, 1)),
            transaction.copy(transactionIdentifier = "4", time = LocalDateTime(2026, 1, 2, 3, 4, 1, 1)),
        )
        entries.forEach { store.store(it) }
        for (order in TransactionLogSortOrder.entries) {
            val first = store.read(TransactionLogQuery(limit = 2, sortOrder = order))
            val cursor = first.nextCursor!!
            val expectedTime = if (order == TransactionLogSortOrder.Ascending) "2026-01-02T03:04:00" else "2026-01-02T03:04:01"
            assertEquals(expectedTime, cursor.sortValue)
            val second = store.read(TransactionLogQuery(sortOrder = order, cursor = cursor))
            val expected = if (order == TransactionLogSortOrder.Ascending) entries else entries.reversed()
            assertEquals(expected, first.entries + second.entries)
            val equivalentCursor = cursor.copy(sortValue = LocalDateTime.parse(cursor.sortValue).toString())
            assertEquals(second, store.read(TransactionLogQuery(sortOrder = order, cursor = equivalentCursor)))
        }
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
