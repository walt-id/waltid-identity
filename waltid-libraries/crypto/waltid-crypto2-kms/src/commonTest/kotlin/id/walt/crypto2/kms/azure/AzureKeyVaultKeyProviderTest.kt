package id.walt.crypto2.kms.azure

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.serialization.BinaryData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AzureKeyVaultKeyProviderTest {
    private val options = AzureKeyVaultOptions(
        keyVaultUrl = "https://vault.example",
        tenantId = "tenant",
        credentialReference = CredentialReference("azure-production"),
    )

    @Test
    fun `existing remote key descriptor restores without credentials or network`() = runTest {
        val publicJwk = Json.parseToJsonElement(ecKeyResponse()).jsonObject["key"]!!.jsonObject
        val stored = AzureKeyVaultKeyProvider.storedKeyForExisting(
            id = KeyId("existing-azure-key"),
            spec = KeySpec.Ec(EcCurve.P256),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            options = options,
            keyIdUrl = "https://vault.example/keys/ec-key/version-1",
            publicKey = EncodedKey.Jwk(
                BinaryData(Json.encodeToString(JsonObject(publicJwk - "kid")).encodeToByteArray()),
                privateMaterial = false,
            ),
        )
        val restored = runtime(mockClient { error("Restore must not call Azure") }).restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(stored))
        )

        assertEquals(AzureKeyVaultKeyProvider.ID, stored.provider)
        assertNotNull(restored.capabilities.signer)
        assertTrue("azure-production" in stored.providerData.toByteArray().decodeToString())
        assertFalse("client-secret" in stored.providerData.toByteArray().decodeToString())
    }

    @Test
    fun `existing Azure P-256K key is normalized to JOSE secp256k1`() = runTest {
        val publicJwk = EncodedKey.Jwk(
            BinaryData(
                """{"kty":"EC","crv":"P-256K","x":"${base64Url.encode(ByteArray(32) { 1 })}","y":"${base64Url.encode(ByteArray(32) { 2 })}"}"""
                    .encodeToByteArray()
            ),
            privateMaterial = false,
        )
        val stored = AzureKeyVaultKeyProvider.storedKeyForExisting(
            id = KeyId("azure-secp256k1"),
            spec = KeySpec.Ec(EcCurve.SECP256K1),
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            options = options,
            keyIdUrl = "https://vault.example/keys/ec-key/version-1",
            publicKey = publicJwk,
        )
        val normalized = Json.parseToJsonElement(
            (stored.publicKey as EncodedKey.Jwk).data.toByteArray().decodeToString()
        ).jsonObject

        assertEquals("secp256k1", normalized["crv"]?.jsonPrimitive?.content)
    }

    @Test
    fun `ECDSA signing converts Azure P1363 and survives persistence restart`() = runTest {
        val p1363 = ByteArray(64) { index -> if (index == 0 || index == 32) 0x80.toByte() else index.toByte() }
        var requestIndex = 0
        val client = mockClient { request ->
            when (requestIndex++) {
                0, 2 -> respondToken(request)
                1 -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/keys/ec-key/create", request.url.encodedPath)
                    val body = request.bodyJson()
                    assertEquals("EC", body.requiredString("kty"))
                    assertEquals("P-256", body.requiredString("crv"))
                    respondJson(ecKeyResponse())
                }
                3 -> {
                    assertEquals("/keys/ec-key/version-1/sign", request.url.encodedPath)
                    assertEquals("Bearer access-token", request.headers[HttpHeaders.Authorization])
                    val body = request.bodyJson()
                    assertEquals("ES256", body.requiredString("alg"))
                    assertEquals(32, base64Url.decode(body.requiredString("value")).size)
                    respondJson("""{"value":"${base64Url.encode(p1363)}"}""")
                }
                4 -> {
                    assertEquals("/keys/ec-key/version-1/verify", request.url.encodedPath)
                    val body = request.bodyJson()
                    assertEquals("ES256", body.requiredString("alg"))
                    assertContentEquals(p1363, base64Url.decode(body.requiredString("signature")))
                    assertEquals(32, base64Url.decode(body.requiredString("digest")).size)
                    respondJson("""{"value":true}""")
                }
                else -> error("Unexpected Azure request: ${request.url}")
            }
        }
        val generated = runtime(client).generateManagedKey(
            AzureKeyVaultKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("ec-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options.encode(),
            ),
        )
        val providerData = generated.storedKey.providerData.toByteArray().decodeToString()
        assertTrue("azure-production" in providerData)
        assertFalse("client-secret" in providerData)
        val restored = runtime(client).restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(generated.storedKey))
        )
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, EcdsaSignatureEncoding.DER)
        val der = assertNotNull(restored.capabilities.signer).sign("hello".encodeToByteArray(), algorithm)
        assertContentEquals(p1363, EcdsaSignatureCodec.derToP1363(der, 32))
        assertTrue(assertNotNull(restored.capabilities.verifier).verify("hello".encodeToByteArray(), der, algorithm))
        assertEquals(5, requestIndex)
    }

    @Test
    fun `RSA encrypt decrypt and delete use the pinned version and key name`() = runTest {
        val plaintext = "plaintext".encodeToByteArray()
        val encrypted = ByteArray(256) { 9 }
        var requestIndex = 0
        val client = mockClient { request ->
            when (requestIndex++) {
                0 -> respondToken(request)
                1 -> respondJson(rsaKeyResponse())
                2 -> {
                    assertEquals("/keys/rsa-key/version-7/encrypt", request.url.encodedPath)
                    val body = request.bodyJson()
                    assertEquals("RSA-OAEP-256", body.requiredString("alg"))
                    assertContentEquals(plaintext, base64Url.decode(body.requiredString("value")))
                    respondJson("""{"value":"${base64Url.encode(encrypted)}"}""")
                }
                3 -> {
                    assertEquals("/keys/rsa-key/version-7/decrypt", request.url.encodedPath)
                    assertContentEquals(encrypted, base64Url.decode(request.bodyJson().requiredString("value")))
                    respondJson("""{"value":"${base64Url.encode(plaintext)}"}""")
                }
                4 -> {
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals("/keys/rsa-key", request.url.encodedPath)
                    respondJson("{}")
                }
                else -> error("Unexpected Azure request: ${request.url}")
            }
        }
        val key = runtime(client).generateManagedKey(
            AzureKeyVaultKeyProvider.ID,
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
        assertEquals("version-7", ciphertext.keyVersion)
        assertContentEquals(plaintext, assertNotNull(key.capabilities.decryptor).decrypt(ciphertext, null))
        assertEquals(KeyDeletionResult.Deleted, assertNotNull(key.capabilities.deleter).delete())
        assertEquals(5, requestIndex)
    }

    private fun runtime(client: HttpClient): CryptoRuntime = CryptoRuntime(
        softwareProviders = emptyList(),
        managedProviders = listOf(
            AzureKeyVaultKeyProvider(
                client = client,
                credentialResolver = AzureCredentialResolver { reference ->
                    assertEquals(options.credentialReference, reference)
                    AzureClientSecretCredential("client-id", "client-secret")
                },
            )
        ),
    )

    private fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) { engine { addHandler(handler) } }

    private fun MockRequestHandleScope.respondToken(request: HttpRequestData) = respondJson(
        """{"access_token":"access-token"}"""
    ).also {
        assertEquals("/tenant/oauth2/v2.0/token", request.url.encodedPath)
        val body = request.bodyText()
        assertTrue("client_id=client-id" in body)
        assertTrue("client_secret=client-secret" in body)
    }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun ecKeyResponse(): String = """
        {"key":{
          "kid":"https://vault.example/keys/ec-key/version-1",
          "kty":"EC","crv":"P-256",
          "x":"${base64Url.encode(ByteArray(32) { 1 })}",
          "y":"${base64Url.encode(ByteArray(32) { 2 })}"
        }}
    """.trimIndent()

    private fun rsaKeyResponse(): String {
        val modulus = ByteArray(256).apply { this[0] = 0x80.toByte() }
        return """
            {"key":{
              "kid":"https://vault.example/keys/rsa-key/version-7",
              "kty":"RSA","n":"${base64Url.encode(modulus)}","e":"AQAB"
            }}
        """.trimIndent()
    }

    companion object {
        private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    }
}

private fun HttpRequestData.bodyText(): String =
    (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
        ?: error("Expected a byte-array request body")

private fun HttpRequestData.bodyJson(): JsonObject = Json.parseToJsonElement(bodyText()) as JsonObject

private fun JsonObject.requiredString(name: String): String =
    requireNotNull(this[name]).jsonPrimitive.content
