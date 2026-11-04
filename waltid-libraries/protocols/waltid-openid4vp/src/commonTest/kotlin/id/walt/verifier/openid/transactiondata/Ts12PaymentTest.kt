package id.walt.verifier.openid.transactiondata

import kotlinx.serialization.json.*
import kotlin.test.*
import kotlin.time.Instant

class Ts12PaymentTest {
    private val now = Instant.parse("2026-11-04T10:00:00Z")
    private val base = Json.parseToJsonElement("""
        {"transaction_id":"reference","payee":{"name":"Shop","id":"merchant"},"currency":"EUR","amount":11.56}
    """).jsonObject
    private fun parse(extra: String = "{}", currency: String = "EUR", amount: String = "11.56"): Ts12Payment =
        Ts12Payment.validate(JsonObject(base + mapOf(
            "currency" to JsonPrimitive(currency), "amount" to Json.parseToJsonElement(amount)) + Json.parseToJsonElement(extra).jsonObject), now)

    @Test fun supportsEveryOptionalLeafWithIndependentExpectedValues() {
        val payment = parse("""{
            "date_time":"2026-11-04T09:00:00+01:00",
            "payee":{"name":"Shop","id":"merchant","logo":"data:image/png;base64,AA==","website":"https://shop.example"},
            "pisp":{"legal_name":"Payments Ltd","brand_name":"Payments","domain_name":"pay.example"},
            "amount_estimated":true,"amount_earmarked":false,"sct_inst":true,
            "recurrence":{"start_date":"2027-01-01T12:00:00Z","end_date":"2028-01-01T12:00:00Z","number":2e0,"frequency":"MNTH",
              "mit_options":{"amount_variable":false,"min_amount":-1.23,"max_amount":9007199254740993.01,
                "total_amount":0,"initial_amount":1.2300,"initial_amount_number":2.0,"apr":0.1234567890123456789}}
        }""")
        val expected = mapOf(
            "transaction_id" to "reference", "payee/name" to "Shop", "payee/id" to "merchant", "currency" to "EUR", "amount" to "11.56",
            "date_time" to "2026-11-04T09:00:00+01:00", "payee/logo" to "data:image/png;base64,AA==", "payee/website" to "https://shop.example",
            "pisp/legal_name" to "Payments Ltd", "pisp/brand_name" to "Payments", "pisp/domain_name" to "pay.example",
            "amount_estimated" to "true", "amount_earmarked" to "false", "sct_inst" to "true",
            "recurrence/start_date" to "2027-01-01T12:00:00Z", "recurrence/end_date" to "2028-01-01T12:00:00Z",
            "recurrence/number" to "2", "recurrence/frequency" to "MNTH", "recurrence/mit_options/amount_variable" to "false",
            "recurrence/mit_options/min_amount" to "-1.23", "recurrence/mit_options/max_amount" to "9007199254740993.01",
            "recurrence/mit_options/total_amount" to "0", "recurrence/mit_options/initial_amount" to "1.23",
            "recurrence/mit_options/initial_amount_number" to "2", "recurrence/mit_options/apr" to "0.1234567890123456789",
        )
        assertEquals(expected, payment.fields.mapKeys { it.key.joinToString("/") })
        fun schemaLeaves(node: JsonObject, prefix: String = ""): Set<String> =
            node.getValue("properties").jsonObject.flatMap { (name, definition) ->
                val path = if (prefix.isEmpty()) name else "$prefix/$name"
                if (definition.jsonObject["type"] == JsonPrimitive("object")) schemaLeaves(definition.jsonObject, path)
                else setOf(path)
            }.toSet()
        assertEquals(expected.keys + "execution_date", schemaLeaves(Ts12Payment.schema),
            "An upstream schema update must also update the independently specified payment fixtures")
        assertIs<Ts12PaymentSchedule.Recurring>(payment.schedule)
    }

    @Test fun schedulesUseInstantsAndMustRemainCurrent() {
        assertIs<Ts12PaymentSchedule.Unscheduled>(parse().schedule)
        val scheduled = parse("""{"execution_date":"2026-11-04T11:00:00+01:00"}""")
        assertIs<Ts12PaymentSchedule.Scheduled>(scheduled.schedule)
        scheduled.requireCurrent(now)
        assertEquals("/execution_date", assertFailsWith<Ts12PaymentValidationException> {
            scheduled.requireCurrent(Instant.parse("2026-11-04T10:00:00.001Z"))
        }.path)
        for (extra in listOf(
            """{"execution_date":"2026-11-04T09:59:59Z"}""",
            """{"execution_date":"2027-01-01"}""",
            """{"execution_date":"2027-01-01T00:00:00Z","recurrence":{"frequency":"MNTH"}}""",
        )) assertFailsWith<Ts12PaymentValidationException> { parse(extra) }
    }

    @Test fun recurrenceSupportsTheWholeFrequencyEnumerationWithoutExtraBusinessRules() {
        val codes = listOf("INDA","DAIL","WEEK","TOWK","TWMN","MNTH","TOMN","QUTR","FOMN","SEMI","YEAR","TYEA")
        val published = Ts12Payment.schema.getValue("properties").jsonObject.getValue("recurrence").jsonObject
            .getValue("properties").jsonObject.getValue("frequency").jsonObject.getValue("enum").jsonArray
        assertEquals(codes.toSet(), published.map { it.jsonPrimitive.content }.toSet())
        assertEquals(codes.toSet(), Ts12PaymentFrequency.entries.map { it.name }.toSet())
        for (code in codes) {
            val parsed = parse("""{"recurrence":{"frequency":"$code","number":-2,"mit_options":{"initial_amount_number":0}}}""")
            assertEquals(code, assertIs<Ts12PaymentSchedule.Recurring>(parsed.schedule).frequency.name)
        }
    }

    @Test fun malformedAndUnknownFieldsAreRejectedAtTheirActualBoundary() {
        val invalid = listOf(
            """{"amount":null}""", """{"date_time":"2026-02-30T12:00:00Z"}""",
            """{"payee":{"name":"Shop","id":"merchant","website":"relative/path"}}""",
            """{"pisp":{"legal_name":"Payments"}}""", """{"sct_inst":"false"}""",
            """{"recurrence":null}""", """{"recurrence":{}}""", """{"recurrence":{"frequency":"OTHER"}}""",
            """{"recurrence":{"frequency":"DAIL","start_date":"2027-01-01"}}""",
            """{"recurrence":{"frequency":"DAIL","end_date":false}}""",
            """{"recurrence":{"frequency":"DAIL","number":2.1}}""",
            """{"recurrence":{"frequency":"DAIL","mit_options":{"initial_amount_number":0.1}}}""",
            """{"recurrence":{"frequency":"DAIL","mit_options":{"min_amount":1.001}}}""",
            """{"recurrence":{"frequency":"DAIL","mit_options":{"amount_variable":"true"}}}""",
            """{"recurrence":{"frequency":"DAIL","mit_options":{"apr":"1"}}}""",
        )
        for (extra in invalid) assertFailsWith<Ts12PaymentValidationException>(extra) { parse(extra) }
        for (extra in listOf(
            """{"unknown":1}""", """{"payee":{"name":"Shop","id":"merchant","unknown":1}}""",
            """{"pisp":{"legal_name":"P","brand_name":"P","domain_name":"p.example","unknown":1}}""",
            """{"recurrence":{"frequency":"DAIL","unknown":1}}""",
            """{"recurrence":{"frequency":"DAIL","mit_options":{"unknown":1}}}""",
        )) assertTrue(assertFailsWith<Ts12PaymentValidationException>(extra) { parse(extra) }.unsupported)
        val diagnostic = assertFailsWith<Ts12PaymentValidationException> {
            parse("""{"unknown/path~name":"private-payment-value"}""")
        }
        assertEquals("/unknown~1path~0name", diagnostic.path)
        assertFalse(diagnostic.message.orEmpty().contains("private-payment-value"))
        for (field in listOf("transaction_id","payee","currency","amount")) assertFailsWith<Ts12PaymentValidationException> {
            Ts12Payment.validate(JsonObject(base - field), now)
        }
    }

    @Test fun exactDecimalsAndMathematicalIntegersRespectCurrencyPrecision() {
        for ((currency, amount, expected) in listOf(
            Triple("JPY","12","12"), Triple("EUR","-1.156e1","-11.56"), Triple("KWD","0.001","0.001"),
            Triple("CLF","0.0001","0.0001"), Triple("EUR","9007199254740993.01","9007199254740993.01"),
        )) assertEquals(expected, parse(currency=currency, amount=amount).fields[listOf("amount")])
        for ((currency, amount) in listOf("JPY" to "1.1", "EUR" to "1.001", "KWD" to "0.0001")) {
            assertFailsWith<Ts12PaymentValidationException> { parse(currency=currency, amount=amount) }
        }
        for (field in listOf("min_amount", "max_amount", "total_amount", "initial_amount")) {
            val failure = assertFailsWith<Ts12PaymentValidationException> {
                parse("""{"recurrence":{"frequency":"MNTH","mit_options":{"$field":1.001}}}""")
            }
            assertEquals("/recurrence/mit_options/$field", failure.path)
        }
        for (token in listOf("2", "2.0", "2e0")) {
            assertEquals("2", parse("""{"recurrence":{"frequency":"DAIL","number":$token}}""").fields[listOf("recurrence","number")])
        }
        for (token in listOf("1e129", "1e-129", "1".repeat(129))) {
            assertTrue(assertFailsWith<Ts12PaymentValidationException> { parse(amount=token) }.unsupported)
        }
    }

    @Test fun transactionIdsCountUnicodeCharacters() {
        for (id in listOf("x", "x".repeat(36), "😀".repeat(36))) {
            Ts12Payment.validate(JsonObject(base + ("transaction_id" to JsonPrimitive(id))), now)
        }
        for (id in listOf("", " ", "x".repeat(37), "😀".repeat(37))) assertFailsWith<Ts12PaymentValidationException> {
            Ts12Payment.validate(JsonObject(base + ("transaction_id" to JsonPrimitive(id))), now)
        }
    }

    @Test fun validatedPaymentHasImmutableFieldsAndPaths() {
        val payment = parse()
        assertFails { (payment.fields as MutableMap<List<String>, String>)[listOf("amount")] = "0" }
        val path = payment.fields.keys.single { it == listOf("payee", "name") }
        assertFails { (path as MutableList<String>)[0] = "changed" }
        assertEquals("11.56", payment.fields[listOf("amount")])
        assertEquals("Shop", payment.fields[listOf("payee", "name")])
    }
}
