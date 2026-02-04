package id.walt.openid4vci.metadata.issuer.credentialconfiguration

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialDisplay
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CredentialConfigurationDisplayTest {

    @Test
    fun `display locales must be unique`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                display = listOf(
                    CredentialDisplay(name = "Credential", locale = "en"),
                    CredentialDisplay(name = "Credential 2", locale = "en"),
                ),
            )
        }
    }

    @Test
    fun `display name must not be blank`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                display = listOf(
                    CredentialDisplay(name = " "),
                ),
            )
        }
    }
}
