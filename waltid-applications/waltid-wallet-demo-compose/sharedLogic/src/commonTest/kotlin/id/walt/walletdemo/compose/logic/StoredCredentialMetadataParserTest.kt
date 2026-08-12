package id.walt.walletdemo.compose.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StoredCredentialMetadataParserTest {

    @Test
    fun parsesIssuerDisplayNameAndLogo() {
        val display = StoredCredentialMetadataParser.issuerDisplay(
            """
            {
              "issuerDisplay": [
                {
                  "name": "Government Issuer",
                  "locale": "en-US",
                  "logo": { "uri": "https://issuer.example/logo.png", "alt_text": "Gov logo" }
                }
              ]
            }
            """.trimIndent()
        )

        assertNotNull(display)
        assertEquals("Government Issuer", display.name)
        assertEquals("https://issuer.example/logo.png", display.logoUri)
        assertEquals("Gov logo", display.logoAltText)
    }

    @Test
    fun selectsPreferredLocale() {
        val display = StoredCredentialMetadataParser.issuerDisplay(
            metadataJson = """
            {
              "issuerDisplay": [
                { "name": "English Issuer", "locale": "en" },
                { "name": "German Issuer", "locale": "de" }
              ]
            }
            """.trimIndent(),
            preferredLocales = listOf("de-AT"),
        )

        assertEquals("German Issuer", display?.name)
    }

    @Test
    fun returnsNullWhenMetadataMissing() {
        assertNull(StoredCredentialMetadataParser.issuerDisplay(null))
        assertNull(StoredCredentialMetadataParser.issuerDisplay("{}"))
        assertNull(StoredCredentialMetadataParser.issuerDisplay("""{"issuerDisplay":[]}"""))
    }

    @Test
    fun toDetailsSurfacesIssuerDisplayOnCredentialDetails() {
        val details = CredentialDisplayNormalizer.toDetails(
            CredentialSummary(
                id = "cred-1",
                format = "vc+sd-jwt",
                issuer = "https://issuer.example",
                label = "PID",
                credentialDataJson = """{"given_name":"Ada"}""",
                metadataJson = """
                    {
                      "issuerDisplay": [
                        { "name": "Demo Issuer", "logo": { "uri": "https://issuer.example/logo.png" } }
                      ]
                    }
                """.trimIndent(),
            )
        )

        assertEquals("Demo Issuer", details.issuerDisplay?.name)
        assertEquals("https://issuer.example/logo.png", details.issuerDisplay?.logoUri)
        assertEquals("Demo Issuer", details.toCardDisplayData().issuer)
    }
}
