package id.walt.wallet2.consent

import kotlinx.serialization.json.*
import kotlin.test.*

class PaymentConsentTest {
    private val payload = Json.parseToJsonElement("""{
        "transaction_id":"8D8AC610-566D-4EF0-9C22-186B2A5ED793",
        "payee":{"name":"Super Store","id":"merchant-001"},"currency":"EUR","amount":11.56
    }""").jsonObject
    private val paths = listOf(listOf("transaction_id"), listOf("payee", "name"), listOf("payee", "id"), listOf("currency"), listOf("amount"))
    private fun catalogue(vararg optional: Pair<String, JsonElement>) = buildJsonObject {
        put("affirmative_action_label", translations("Confirm payment", "Zahlung bestätigen"))
        optional.forEach { (name, value) -> put(name, value) }
    }
    private fun translations(en: String, de: String) = buildJsonArray {
        add(buildJsonObject { put("lang", "en"); put("value", en) })
        add(buildJsonObject { put("lang", "de"); put("value", de) })
    }
    private fun claims() = JsonArray(paths.mapIndexed { index, path -> buildJsonObject {
        put("path", JsonArray(path.map(::JsonPrimitive)))
        if (index != 0) put("visualisation", index)
        put("display", buildJsonArray {
            add(buildJsonObject { put("locale", "en"); put("label", "English ${path.last()}") })
            add(buildJsonObject { put("locale", "de"); put("label", "Deutsch ${path.last()}") })
        })
    } })
    private fun render(catalogue: JsonElement = catalogue(), claims: JsonElement = claims(), locales: List<String> = listOf("de-AT", "en")) =
        localizePaymentConsent(claims, catalogue, paymentValues(payload), locales)
    private fun failure(reason: PaymentConsentFailure, action: () -> Unit) =
        assertEquals(reason, assertFailsWith<PaymentConsentException>(block = action).reason)

    @Test
    fun oneLanguageAndAllDisplayLevelsAreResolvedTogether() {
        val consent = render()
        assertEquals("de", consent.locale)
        assertEquals("Zahlung bestätigen", consent.affirmativeAction)
        assertNull(consent.title)
        assertNull(consent.securityHint)
        assertNull(consent.denialAction)
        assertEquals(listOf(PaymentFieldPlacement.DETAILS, PaymentFieldPlacement.PROMINENT,
            PaymentFieldPlacement.MAIN, PaymentFieldPlacement.DETAILS, PaymentFieldPlacement.OMITTED), consent.fields.map { it.placement })
        assertTrue(consent.fields.all { it.label.startsWith("Deutsch") })
        assertEquals("11.56", consent.fields.last().value) // Omission changes placement, not the bound value.
        assertEquals("en", render(locales = listOf("en-US")).locale)
        failure(PaymentConsentFailure.MISSING_TRANSLATION) { render(locales = listOf("fr")) }
    }

    @Test
    fun regionalAndScriptFallbacksPreserveThePreferredLanguageOrder() {
        val regionalClaims = JsonArray(claims().map { raw ->
            val claim = raw.jsonObject
            JsonObject(claim + ("display" to JsonArray(claim.getValue("display").jsonArray.map { rawDisplay ->
                val display = rawDisplay.jsonObject
                JsonObject(display + ("locale" to JsonPrimitive(if (display["locale"] == JsonPrimitive("de")) "de-DE" else "en-US")))
            })))
        })
        val german = render(claims = regionalClaims, locales = listOf("de-AT", "en"))
        assertEquals("de", german.locale)
        assertTrue(german.fields.all { it.label.startsWith("Deutsch") })
        assertEquals("en", render(claims = regionalClaims, locales = listOf("fr", "en-GB")).locale)

        val scriptClaims = Json.parseToJsonElement(claims().toString().replace("\"de\"", "\"zh-Hant\""))
        val scriptCatalogue = Json.parseToJsonElement(catalogue().toString().replace("\"de\"", "\"zh-Hant\""))
        assertEquals("zh-hant", render(scriptCatalogue, scriptClaims, listOf("zh-Hant-TW-x-demo", "en")).locale)
        failure(PaymentConsentFailure.MISSING_TRANSLATION) {
            render(claims = regionalClaims, locales = listOf("fr-CA"))
        }
    }

    @Test
    fun claimMetadataUsesPinnedLocaleAndPayloadRootWithoutAliases() {
        val first = claims().first().jsonObject
        val legacyDisplay = JsonArray(first.getValue("display").jsonArray.map { entry ->
            JsonObject(entry.jsonObject - "locale" + ("lang" to entry.jsonObject.getValue("locale")))
        })
        for (replacement in listOf(
            "display" to legacyDisplay,
            "path" to JsonArray(listOf(JsonPrimitive("payload")) + first.getValue("path").jsonArray),
        )) failure(PaymentConsentFailure.INVALID_METADATA) {
            render(claims = JsonArray(listOf(JsonObject(first + replacement)) + claims().drop(1)))
        }
    }

    @Test
    fun unknownMetadataIsIgnoredWithoutOverridingKnownInstructions() {
        val extended = JsonArray(claims().map { raw ->
            val claim = raw.jsonObject
            JsonObject(claim + mapOf(
                "vendor_hint" to JsonPrimitive("ignored"),
                "sd" to JsonPrimitive("always"), // Inapplicable to transaction claims.
                "mandatory" to JsonPrimitive(true),
                "display" to JsonArray(claim.getValue("display").jsonArray.map { entry ->
                    JsonObject(entry.jsonObject + ("lang" to JsonPrimitive("fr")))
                }),
            ))
        })
        val result = render(catalogue("vendor_button" to JsonNull), extended)
        assertEquals("de", result.locale)
        assertTrue(result.fields.all { it.label.startsWith("Deutsch") })
        val unknownRequired = JsonObject(extended.first().jsonObject +
            ("path" to JsonArray(listOf(JsonPrimitive("not_present")))))
        failure(PaymentConsentFailure.INVALID_METADATA) {
            render(claims = JsonArray(extended + unknownRequired))
        }
    }

    @Test
    fun optionalElementsDoNotPermitMixedOrSilentlyDroppedInstructions() {
        val englishHint = JsonArray(listOf(buildJsonObject { put("lang", "en"); put("value", "Check the payee") }))
        failure(PaymentConsentFailure.MISSING_TRANSLATION) {
            render(catalogue("security_hint" to englishHint), locales = listOf("de"))
        }
        val fallback = render(catalogue("security_hint" to englishHint))
        assertEquals("en", fallback.locale)
        assertEquals("Check the payee", fallback.securityHint)
        assertTrue(fallback.fields.all { it.label.startsWith("English") })
        failure(PaymentConsentFailure.INVALID_METADATA) { render(catalogue("security_hint" to JsonNull)) }
        failure(PaymentConsentFailure.INVALID_METADATA) { render(JsonObject(emptyMap())) }
    }

    @Test
    fun catalogueLengthsCountCodePointsAndNeverTruncate() {
        for ((name, limit) in mapOf("affirmative_action_label" to 30, "denial_action_label" to 30,
            "transaction_title" to 50, "security_hint" to 250)) {
            val permitted = "😀".repeat(limit)
            val document = JsonObject(catalogue() + (name to translations(permitted, permitted)))
            render(document)
            failure(PaymentConsentFailure.INVALID_METADATA) {
                render(JsonObject(document + (name to translations(permitted + "x", permitted))))
            }
        }
        failure(PaymentConsentFailure.INVALID_METADATA) { render(catalogue("security_hint" to translations(" ", " "))) }
        failure(PaymentConsentFailure.INVALID_METADATA) {
            render(JsonObject(catalogue() + ("affirmative_action_label" to JsonArray(listOf(
                buildJsonObject { put("lang", "EN"); put("value", "Approve") },
                buildJsonObject { put("lang", "en"); put("value", "Different") },
            )))))
        }
    }

    @Test
    fun coveragePathsTranslationsAndPlacementsFailClosed() {
        failure(PaymentConsentFailure.INVALID_METADATA) { render(claims = JsonArray(claims().dropLast(1))) }
        failure(PaymentConsentFailure.INVALID_METADATA) { render(claims = JsonArray(claims() + claims().first())) }
        val first = claims().first().jsonObject
        val mutations = listOf(
            "path" to JsonArray(listOf(JsonPrimitive("payload"))),
            "path" to JsonArray(listOf(JsonPrimitive(0))),
            "visualisation" to JsonPrimitive(0), "visualisation" to JsonPrimitive(5),
            "visualisation" to JsonPrimitive("1"), "mandatory" to JsonPrimitive("true"),
            "display" to JsonArray(emptyList()),
        )
        mutations.forEach { (name, value) ->
            failure(PaymentConsentFailure.INVALID_METADATA) {
                render(claims = JsonArray(listOf(JsonObject(first + (name to value))) + claims().drop(1)))
            }
        }
        val untranslated = JsonObject(first + ("display" to buildJsonArray {
            add(buildJsonObject { put("locale", "fr"); put("label", "Référence") })
        }))
        failure(PaymentConsentFailure.MISSING_TRANSLATION) {
            render(claims = JsonArray(listOf(untranslated) + claims().drop(1)))
        }
    }

    @Test
    fun payloadAmountsPreserveDecimalMeaningAndCurrencyPrecision() {
        val cases = listOf(
            Triple("EUR", "11.56", "11.56"), Triple("EUR", "-11.56", "-11.56"),
            Triple("EUR", "1.156e1", "11.56"), Triple("EUR", "9007199254740993.01", "9007199254740993.01"),
            Triple("JPY", "12", "12"), Triple("KWD", "0.001", "0.001"), Triple("CLF", "0.0001", "0.0001"),
            Triple("EUR", "0", "0"), Triple("EUR", "1.2300", "1.23"),
        )
        cases.forEach { (currency, amount, expected) ->
            val updated = JsonObject(payload + mapOf("currency" to JsonPrimitive(currency), "amount" to Json.parseToJsonElement(amount)))
            assertEquals(expected, paymentValues(updated)[listOf("amount")])
        }
        listOf("EUR" to "1.001", "JPY" to "1.1", "KWD" to "0.0001").forEach { (currency, amount) ->
            failure(PaymentConsentFailure.INVALID_PAYMENT) {
                paymentValues(JsonObject(payload + mapOf("currency" to JsonPrimitive(currency), "amount" to Json.parseToJsonElement(amount))))
            }
        }
    }

    @Test
    fun unsupportedShapesAndMalformedPaymentValuesAreDistinct() {
        for (field in payload.keys) failure(PaymentConsentFailure.INVALID_PAYMENT) { paymentValues(JsonObject(payload - field)) }
        failure(PaymentConsentFailure.INVALID_PAYMENT) { paymentValues(JsonObject(payload + ("amount" to JsonPrimitive("11.56")))) }
        failure(PaymentConsentFailure.INVALID_PAYMENT) { paymentValues(JsonObject(payload + ("transaction_id" to JsonPrimitive("x".repeat(37))))) }
        for (currency in listOf("eur", "ZZZ", "XXX")) failure(PaymentConsentFailure.UNSUPPORTED_PAYMENT) {
            paymentValues(JsonObject(payload + ("currency" to JsonPrimitive(currency))))
        }
        failure(PaymentConsentFailure.UNSUPPORTED_PAYMENT) { paymentValues(JsonObject(payload + ("date_time" to JsonPrimitive("2026-10-01T00:00:00Z")))) }
        failure(PaymentConsentFailure.UNSUPPORTED_PAYMENT) {
            paymentValues(JsonObject(payload + ("payee" to JsonObject(payload.getValue("payee").jsonObject + ("website" to JsonPrimitive("https://shop.example"))))))
        }
        failure(PaymentConsentFailure.UNSUPPORTED_PAYMENT) { paymentValues(JsonObject(payload + ("amount" to Json.parseToJsonElement("1e99999999")))) }
    }

    @Test
    fun metadataSourcesAreExclusiveAndUnsupportedSchemaIsNeverIgnored() {
        val inline = buildJsonObject { put("schema", TS12_PAYMENT_TYPE); put("claims", claims()); put("ui_labels", catalogue()) }
        assertIs<PaymentMetadataSource.Inline>(paymentMetadataSources(inline).claims)
        assertIs<PaymentMetadataSource.Inline>(paymentMetadataSources(JsonObject(inline + ("vendor_hint" to JsonNull))).claims)
        val referenced = buildJsonObject {
            put("schema", TS12_PAYMENT_TYPE); put("claims_uri", "https://issuer.example/claims")
            put("ui_labels_uri", "https://issuer.example/labels"); put("ui_labels_uri#integrity", "sha256-example")
        }
        val ref = assertIs<PaymentMetadataSource.Reference>(paymentMetadataSources(referenced).catalogue)
        assertEquals("sha256-example", ref.integrity)
        failure(PaymentConsentFailure.INVALID_METADATA) { paymentMetadataSources(JsonObject(inline + ("claims_uri" to JsonPrimitive("https://issuer.example/claims")))) }
        failure(PaymentConsentFailure.INVALID_METADATA) { paymentMetadataSources(JsonObject(inline - "claims")) }
        failure(PaymentConsentFailure.UNSUPPORTED_SCHEMA) { paymentMetadataSources(JsonObject(inline + ("schema" to buildJsonObject { put("type", "object") }))) }
        failure(PaymentConsentFailure.UNSUPPORTED_SCHEMA) { paymentMetadataSources(JsonObject(inline - "schema" + ("schema_uri" to JsonPrimitive("https://issuer.example/schema")))) }
        failure(PaymentConsentFailure.UNSUPPORTED_SCHEMA) { paymentMetadataSources(JsonObject(inline + ("extends" to JsonPrimitive("urn:other")))) }
    }
}
