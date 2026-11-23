package id.walt.mobile.test.backend

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EudiTestBackendTest {
    @Test
    fun requestObjectPayloadCannotReportVerifierFailure() {
        listOf("error", "failed", "invalid").forEach { word ->
            val events = JsonArray(listOf(buildJsonObject {
                put("event", "Request object retrieved")
                put("jwt", "opaque.$word.payload")
            }))

            assertFalse(EudiTestBackend.verifierEventsConfirmPresentation(events.toString()), word)
        }
    }

    @Test
    fun requestObjectPayloadCannotConfirmPresentation() {
        listOf("successful", "verified").forEach { word ->
            val events = JsonArray(listOf(buildJsonObject {
                put("event", "Request object retrieved")
                put("jwt", "opaque.$word.payload")
            }))

            assertFalse(EudiTestBackend.verifierEventsConfirmPresentation(events.toString()), word)
        }
    }

    @Test
    fun postedWalletResponsesConfirmPresentationInBothResponseShapes() {
        listOf("Wallet response posted", "Verifier got wallet response").forEach { name ->
            val events = JsonArray(listOf(buildJsonObject {
                put("event", name)
                putJsonObject("wallet_response") {
                    put("error", JsonNull)
                    putJsonObject("vp_token") {
                        putJsonArray("identity") {
                            add(JsonPrimitive("opaque.error.failed.invalid.verified.payload"))
                        }
                    }
                }
            }))

            assertTrue(EudiTestBackend.verifierEventsConfirmPresentation(events.toString()), name)
            assertTrue(EudiTestBackend.verifierEventsConfirmPresentation(buildJsonObject {
                put("transaction_id", "test-transaction")
                put("events", events)
            }.toString()), name)
        }
    }

    @Test
    fun verifierFailureEventsRejectPresentation() {
        listOf(
            "FailedToRetrieve request",
            "Wallet failed to post response",
            "Verifier failed to get wallet",
            "Presentation expired",
            "Attestation status check failed",
        ).forEach { name ->
            val events = JsonArray(listOf(buildJsonObject {
                put("event", name)
                put("jwt", "opaque-payload-must-not-be-logged")
            }))

            val failure = assertFailsWith<IllegalStateException> {
                EudiTestBackend.verifierEventsConfirmPresentation(events.toString())
            }
            assertTrue(failure.message.orEmpty().contains(name))
            assertFalse(failure.message.orEmpty().contains("opaque-payload-must-not-be-logged"))
        }
    }

    @Test
    fun postedWalletErrorCannotConfirmPresentation() {
        listOf("Wallet response posted", "Verifier got wallet response").forEach { name ->
            val events = JsonArray(listOf(buildJsonObject {
                put("event", name)
                putJsonObject("wallet_response") {
                    put("error", "access_denied")
                    put("error_description", "opaque-description-must-not-be-logged")
                }
            }))

            val failure = assertFailsWith<IllegalStateException> {
                EudiTestBackend.verifierEventsConfirmPresentation(events.toString())
            }
            assertTrue(failure.message.orEmpty().contains("wallet error"))
            assertFalse(failure.message.orEmpty().contains("opaque-description-must-not-be-logged"))
        }
    }

    @Test
    fun unrelatedOrUnknownEventsCannotConfirmPresentation() {
        listOf("Attestation status check succeeded", "Future wallet response posted event").forEach { name ->
            val events = JsonArray(listOf(buildJsonObject { put("event", name) }))
            assertFalse(EudiTestBackend.verifierEventsConfirmPresentation(events.toString()), name)
        }
    }

    @Test
    fun rejectsMalformedVerifierEventEvidence() {
        listOf(
            "null",
            "{}",
            """{"events":{}}""",
            "[1]",
            "[{}]",
            """[{"event":true}]""",
            """[{"event":"Wallet response posted"}]""",
            """[{"event":"Wallet response posted","wallet_response":{}}]""",
            """[{"event":"Wallet response posted","wallet_response":{"vp_token":{}}}]""",
        ).forEach { body ->
            assertFailsWith<IllegalStateException>(body) {
                EudiTestBackend.verifierEventsConfirmPresentation(body)
            }
        }
    }

    @Test
    fun redirectPreservesOptionalSignedPayload() {
        val payload = """<input name="payload" value='{}'>"""
        assertEquals(mapOf("payload" to "{}"), EudiTestBackend.redirectFields(payload))
        assertEquals(
            mapOf("payload" to "{}", "payload_jwt" to "opaque.signed.value"),
            EudiTestBackend.redirectFields(payload + """<input name="payload_jwt" value="opaque.signed.value" />"""),
        )
    }

    @Test
    fun payloadFieldSupportsEitherHtmlQuoteStyle() {
        val pages = listOf(
            """<input type="hidden" name="payload" value='{"message":"A &amp; B"}'>""",
            """<input type="hidden" name="payload" value="{&quot;message&quot;:&quot;A &amp; B&quot;}" />""",
            """<input type="hidden" name="payload" value="{&#34;message&#34;:&#34;A &amp; B&#34;}" />""",
        )
        pages.forEach { page ->
            assertEquals("""{"message":"A & B"}""", EudiTestBackend.extractPayload(page))
        }
    }

    @Test
    fun payloadEntitiesAreDecodedOnlyOnce() {
        val page = """<input name="payload" value="{&#34;message&#34;:&#34;&amp;quot; &amp;lt;&#34;}" />"""
        assertEquals("""{"message":"&quot; &lt;"}""", EudiTestBackend.extractPayload(page))
    }

    @Test
    fun payloadFieldRejectsMissingOrMismatchedQuotes() {
        listOf(
            """<input name="payload_jwt" value="ignored">""",
            """<input name="payload" value='unterminated">""",
        ).forEach { page ->
            assertFailsWith<IllegalStateException> { EudiTestBackend.extractPayload(page) }
        }
    }

    @Test
    fun selectsFirstNonBlankConfiguredIntendedUse() {
        val response = buildJsonObject {
            putJsonArray("intended_uses") {
                addJsonObject {
                    put("intended_use_id", JsonPrimitive("  "))
                }
                addJsonObject {
                    put("intended_use_id", JsonPrimitive("configured-use"))
                }
                addJsonObject {
                    put("intended_use_id", JsonPrimitive("later-use"))
                }
            }
        }

        assertEquals("configured-use", EudiTestBackend.intendedUseIdFromResponse(response))
    }

    @Test
    fun rejectsVerifierResponseWithoutConfiguredIntendedUse() {
        val response = buildJsonObject {
            putJsonArray("intended_uses") {
                addJsonObject {
                    put("description", JsonPrimitive("missing id"))
                }
            }
        }

        assertFailsWith<IllegalStateException> {
            EudiTestBackend.intendedUseIdFromResponse(response)
        }
    }

    @Test
    fun addsIntendedUseIdWithoutSendingRegistrationCertificate() {
        val payload = EudiTestBackend.buildVerifierTransactionPayload(
            credentialId = "eu.europa.ec.eudi.ehic_sd_jwt_vc",
            intendedUseId = "configured-use",
        )

        assertEquals("configured-use", payload["intended_use_id"]?.jsonPrimitive?.content)
        assertFalse(payload.containsKey("registration_certificate"))
    }

    @Test
    fun buildsEhicSdJwtVerifierQuery() {
        val query = EudiTestBackend.buildDcqlQuery("eu.europa.ec.eudi.ehic_sd_jwt_vc").jsonObject
        val credentialQuery = query.getValue("credentials").jsonArray.single().jsonObject

        assertEquals("dc+sd-jwt", credentialQuery.getValue("format").jsonPrimitive.content)
        assertEquals(
            listOf("urn:eudi:ehic:1"),
            credentialQuery.getValue("meta").jsonObject.getValue("vct_values").jsonArray
                .map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun preservesStandardOfferAndReturnsTransactionCodeSeparately() {
        val offerJson = """
            {
              "credential_issuer": "https://issuer.example",
              "credential_configuration_ids": ["credential-id"],
              "grants": {
                "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                  "pre-authorized_code": "pre-authorized-code",
                  "tx_code": {
                    "input_mode": "numeric",
                    "length": 4,
                    "description": "Enter the separately delivered code"
                  }
                }
              }
            }
        """.trimIndent()
        val offerUrl = "openid-credential-offer://credential_offer?credential_offer=${offerJson.encodeURLParameter()}"
        val payload = buildJsonObject {
            put("url_data", JsonPrimitive(offerUrl))
            put("tx_code", JsonPrimitive("1234"))
        }

        val generated = EudiTestBackend.generatedOfferFromFinalPayload(payload)

        assertEquals(offerUrl, generated.offerUrl)
        assertEquals("1234", generated.txCode)
    }

    @Test
    fun rejectsPayloadWithoutSeparatelyDeliveredTransactionCode() {
        val offerUrl = "openid-credential-offer://credential_offer?credential_offer={}".encodeURLParameter()
        val payload = buildJsonObject {
            put("url_data", JsonPrimitive(offerUrl))
        }

        assertFailsWith<IllegalStateException> {
            EudiTestBackend.generatedOfferFromFinalPayload(payload)
        }
    }

    @Test
    fun rejectsOfferWithoutTransactionCodeMetadata() {
        val offerJson = """
            {
              "credential_issuer": "https://issuer.example",
              "credential_configuration_ids": ["credential-id"],
              "grants": {
                "urn:ietf:params:oauth:grant-type:pre-authorized_code": {
                  "pre-authorized_code": "pre-authorized-code"
                }
              }
            }
        """.trimIndent()
        val offerUrl = "openid-credential-offer://credential_offer?credential_offer=${offerJson.encodeURLParameter()}"
        val payload = buildJsonObject {
            put("url_data", JsonPrimitive(offerUrl))
            put("tx_code", JsonPrimitive("1234"))
        }

        assertFailsWith<IllegalStateException> {
            EudiTestBackend.generatedOfferFromFinalPayload(payload)
        }
    }
}
