package id.waltid.openid4vci.wallet.metadata

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.ClaimDescription
import id.walt.openid4vci.metadata.issuer.ClaimDisplay
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.CredentialDisplayBackgroundImage
import id.walt.openid4vci.metadata.issuer.CredentialDisplayLogo
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialMetadata
import id.walt.openid4vci.offers.CredentialOffer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfferedCredentialResolverTest {

    @Test
    fun `unoffered mdoc array paths do not block resolving an sd jwt offer`() {
        val metadata = Json.decodeFromString<CredentialIssuerMetadata>(
            """
            {
              "credential_issuer": "https://issuer.example.com",
              "credential_endpoint": "https://issuer.example.com/credential",
              "credential_configurations_supported": {
                "mdl": {
                  "format": "mso_mdoc",
                  "doctype": "org.iso.18013.5.1.mDL",
                  "credential_metadata": {"claims": [{
                    "path": ["org.iso.18013.5.1", "driving_privileges", null, "vehicle_category_code"]
                  }]}
                },
                "pid": {
                  "format": "dc+sd-jwt",
                  "vct": "urn:example:pid",
                  "credential_metadata": {"claims": [{"path": ["nationalities", 0]}]}
                }
              }
            }
            """.trimIndent()
        )
        val offer = CredentialOffer(
            credentialIssuer = metadata.credentialIssuer,
            credentialConfigurationIds = listOf("pid"),
        )
        val offered = OfferedCredentialResolver.resolveOfferedCredentials(offer, metadata)
        assertEquals(listOf("pid"), offered.map { it.credentialConfigurationId })
        assertEquals(
            listOf(JsonPrimitive("nationalities"), JsonPrimitive(0)),
            offered.single().configuration.credentialMetadata!!.claims!!.single().path,
        )
        assertEquals(
            JsonNull,
            metadata.credentialConfigurationsSupported.getValue("mdl").credentialMetadata!!.claims!!.single().path[2],
        )
    }

    private fun issuerMetadata(configuration: CredentialConfiguration): CredentialIssuerMetadata =
        CredentialIssuerMetadata(
            credentialIssuer = "https://issuer.example.com",
            credentialEndpoint = "https://issuer.example.com/openid4vci/credential",
            credentialConfigurationsSupported = mapOf(
                "identity_credential" to configuration,
            ),
        )

    @Test
    fun `resolves offered credentials and returns display name from credential metadata`() {
        val configuration = CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            credentialMetadata = CredentialMetadata(
                display = listOf(
                    CredentialDisplay(
                        name = "Identity Credential",
                        locale = "en-US",
                        logo = CredentialDisplayLogo(
                            uri = "https://issuer.example.com/assets/identity-logo.png",
                            altText = "Identity credential logo",
                        ),
                        description = "Government-issued identity credential",
                        backgroundColor = "#12107c",
                        backgroundImage = CredentialDisplayBackgroundImage(
                            uri = "https://issuer.example.com/assets/identity-bg.png",
                        ),
                        textColor = "#FFFFFF",
                    ),
                ),
                claims = listOf(
                    ClaimDescription(
                        path = listOf("given_name"),
                        display = listOf(
                            ClaimDisplay(name = "Given Name", locale = "en-US"),
                            ClaimDisplay(name = "Vorname", locale = "de-DE"),
                        ),
                    ),
                    ClaimDescription(
                        path = listOf("family_name"),
                        display = listOf(
                            ClaimDisplay(name = "Family Name", locale = "en-US"),
                        ),
                    ),
                ),
            ),
        )

        val offer = CredentialOffer(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = listOf("identity_credential"),
        )

        val resolved = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata(configuration))
        val displayName = OfferedCredentialResolver.getDisplayName(resolved.single())

        assertEquals(1, resolved.size)
        assertEquals("identity_credential", resolved.single().credentialConfigurationId)
        assertEquals("Identity Credential", displayName)
        assertEquals("Given Name", resolved.single().configuration.credentialMetadata?.claims?.get(0)?.display?.get(0)?.name)
        assertEquals("Vorname", resolved.single().configuration.credentialMetadata?.claims?.get(0)?.display?.get(1)?.name)
    }

    @Test
    fun `throws when offered credential configuration id is missing`() {
        val offer = CredentialOffer(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = listOf("unknown_id"),
        )

        assertFailsWith<IllegalArgumentException> {
            OfferedCredentialResolver.resolveOfferedCredentials(
                offer = offer,
                metadata = issuerMetadata(
                    CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
            )
        }
    }

    @Test
    fun `validate offered credentials returns false when at least one id is unsupported`() {
        val metadata = issuerMetadata(CredentialConfiguration(format = CredentialFormat.SD_JWT_VC))
        val offer = CredentialOffer(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = listOf("identity_credential", "unknown_id"),
        )

        assertTrue(!OfferedCredentialResolver.validateOfferedCredentials(offer, metadata))
    }

    @Test
    fun `display name is null when credential metadata display is absent`() {
        val configuration = CredentialConfiguration(format = CredentialFormat.SD_JWT_VC)
        val offer = CredentialOffer(
            credentialIssuer = "https://issuer.example.com",
            credentialConfigurationIds = listOf("identity_credential"),
        )

        val resolved = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata(configuration))
        assertNull(OfferedCredentialResolver.getDisplayName(resolved.single()))
    }
}
