package id.walt.openid4vci.metadata.issuer.credentialconfiguration

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.CredentialMetadata
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CredentialConfigurationDisplayTest {

    private val json = Json { ignoreUnknownKeys = false }

    @Test
    fun `credential metadata display locales must be unique`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(
                    display = listOf(
                        CredentialDisplay(name = "Credential", locale = "en"),
                        CredentialDisplay(name = "Credential 2", locale = "en"),
                    ),
                ),
            )
        }
    }

    @Test
    fun `credential metadata display name must not be blank`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(
                    display = listOf(
                        CredentialDisplay(name = " "),
                    ),
                ),
            )
        }
    }

    @Test
    fun `top level display is rejected and must use credential metadata display`() {
        val payload = """
            {
              "format": "sd_jwt_vc",
              "display": [
                { "name": "Credential", "locale": "en-US" }
              ]
            }
        """.trimIndent()

        assertFailsWith<SerializationException> {
            json.decodeFromString<CredentialConfiguration>(payload)
        }
    }
}
