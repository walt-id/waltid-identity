package id.walt.openid4vci.metadata.issuer.credentialconfiguration

import id.walt.openid4vci.CredentialFormat
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.ProofType
import id.walt.openid4vci.prooftypes.ProofTypeId
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CredentialConfigurationBindingMethodsTest {

    @Test
    fun `cryptographic binding methods must not be empty when present`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                format = CredentialFormat.SD_JWT_VC,
                cryptographicBindingMethodsSupported = emptySet(),
            )
        }
    }

    @Test
    fun `did binding method must not include method specific id`() {
        assertFailsWith<IllegalArgumentException> {
            CredentialConfiguration(
                format = CredentialFormat.SD_JWT_VC,
                cryptographicBindingMethodsSupported = setOf(
                    CryptographicBindingMethod.fromValue("did:example:123"),
                ),
                proofTypesSupported = mapOf(
                    ProofTypeId.JWT.value to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
                ),
            )
        }
    }

    @Test
    fun `accepts custom did method`() {
        CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            cryptographicBindingMethodsSupported = setOf(
                CryptographicBindingMethod.fromValue("did:example"),
            ),
            proofTypesSupported = mapOf(
                ProofTypeId.JWT.value to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
            ),
        )
    }

    @Test
    fun `rejects blank did method`() {
        assertFailsWith<IllegalArgumentException> {
            CryptographicBindingMethod.fromValue("did:")
        }
    }

    @Test
    fun `rejects upper case did prefix`() {
        assertFailsWith<IllegalArgumentException> {
            CryptographicBindingMethod.fromValue("DID:key")
        }
    }

    @Test
    fun `accepts supported did binding methods`() {
        CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            cryptographicBindingMethodsSupported = setOf(
                CryptographicBindingMethod.DidKey,
                CryptographicBindingMethod.DidJwk,
                CryptographicBindingMethod.DidWeb,
                CryptographicBindingMethod.DidEbsi,
            ),
            proofTypesSupported = mapOf(
                ProofTypeId.JWT.value to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
            ),
        )
    }

    @Test
    fun `accepts mixed binding methods`() {
        CredentialConfiguration(
            format = CredentialFormat.SD_JWT_VC,
            cryptographicBindingMethodsSupported = setOf(
                CryptographicBindingMethod.Jwk,
                CryptographicBindingMethod.CoseKey,
                CryptographicBindingMethod.DidKey,
                CryptographicBindingMethod.DidJwk,
                CryptographicBindingMethod.DidWeb,
                CryptographicBindingMethod.DidEbsi,
            ),
            proofTypesSupported = mapOf(
                ProofTypeId.JWT.value to ProofType(proofSigningAlgValuesSupported = setOf("ES256")),
            ),
        )
    }
}
