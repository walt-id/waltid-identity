package id.walt.crypto.keys

import id.walt.crypto.exceptions.KeyTypeNotSupportedException
import id.walt.crypto.exceptions.KeyVaultUnavailable
import id.walt.crypto.keys.aws.AWSKeyMetadata
import id.walt.crypto.keys.aws.AWSKeyRestAPI
import id.walt.crypto.keys.azure.AzureKeyRestApi
import id.walt.crypto.keys.tse.TSEKey
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalKmsKeyGenerationTest {

    @Test
    fun `Vault public key decoder handles PEM encoded keys`() {
        val pem = """
            -----BEGIN PUBLIC KEY-----
            AQID
            BA==
            -----END PUBLIC KEY-----
        """.trimIndent()

        val decoded = TSEKey.decodeVaultPublicKey(pem)

        assertContentEquals(byteArrayOf(1, 2, 3, 4), decoded)
    }

    @Test
    fun `Vault public key decoder handles raw base64 keys`() {
        val decoded = TSEKey.decodeVaultPublicKey("AQIDBA==")

        assertContentEquals(byteArrayOf(1, 2, 3, 4), decoded)
    }

    @Test
    fun `Vault public key decoder reports provider context for invalid data`() {
        val exception = assertFailsWith<KeyVaultUnavailable> {
            TSEKey.decodeVaultPublicKey("not a public key")
        }

        assertTrue(exception.message!!.contains("HashiCorp Vault Transit public key decoding failed"))
        assertFalse(exception.message!!.contains("Invalid symbol"))
    }

    @Test
    fun `external KMS error sanitization redacts credentials`() {
        val sanitized = sanitizeExternalKmsError(
            """{"secretId":"vault-secret","accessKey":"root-token","Authorization":"Bearer abc.def"}"""
        )

        assertFalse(sanitized.contains("vault-secret"))
        assertFalse(sanitized.contains("root-token"))
        assertFalse(sanitized.contains("abc.def"))
        assertTrue(sanitized.contains("<redacted>"))
    }

    @Test
    fun `AWS generation rejects unsupported Ed25519 before provider request`() = runTest {
        val exception = assertFailsWith<KeyTypeNotSupportedException> {
            AWSKeyRestAPI.generate(
                KeyType.Ed25519,
                AWSKeyMetadata(
                    accessKeyId = "test-access-key",
                    secretAccessKey = "test-secret-key",
                    region = "us-east-1",
                )
            )
        }

        assertTrue(exception.message!!.contains("Ed25519"))
    }

    @Test
    fun `Azure key mapping rejects unsupported Ed25519 before provider request`() {
        val exception = assertFailsWith<KeyTypeNotSupportedException> {
            AzureKeyRestApi.AzureKeyFunctions.keyTypeToAzureKeyMapping(KeyType.Ed25519)
        }

        assertTrue(exception.message!!.contains("Ed25519"))
    }
}
