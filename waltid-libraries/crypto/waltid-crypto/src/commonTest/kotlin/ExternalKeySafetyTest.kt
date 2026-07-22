import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.azure.AzureAuth
import id.walt.crypto.keys.oci.OCIKeyMetadata
import id.walt.crypto.keys.safeLogDescription
import id.walt.crypto.keys.tse.TSEAuth
import id.walt.crypto.keys.tse.TSEKeyMetadata
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ExternalKeySafetyTest {
    @Test
    fun keyGenerationLogDescriptionDoesNotContainConfigurationSecrets() {
        val request = KeyGenerationRequest(
            backend = "aws-rest-api",
            keyType = KeyType.RSA,
            name = "signing-key",
            config = buildJsonObject {
                put("secretAccessKey", JsonPrimitive("sentinel-secret"))
            },
        )

        assertFalse("sentinel-secret" in request.safeLogDescription())
    }

    @Test
    fun rejectsMalformedExternalProviderEndpoints() {
        val vaultAuth = TSEAuth(accessKey = "token")
        assertFailsWith<IllegalArgumentException> { TSEKeyMetadata("https://", vaultAuth) }
        assertFailsWith<IllegalArgumentException> { AzureAuth(keyVaultUrl = "https://user@example.com") }
        assertFailsWith<IllegalArgumentException> {
            OCIKeyMetadata("tenancy", "compartment", "user", "fingerprint", "https://user@management", "crypto.example")
        }
    }
}
