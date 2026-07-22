package id.walt.issuer2.service.openid4vci

import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExternalLoginAuthorizationParametersTest {

    @Test
    fun `round trips nested redirect query and authorization details`() {
        val authorizationDetails = buildJsonArray {
            repeat(3) { index ->
                add(buildJsonObject {
                    put("type", "openid_credential")
                    put("credential_configuration_id", "identity_credential_$index")
                })
            }
        }.toString()
        val parameters = mapOf(
            "client_id" to listOf("wallet-client"),
            "redirect_uri" to listOf("https://wallet.example/callback?dummy1=foo&dummy2=ipsum"),
            "authorization_details" to listOf(authorizationDetails),
            "resource" to listOf("first", "second"),
            "state" to listOf("Grüße"),
        )

        val encoded = parameters.encodeExternalLoginAuthorizationParameters()

        assertTrue(encoded.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(parameters, encoded.decodeExternalLoginAuthorizationParameters())
    }

    @Test
    fun `rejects malformed and oversized values`() {
        assertFailsWith<IllegalArgumentException> {
            "not_valid*".decodeExternalLoginAuthorizationParameters()
        }
        assertFailsWith<IllegalArgumentException> {
            "a".repeat(MAX_EXTERNAL_LOGIN_AUTHORIZATION_PARAMETERS_LENGTH + 1)
                .decodeExternalLoginAuthorizationParameters()
        }
        assertFailsWith<IllegalArgumentException> {
            mapOf("value" to listOf("a".repeat(MAX_EXTERNAL_LOGIN_AUTHORIZATION_PARAMETERS_LENGTH)))
                .encodeExternalLoginAuthorizationParameters()
        }
    }
}
