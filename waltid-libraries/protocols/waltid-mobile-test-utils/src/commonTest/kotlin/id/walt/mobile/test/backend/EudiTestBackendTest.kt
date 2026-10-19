package id.walt.mobile.test.backend

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
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

class EudiTestBackendTest {
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
        val validOfferUrl = offerUrl()
        val payload = buildJsonObject {
            put("url_data", validOfferUrl)
            put("tx_code", JsonPrimitive("1234"))
        }

        val generated = EudiTestBackend.generatedOfferFromFinalPayload(payload)

        assertEquals(validOfferUrl, generated.offerUrl)
        assertEquals("1234", generated.txCode)
    }

    @Test
    fun rejectsPayloadWithoutSeparatelyDeliveredTransactionCode() {
        val payload = buildJsonObject {
            put("url_data", offerUrl())
        }

        val failure = assertFailsWith<IllegalStateException> {
            EudiTestBackend.generatedOfferFromFinalPayload(payload)
        }
        assertEquals("final payload missing tx_code", failure.message)
    }

    @Test
    fun rejectsOfferWithoutTransactionCodeMetadata() {
        val payload = buildJsonObject {
            put("url_data", offerUrl(includeTransactionCodeMetadata = false))
            put("tx_code", JsonPrimitive("1234"))
        }

        val failure = assertFailsWith<IllegalStateException> {
            EudiTestBackend.generatedOfferFromFinalPayload(payload)
        }
        assertEquals("credential offer missing tx_code metadata", failure.message)
    }

    private fun offerUrl(includeTransactionCodeMetadata: Boolean = true): String {
        val offer = buildJsonObject {
            put("credential_issuer", "https://issuer.example")
            putJsonArray("credential_configuration_ids") { add("credential-id") }
            putJsonObject("grants") {
                putJsonObject("urn:ietf:params:oauth:grant-type:pre-authorized_code") {
                    put("pre-authorized_code", "pre-authorized-code")
                    if (includeTransactionCodeMetadata) putJsonObject("tx_code") {
                        put("input_mode", "numeric")
                        put("length", 4)
                        put("description", "Enter the separately delivered code")
                    }
                }
            }
        }
        // Keep whitespace so the preservation assertion detects URL reserialization.
        val offerJson = Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), offer)
        return "openid-credential-offer://credential_offer?credential_offer=${offerJson.encodeURLParameter()}"
    }
}
