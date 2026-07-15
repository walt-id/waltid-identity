package id.walt.mobile.test.backend

import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class EudiTestBackendTest {
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
}
