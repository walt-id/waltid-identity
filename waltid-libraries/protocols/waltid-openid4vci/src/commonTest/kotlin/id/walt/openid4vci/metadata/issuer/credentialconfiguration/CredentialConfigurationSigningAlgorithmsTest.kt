package id.walt.openid4vci.metadata.issuer.credentialconfiguration

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.SigningAlgId
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CredentialConfigurationSigningAlgorithmsTest {

    @Test
    fun `credential signing algorithms must not be empty when present`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                credentialSigningAlgValuesSupported = emptySet(),
            )
        }
    }

    @Test
    fun `credential signing algorithms must not contain blank entries`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                credentialSigningAlgValuesSupported = setOf(
                    SigningAlgId.jose(""),
                ),
            )
        }
    }

    @Test
    fun `jwt formats require jose algorithm identifiers`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.JWT_VC_JSON,
                credentialSigningAlgValuesSupported = setOf(
                    SigningAlgId.coseValue(-7),
                ),
            )
        }
    }

    @Test
    fun `mso_mdoc allows cose numeric and cose name identifiers`() {
        CredentialConfiguration(
            id = "cred-id-1",
            format = CredentialFormat.MSO_MDOC,
            credentialSigningAlgValuesSupported = setOf(
                SigningAlgId.coseValue(-7),
                SigningAlgId.coseName("ES256"),
            ),
        )
    }

    @Test
    fun `mso_mdoc rejects jose and ld suite identifiers`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.MSO_MDOC,
                credentialSigningAlgValuesSupported = setOf(
                    SigningAlgId.jose("ES256"),
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.MSO_MDOC,
                credentialSigningAlgValuesSupported = setOf(
                    SigningAlgId.ldSuite("Ed25519Signature2018"),
                ),
            )
        }
    }

    @Test
    fun `ldp_vc requires ld suite identifiers`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.LDP_VC,
                credentialSigningAlgValuesSupported = setOf(
                    SigningAlgId.jose("ES256"),
                ),
            )
        }
        CredentialConfiguration(
            id = "cred-id-1",
            format = CredentialFormat.LDP_VC,
            credentialSigningAlgValuesSupported = setOf(
                SigningAlgId.ldSuite("Ed25519Signature2018"),
            ),
        )
    }
}
