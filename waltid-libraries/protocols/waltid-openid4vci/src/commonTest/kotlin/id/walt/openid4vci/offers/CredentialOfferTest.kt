package id.walt.openid4vci.offers

import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import id.walt.openid4vci.GrantType

class CredentialOfferTest {

    private val json = Json {
        encodeDefaults = false
        explicitNulls = false
    }

    @Test
    fun `credential offer round trips authorization code combinations`() {
        val offers = listOf(
            CredentialOffer(
                credentialIssuer = "https://issuer.example",
                credentialConfigurationIds = listOf("cred-id-1"),
            ),
            CredentialOffer.withAuthorizationCodeGrant(
                credentialIssuer = "https://issuer.example",
                credentialConfigurationIds = listOf("cred-id-1", "cred-id-2"),
                issuerState = "state-123",
            ),
            CredentialOffer.withAuthorizationCodeGrant(
                credentialIssuer = "https://issuer.example",
                credentialConfigurationIds = listOf("cred-id-1"),
                authorizationServer = "https://as.example",
            ),
            CredentialOffer.withAuthorizationCodeGrant(
                credentialIssuer = "https://issuer.example",
                credentialConfigurationIds = listOf("cred-id-1"),
                issuerState = "state-456",
                authorizationServer = "https://as.example",
            ),
        )

        offers.forEach { offer ->
            val encoded = json.encodeToJsonElement(CredentialOffer.serializer(), offer).jsonObject
            assertEquals(offer.credentialIssuer, encoded["credential_issuer"]?.jsonPrimitive?.content)
            val ids = encoded["credential_configuration_ids"]?.jsonArray
            assertEquals(offer.credentialConfigurationIds, ids?.map { it.jsonPrimitive.content })

            if (offer.grants == null) {
                assertTrue(!encoded.containsKey("grants"))
            } else {
                val grants = encoded["grants"]?.jsonObject
                assertNotNull(grants)
                assertTrue(grants.containsKey("authorization_code"))
            }

            val decoded = json.decodeFromJsonElement(CredentialOffer.serializer(), encoded)
            assertEquals(offer, decoded)
        }
    }

    @Test
    fun `credential offer decodes authorization code combinations`() {
        val payloads = listOf(
            """
            {"credential_issuer":"https://issuer.example","credential_configuration_ids":["cred-id-1"]}
            """,
            """
            {"credential_issuer":"https://issuer.example","credential_configuration_ids":["cred-id-1","cred-id-2"],"grants":{"authorization_code":{"issuer_state":"state-123"}}}
            """,
            """
            {"credential_issuer":"https://issuer.example","credential_configuration_ids":["cred-id-1"],"grants":{"authorization_code":{"authorization_server":"https://as.example"}}}
            """,
            """
            {"credential_issuer":"https://issuer.example","credential_configuration_ids":["cred-id-1"],"grants":{"authorization_code":{"issuer_state":"state-456","authorization_server":"https://as.example"}}}
            """,
        )

        payloads.forEach { payload ->
            val offer = json.decodeFromString(CredentialOffer.serializer(), payload.trimIndent())
            assertEquals("https://issuer.example", offer.credentialIssuer)
            assertTrue(offer.credentialConfigurationIds.isNotEmpty())
            offer.grants?.authorizationCode?.let { authGrant ->
                authGrant.issuerState?.let { assertTrue(it.isNotBlank()) }
                authGrant.authorizationServer?.let { assertTrue(it.isNotBlank()) }
            }
        }
    }

    @Test
    fun `credential offer rejects invalid issuer identifiers`() {
        // issuer identifier must be a URL without query/fragment components.
        assertFailsWith<IllegalArgumentException> {
            CredentialOffer.withAuthorizationCodeGrant(
                credentialIssuer = "",
                credentialConfigurationIds = listOf("cred-id-1"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialOffer.withAuthorizationCodeGrant(
                credentialIssuer = "https://issuer.example?query=1",
                credentialConfigurationIds = listOf("cred-id-1"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialOffer.withAuthorizationCodeGrant(
                credentialIssuer = "https://issuer.example#frag",
                credentialConfigurationIds = listOf("cred-id-1"),
            )
        }
    }

    @Test
    fun `credential offer rejects null configuration ids`() {
        // credential_configuration_ids is required and must not be null.
        val payload = """{"credential_issuer":"https://issuer.example","credential_configuration_ids":null}"""
        assertFailsWith<SerializationException> {
            json.decodeFromString(CredentialOffer.serializer(), payload)
        }
    }

    @Test
    fun `credential offer rejects empty configuration ids`() {
        // credential_configuration_ids must include at least one entry.
        val payload = """{"credential_issuer":"https://issuer.example","credential_configuration_ids":[]}"""
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString(CredentialOffer.serializer(), payload)
        }
    }

    @Test
    fun `credential offer rejects blank configuration ids`() {
        // credential_configuration_ids entries must not be blank.
        val payload = """{"credential_issuer":"https://issuer.example","credential_configuration_ids":[" "]}"""
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString(CredentialOffer.serializer(), payload)
        }
    }

    @Test
    fun `credential offer rejects duplicate configuration ids`() {
        // credential_configuration_ids must not contain duplicates.
        val payload =
            """{"credential_issuer":"https://issuer.example","credential_configuration_ids":["cred-id-1","cred-id-1"]}"""
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString(CredentialOffer.serializer(), payload)
        }
    }

    @Test
    fun `credential offer exposes grant type`() {
        val noGrant = CredentialOffer(
            credentialIssuer = "https://issuer.example",
            credentialConfigurationIds = listOf("cred-id-1"),
        )
        assertEquals(null, noGrant.getGrantType())

        val authOffer = CredentialOffer.withAuthorizationCodeGrant(
            credentialIssuer = "https://issuer.example",
            credentialConfigurationIds = listOf("cred-id-1"),
        )
        assertEquals(GrantType.AuthorizationCode, authOffer.getGrantType())

        val preAuthOffer = CredentialOffer.withPreAuthorizedCodeGrant(
            credentialIssuer = "https://issuer.example",
            credentialConfigurationIds = listOf("cred-id-1"),
            preAuthorizedCode = "pre-auth-123",
        )
        assertEquals(GrantType.PreAuthorizedCode, preAuthOffer.getGrantType())
    }
}
