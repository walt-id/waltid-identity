package id.walt.crypto2.kms

import id.walt.crypto2.kms.aws.AwsKmsOptions
import id.walt.crypto2.kms.azure.AzureKeyVaultOptions
import id.walt.crypto2.kms.oci.OciKmsOptions
import id.walt.crypto2.kms.vault.VaultTransitOptions
import kotlin.test.Test
import kotlin.test.assertFailsWith

class KmsOptionsValidationTest {
    private val credential = CredentialReference("test-credential")

    @Test
    fun acceptsValidEndpoints() {
        VaultTransitOptions("https://vault.example/v1", credential)
        AzureKeyVaultOptions("https://keys.example", "tenant", credential)
        OciKmsOptions("https://management.example", "https://crypto.example", "ocid1.compartment", credential)
        AwsKmsOptions("eu-central-1", credential, "http://localhost:4566")
    }

    @Test
    fun rejectsMalformedEndpointsAndRegions() {
        listOf(
            "https://",
            "https://user:password@example.com",
            "https://example.com?token=secret",
            "https://example.com#fragment",
            "ftp://example.com",
        ).forEach { endpoint ->
            assertFailsWith<IllegalArgumentException> { VaultTransitOptions(endpoint, credential) }
        }
        assertFailsWith<IllegalArgumentException> { AwsKmsOptions("../invalid", credential) }
        assertFailsWith<IllegalArgumentException> {
            AzureKeyVaultOptions("https://keys.example", "tenant", credential, authorityUrl = "https://")
        }
        assertFailsWith<IllegalArgumentException> {
            OciKmsOptions("https://management.example", "https://crypto.example?secret=x", "compartment", credential)
        }
    }
}
