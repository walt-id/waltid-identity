package id.walt.crypto2.kms.aws

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.StoredKeyCodec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AwsKmsKeyProviderTest {
    private val options = AwsKmsOptions(
        region = "eu-central-1",
        credentialReference = CredentialReference("aws-production"),
        endpoint = "https://kms.test/",
    )

    @Test
    fun `existing remote key descriptor restores without credentials or network`() = runTest {
        val publicKey = testSpki(KeySpec.Ec(EcCurve.P256))
        val stored = AwsKmsKeyProvider.storedKeyForExisting(
            id = KeyId("existing-aws-key"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            options = options,
            remoteKeyId = "remote-existing-key",
            publicKey = publicKey,
        )
        val restored = runtime(mockClient { error("Restore must not call AWS") }).restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(stored))
        )

        assertEquals(AwsKmsKeyProvider.ID, stored.provider)
        assertNotNull(restored.capabilities.signer)
        assertTrue("aws-production" in stored.providerData.toByteArray().decodeToString())
        assertFalse("secret-key" in stored.providerData.toByteArray().decodeToString())
    }

    @Test
    fun `SigV4 signing includes session credentials and restored ECDSA uses P1363`() = runTest {
        val testSpki = testSpki(KeySpec.Ec(EcCurve.P256))
        val p1363 = ByteArray(64) { index -> if (index == 0 || index == 32) 0x80.toByte() else index.toByte() }
        val der = EcdsaSignatureCodec.p1363ToDer(p1363, 32)
        var requestIndex = 0
        val client = mockClient { request ->
            assertEquals("20260717T120000Z", request.headers["X-Amz-Date"])
            assertEquals("session-token", request.headers["X-Amz-Security-Token"])
            assertTrue("Credential=access-key/20260717/eu-central-1/kms/aws4_request" in request.headers[HttpHeaders.Authorization].orEmpty())
            assertTrue("x-amz-security-token;x-amz-target" in request.headers[HttpHeaders.Authorization].orEmpty())
            when (requestIndex++) {
                0 -> {
                    assertEquals("TrentService.CreateKey", request.headers["X-Amz-Target"])
                    assertEquals("ECC_NIST_P256", request.bodyJson().requiredString("KeySpec"))
                    respondJson("""{"KeyMetadata":{"KeyId":"remote-key"}}""")
                }
                1 -> {
                    assertEquals("TrentService.GetPublicKey", request.headers["X-Amz-Target"])
                    respondJson(
                        """{"KeySpec":"ECC_NIST_P256","PublicKey":"${Base64.Default.encode(testSpki.data.toByteArray())}"}"""
                    )
                }
                2 -> {
                    assertEquals("TrentService.Sign", request.headers["X-Amz-Target"])
                    val body = request.bodyJson()
                    assertEquals("DIGEST", body.requiredString("MessageType"))
                    assertEquals("ECDSA_SHA_256", body.requiredString("SigningAlgorithm"))
                    assertEquals(32, Base64.Default.decode(body.requiredString("Message")).size)
                    respondJson("""{"Signature":"${Base64.Default.encode(der)}"}""")
                }
                3 -> {
                    assertEquals("TrentService.Verify", request.headers["X-Amz-Target"])
                    assertContentEquals(der, Base64.Default.decode(request.bodyJson().requiredString("Signature")))
                    respondJson("""{"SignatureValid":true}""")
                }
                4 -> {
                    assertEquals("TrentService.ScheduleKeyDeletion", request.headers["X-Amz-Target"])
                    assertEquals("7", request.bodyJson().requiredString("PendingWindowInDays"))
                    respondJson("""{"DeletionDate":1784894400}""")
                }
                else -> error("Unexpected AWS request")
            }
        }
        val generated = runtime(client).generateManagedKey(
            AwsKmsKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("logical-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options.encode(),
            ),
        )
        val providerData = generated.storedKey.providerData.toByteArray().decodeToString()
        assertTrue("aws-production" in providerData)
        assertFalse("secret-key" in providerData)
        val restored = runtime(client).restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(generated.storedKey))
        )
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)
        val signature = assertNotNull(restored.capabilities.signer).sign("message".encodeToByteArray(), algorithm)
        assertContentEquals(p1363, signature)
        assertTrue(assertNotNull(restored.capabilities.verifier).verify("message".encodeToByteArray(), signature, algorithm))
        assertEquals(
            KeyDeletionResult.Scheduled(Instant.parse("2026-07-24T12:00:00Z")),
            assertNotNull(restored.capabilities.deleter).delete(),
        )
        assertEquals(5, requestIndex)
    }

    @Test
    fun `RSA OAEP encryption uses opaque provider-bound ciphertext`() = runTest {
        val testSpki = testSpki(KeySpec.Rsa(2048))
        val plaintext = "aws plaintext".encodeToByteArray()
        val encrypted = ByteArray(256) { 5 }
        var requestIndex = 0
        val client = mockClient { request ->
            when (requestIndex++) {
                0 -> respondJson("""{"KeyMetadata":{"KeyId":"remote-rsa"}}""")
                1 -> respondJson(
                    """{"KeySpec":"RSA_2048","PublicKey":"${Base64.Default.encode(testSpki.data.toByteArray())}"}"""
                )
                2 -> {
                    assertEquals("TrentService.Encrypt", request.headers["X-Amz-Target"])
                    val body = request.bodyJson()
                    assertEquals("RSAES_OAEP_SHA_256", body.requiredString("EncryptionAlgorithm"))
                    assertContentEquals(plaintext, Base64.Default.decode(body.requiredString("Plaintext")))
                    respondJson("""{"CiphertextBlob":"${Base64.Default.encode(encrypted)}"}""")
                }
                3 -> {
                    assertEquals("TrentService.Decrypt", request.headers["X-Amz-Target"])
                    assertContentEquals(encrypted, Base64.Default.decode(request.bodyJson().requiredString("CiphertextBlob")))
                    respondJson("""{"Plaintext":"${Base64.Default.encode(plaintext)}"}""")
                }
                else -> error("Unexpected AWS request")
            }
        }
        val key = runtime(client).generateManagedKey(
            AwsKmsKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("rsa-key"),
                spec = KeySpec.Rsa(2048),
                usages = setOf(KeyUsage.ENCRYPT, KeyUsage.DECRYPT),
                providerOptions = options.encode(),
            ),
        )
        val algorithm = AsymmetricEncryptionAlgorithm.RsaOaep(DigestAlgorithm.SHA_256)
        val ciphertext = assertIs<AsymmetricCiphertext.Opaque>(
            assertNotNull(key.capabilities.encryptor).encrypt(plaintext, algorithm, null)
        )
        assertEquals(AwsKmsKeyProvider.ID, ciphertext.provider)
        assertContentEquals(plaintext, assertNotNull(key.capabilities.decryptor).decrypt(ciphertext, null))
        assertEquals(4, requestIndex)
    }

    private fun runtime(client: HttpClient): CryptoRuntime = CryptoRuntime(
        softwareProviders = emptyList(),
        managedProviders = listOf(
            AwsKmsKeyProvider(
                client = client,
                credentialResolver = AwsCredentialResolver { reference ->
                    assertEquals(options.credentialReference, reference)
                    AwsCredentials("access-key", "secret-key", "session-token")
                },
                now = { Instant.parse("2026-07-17T12:00:00Z") },
            )
        ),
    )

    private fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) { engine { addHandler(handler) } }

    private suspend fun testSpki(spec: KeySpec) = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))
        .generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("public-key-source"),
                spec = spec,
                usages = if (spec is KeySpec.Rsa) {
                    setOf(KeyUsage.ENCRYPT, KeyUsage.DECRYPT)
                } else {
                    setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
                },
            )
        ).capabilities.publicKeyExporter!!.exportPublicKey().toSpkiDer(spec)

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/x-amz-json-1.1"),
    )

}

private fun HttpRequestData.bodyJson(): JsonObject {
    val body = body as? OutgoingContent.ByteArrayContent ?: error("Expected byte-array request body")
    return Json.parseToJsonElement(body.bytes().decodeToString()) as JsonObject
}

private fun JsonObject.requiredString(name: String): String = requireNotNull(this[name]).jsonPrimitive.content
