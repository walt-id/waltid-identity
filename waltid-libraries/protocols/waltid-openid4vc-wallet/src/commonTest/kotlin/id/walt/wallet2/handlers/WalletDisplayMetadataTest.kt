package id.walt.wallet2.handlers

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import id.walt.openid4vci.metadata.issuer.CredentialDisplayBackgroundImage
import id.walt.openid4vci.metadata.issuer.CredentialDisplayLogo
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.CredentialMetadata
import id.walt.openid4vci.metadata.issuer.IssuerDisplay
import id.walt.openid4vci.metadata.issuer.IssuerLogo
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WalletDisplayMetadataTest {

    @Test
    fun persistsIssuerAndCredentialDisplayFields() {
        val metadata = storedCredentialDisplayMetadata(
            issuerMetadata = issuerMetadata(),
            credentialConfigurationId = "pid",
        )

        val issuer = metadata!!.jsonObject.getValue("issuerDisplay").jsonArray.single().jsonObject
        assertEquals("Example Issuer", issuer.getValue("name").jsonPrimitive.content)
        assertEquals("https://issuer.example/issuer.png", issuer.getValue("logo").jsonObject.getValue("uri").jsonPrimitive.content)

        val credential = metadata.jsonObject.getValue("credentialDisplay").jsonArray.single().jsonObject
        assertEquals("Personal ID", credential.getValue("name").jsonPrimitive.content)
        assertEquals("#12107c", credential.getValue("background_color").jsonPrimitive.content)
        assertEquals(
            "https://issuer.example/pid-bg.png",
            credential.getValue("background_image").jsonObject.getValue("uri").jsonPrimitive.content,
        )
        assertEquals("#FFFFFF", credential.getValue("text_color").jsonPrimitive.content)
        assertEquals("https://issuer.example/pid.png", credential.getValue("logo").jsonObject.getValue("uri").jsonPrimitive.content)
        assertEquals("PID logo", credential.getValue("logo").jsonObject.getValue("alt_text").jsonPrimitive.content)
    }

    @Test
    fun returnsNullWhenNoDisplayIsAdvertised() {
        val metadata = storedCredentialDisplayMetadata(
            issuerMetadata = CredentialIssuerMetadata(
                credentialIssuer = "https://issuer.example",
                credentialEndpoint = "https://issuer.example/credential",
                credentialConfigurationsSupported = mapOf(
                    "pid" to CredentialConfiguration(format = CredentialFormat.SD_JWT_VC),
                ),
            ),
            credentialConfigurationId = "pid",
        )
        assertNull(metadata)
    }

    private fun issuerMetadata() = CredentialIssuerMetadata(
        credentialIssuer = "https://issuer.example",
        credentialEndpoint = "https://issuer.example/credential",
        display = listOf(
            IssuerDisplay(
                name = "Example Issuer",
                locale = "en",
                logo = IssuerLogo(uri = "https://issuer.example/issuer.png", altText = "Issuer logo"),
            ),
        ),
        credentialConfigurationsSupported = mapOf(
            "pid" to CredentialConfiguration(
                format = CredentialFormat.SD_JWT_VC,
                credentialMetadata = CredentialMetadata(
                    display = listOf(
                        CredentialDisplay(
                            name = "Personal ID",
                            locale = "en",
                            logo = CredentialDisplayLogo(
                                uri = "https://issuer.example/pid.png",
                                altText = "PID logo",
                            ),
                            description = "Government identity",
                            backgroundColor = "#12107c",
                            backgroundImage = CredentialDisplayBackgroundImage(
                                uri = "https://issuer.example/pid-bg.png",
                            ),
                            textColor = "#FFFFFF",
                        ),
                    ),
                ),
            ),
        ),
    )
}
