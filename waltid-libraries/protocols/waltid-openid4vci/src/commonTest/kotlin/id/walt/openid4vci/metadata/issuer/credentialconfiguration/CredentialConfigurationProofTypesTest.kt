package id.walt.openid4vci.metadata.issuer.credentialconfiguration

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import id.walt.openid4vci.metadata.issuer.ProofType
import id.walt.openid4vci.prooftypes.ProofTypeId
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CredentialConfigurationProofTypesTest {

    @Test
    fun `proof signing algorithms must not be empty`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                proofTypesSupported = mapOf(
                    ProofTypeId.JWT.value to ProofType(proofSigningAlgValuesSupported = emptySet()),
                ),
            )
        }
    }

    @Test
    fun `proof types must be present when cryptographic binding methods are set`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                proofTypesSupported = null,
            )
        }
    }

    @Test
    fun `cryptographic binding methods must be present when proof types are set`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                proofTypesSupported = mapOf(
                    ProofTypeId.JWT.value to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
                ),
                cryptographicBindingMethodsSupported = null,
            )
        }
    }

    @Test
    fun `proof types must not be empty when present`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                proofTypesSupported = emptyMap(),
            )
        }
    }

    @Test
    fun `proof type identifiers must not be blank`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                id = "cred-id-1",
                format = CredentialFormat.SD_JWT_VC,
                cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
                proofTypesSupported = mapOf(
                    "" to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
                ),
            )
        }
    }

    @Test
    fun `accepts proof types with key attestation requirements`() {
        CredentialConfiguration(
            id = "cred-id-1",
            format = CredentialFormat.SD_JWT_VC,
            cryptographicBindingMethodsSupported = setOf(CryptographicBindingMethod.Jwk),
            proofTypesSupported = mapOf(
                ProofTypeId.JWT.value to ProofType(
                    proofSigningAlgValuesSupported = setOf("ES256"),
                    keyAttestationsRequired = KeyAttestationsRequired(
                        keyStorage = setOf("iso_18045_moderate"),
                        userAuthentication = setOf("iso_18045_moderate"),
                    ),
                ),
            ),
        )
    }
}
