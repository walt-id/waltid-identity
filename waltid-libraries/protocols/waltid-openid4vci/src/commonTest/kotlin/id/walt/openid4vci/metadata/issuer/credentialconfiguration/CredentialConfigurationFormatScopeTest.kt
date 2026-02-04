package id.walt.openid4vci.metadata.issuer.credentialconfiguration

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CredentialConfigurationFormatScopeTest {

    @Test
    fun `credential formats include spec identifiers`() {
        assertNotNull(CredentialFormat.fromValue("jwt_vc_json"))
        assertNotNull(CredentialFormat.fromValue("jwt_vc_json-ld"))
        assertNotNull(CredentialFormat.fromValue("ldp_vc"))
        assertNotNull(CredentialFormat.fromValue("mso_mdoc"))
        assertNotNull(CredentialFormat.fromValue("dc+sd-jwt"))
    }

    @Test
    fun `unknown credential format returns null`() {
        assertNull(CredentialFormat.fromValue("unknown_format"))
    }

    @Test
    fun `scope must not be blank when present`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                scope = " ",
            )
        }
    }
}
