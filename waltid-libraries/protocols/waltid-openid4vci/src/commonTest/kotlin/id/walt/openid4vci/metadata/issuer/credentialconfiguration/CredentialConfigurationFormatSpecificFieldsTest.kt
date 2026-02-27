package id.walt.openid4vci.metadata.issuer.credentialconfiguration

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class CredentialConfigurationFormatSpecificFieldsTest {

    @Test
    fun `supports credential definition for jwt vc json`() {
        val definition = CredentialDefinition(
            type = listOf("VerifiableCredential", "UniversityDegreeCredential"),
        )

        val configuration = CredentialConfiguration(
            format = CredentialFormat.JWT_VC_JSON,
            credentialDefinition = definition,
        )

        assertEquals(definition, configuration.credentialDefinition)
    }

    @Test
    fun `supports credential definition with context for ldp vc`() {
        val definition = CredentialDefinition(
            context = listOf("https://www.w3.org/2018/credentials/v1"),
            type = listOf("VerifiableCredential"),
        )

        val configuration = CredentialConfiguration(
            format = CredentialFormat.LDP_VC,
            credentialDefinition = definition,
        )

        assertEquals(definition, configuration.credentialDefinition)
    }

    @Test
    fun `supports doctype for mso mdoc`() {
        val configuration = CredentialConfiguration(
            format = CredentialFormat.MSO_MDOC,
            doctype = "org.iso.18013.5.1.mDL",
        )

        assertEquals("org.iso.18013.5.1.mDL", configuration.doctype)
    }

    @Test
    fun `supports vct for sd jwt vc`() {
        val configuration = CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            vct = "SD_JWT_VC_example_in_OpenID4VCI",
        )

        assertEquals("SD_JWT_VC_example_in_OpenID4VCI", configuration.vct)
    }
}
