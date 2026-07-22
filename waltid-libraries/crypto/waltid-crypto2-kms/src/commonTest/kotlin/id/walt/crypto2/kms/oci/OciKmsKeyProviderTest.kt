package id.walt.crypto2.kms.oci

import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.AsymmetricEncryptionAlgorithm
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureCodec
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.AsymmetricCiphertext
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyDeletionResult
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.StoredKey
import id.walt.crypto2.keys.encodePem
import id.walt.crypto2.keys.toSpkiDer
import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.BinaryData
import id.walt.crypto2.serialization.StoredKeyCodec
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
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class OciKmsKeyProviderTest {
    private val options = OciKmsOptions(
        managementEndpoint = "https://management.test",
        cryptoEndpoint = "https://crypto.test",
        compartmentOcid = "ocid1.compartment.test",
        credentialReference = CredentialReference("oci-production"),
    )

    @Test
    fun `existing key discovery pins current remote version and public key`() = runTest {
        val apiKeyPem = apiKeyPem()
        val spec = KeySpec.Ec(EcCurve.P256)
        val publicKey = CryptoRuntime(listOf(CryptographySoftwareKeyProvider())).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("source"),
                spec = spec,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        ).capabilities.publicKeyExporter!!.exportPublicKey().toSpkiDer(spec)
        var requestIndex = 0
        val client = mockClient { request ->
            when (requestIndex++) {
                0 -> {
                    assertEquals("/20180608/keys/ocid1.key.existing", request.url.encodedPath)
                    respondJson(
                        """{"id":"ocid1.key.existing","currentKeyVersion":"version-7","keyShape":{"algorithm":"ECDSA","length":32,"curveId":"NIST_P256"}}"""
                    )
                }
                1 -> {
                    assertEquals(
                        "/20180608/keys/ocid1.key.existing/keyVersions/version-7",
                        request.url.encodedPath,
                    )
                    respondJson(
                        """{"publicKey":"${publicKey.encodePem().replace("\n", "\\n")}"}"""
                    )
                }
                else -> error("Unexpected OCI request: ${request.url}")
            }
        }
        val provider = provider(client, apiKeyPem)
        val stored = provider.storedKeyForExisting(
            id = KeyId("existing-key"),
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            options = options,
            remoteKeyId = "ocid1.key.existing",
        )
        val restored = provider.restore(
            assertIs<StoredKey.Managed>(StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(stored)))
        )

        assertNotNull(restored.capabilities.signer)
        assertTrue("version-7" in stored.providerData.toByteArray().decodeToString())
        assertTrue("oci-production" in stored.providerData.toByteArray().decodeToString())
        assertFalse("PRIVATE KEY" in stored.providerData.toByteArray().decodeToString())
        assertEquals(2, requestIndex)
    }

    @Test
    fun `P384 operations pin key version and survive persistence restart`() = runTest {
        val apiKeyPem = apiKeyPem()
        val p1363 = ByteArray(96) { index -> if (index == 0 || index == 48) 0x80.toByte() else index.toByte() }
        val der = EcdsaSignatureCodec.p1363ToDer(p1363, 48)
        var requestIndex = 0
        val client = mockClient { request ->
            assertEquals("Fri, 17 Jul 2026 12:00:00 GMT", request.headers[HttpHeaders.Date])
            assertTrue("keyId=\"ocid1.tenancy/ocid1.user/fingerprint\"" in request.headers[HttpHeaders.Authorization].orEmpty())
            assertTrue("signature=\"" in request.headers[HttpHeaders.Authorization].orEmpty())
            when (requestIndex++) {
                0 -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/20180608/keys", request.url.encodedPath)
                    assertSignedBody(request)
                    val shape = request.bodyJson().requiredObject("keyShape")
                    assertEquals("ECDSA", shape.requiredString("algorithm"))
                    assertEquals("NIST_P384", shape.requiredString("curveId"))
                    assertEquals(48, shape.requiredInt("length"))
                    respondJson(
                        """{"id":"ocid1.key.remote","currentKeyVersion":"version-4","keyShape":{"algorithm":"ECDSA","length":48,"curveId":"NIST_P384"}}"""
                    )
                }
                1 -> {
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/20180608/keys/ocid1.key.remote/keyVersions/version-4", request.url.encodedPath)
                    assertEquals("host (request-target) date", authorizationHeaders(request))
                    val pem = EncodedKey.SpkiDer(BinaryData(TEST_SPKI)).encodePem().replace("\n", "\\n")
                    respondJson("""{"publicKey":"$pem"}""")
                }
                2 -> {
                    assertEquals("https://crypto.test/20180608/sign", request.url.toString())
                    assertSignedBody(request)
                    val body = request.bodyJson()
                    assertEquals("version-4", body.requiredString("keyVersionId"))
                    assertEquals("ECDSA_SHA_384", body.requiredString("signingAlgorithm"))
                    assertEquals("DIGEST", body.requiredString("messageType"))
                    assertEquals(48, Base64.Default.decode(body.requiredString("message")).size)
                    respondJson("""{"signature":"${Base64.Default.encode(der)}"}""")
                }
                3 -> {
                    assertEquals("https://crypto.test/20180608/verify", request.url.toString())
                    val body = request.bodyJson()
                    assertEquals("version-4", body.requiredString("keyVersionId"))
                    assertEquals("DIGEST", body.requiredString("messageType"))
                    assertContentEquals(der, Base64.Default.decode(body.requiredString("signature")))
                    respondJson("""{"isSignatureValid":true}""")
                }
                4 -> {
                    assertEquals("/20180608/keys/ocid1.key.remote/actions/scheduleDeletion", request.url.encodedPath)
                    assertEquals("2026-07-24T12:00:00Z", request.bodyJson().requiredString("timeOfDeletion"))
                    respondJson("{}")
                }
                else -> error("Unexpected OCI request: ${request.url}")
            }
        }
        val runtime = runtime(client, apiKeyPem)
        val generated = runtime.generateManagedKey(
            OciKmsKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("logical-key"),
                spec = KeySpec.Ec(EcCurve.P384),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options.encode(),
            ),
        )
        val providerData = generated.storedKey.providerData.toByteArray().decodeToString()
        assertTrue("oci-production" in providerData)
        assertFalse("PRIVATE KEY" in providerData)
        val restored = runtime(client, apiKeyPem).restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(generated.storedKey))
        )
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_384)
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
    fun `RSA OAEP encryption remains bound to the OCI key version`() = runTest {
        val apiKeyPem = apiKeyPem()
        val plaintext = "oci plaintext".encodeToByteArray()
        val encrypted = ByteArray(256) { 6 }
        var requestIndex = 0
        val client = mockClient { request ->
            when (requestIndex++) {
                0 -> respondJson(
                    """{"id":"ocid1.key.rsa","currentKeyVersion":"version-rsa","keyShape":{"algorithm":"RSA","length":256}}"""
                )
                1 -> respondJson(
                    """{"publicKey":"-----BEGIN PUBLIC KEY-----\n${Base64.Default.encode(TEST_SPKI)}\n-----END PUBLIC KEY-----"}"""
                )
                2 -> {
                    assertEquals("https://crypto.test/20180608/encrypt", request.url.toString())
                    val body = request.bodyJson()
                    assertEquals("version-rsa", body.requiredString("keyVersionId"))
                    assertEquals("RSA_OAEP_SHA_256", body.requiredString("encryptionAlgorithm"))
                    assertContentEquals(plaintext, Base64.Default.decode(body.requiredString("plaintext")))
                    respondJson(
                        """{"ciphertext":"${Base64.Default.encode(encrypted)}","keyId":"ocid1.key.rsa","keyVersionId":"version-rsa"}"""
                    )
                }
                3 -> {
                    assertEquals("https://crypto.test/20180608/decrypt", request.url.toString())
                    val body = request.bodyJson()
                    assertEquals("version-rsa", body.requiredString("keyVersionId"))
                    assertContentEquals(encrypted, Base64.Default.decode(body.requiredString("ciphertext")))
                    respondJson("""{"plaintext":"${Base64.Default.encode(plaintext)}"}""")
                }
                else -> error("Unexpected OCI request")
            }
        }
        val key = runtime(client, apiKeyPem).generateManagedKey(
            OciKmsKeyProvider.ID,
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
        assertEquals("version-rsa", ciphertext.keyVersion)
        assertFailsWith<IllegalArgumentException> {
            assertNotNull(key.capabilities.decryptor).decrypt(ciphertext, byteArrayOf(1))
        }
        assertContentEquals(plaintext, assertNotNull(key.capabilities.decryptor).decrypt(ciphertext, null))
        assertEquals(4, requestIndex)
    }

    private suspend fun apiKeyPem(): String = CryptographyProvider.Default.get(RSA.PKCS1)
        .keyPairGenerator(2048.bits, SHA256)
        .generateKey()
        .privateKey
        .encodeToByteArray(RSA.PrivateKey.Format.PEM)
        .decodeToString()

    private fun runtime(client: HttpClient, privateKeyPem: String): CryptoRuntime = CryptoRuntime(
        softwareProviders = emptyList(),
        managedProviders = listOf(provider(client, privateKeyPem)),
    )

    private fun provider(client: HttpClient, privateKeyPem: String) = OciKmsKeyProvider(
        client = client,
        credentialResolver = OciCredentialResolver { reference ->
            assertEquals(options.credentialReference, reference)
            OciApiKeyCredential(
                tenancyOcid = "ocid1.tenancy",
                userOcid = "ocid1.user",
                fingerprint = "fingerprint",
                privateKeyPem = privateKeyPem,
            )
        },
        now = { Instant.parse("2026-07-17T12:00:00Z") },
    )

    private fun assertSignedBody(request: HttpRequestData) {
        assertEquals("date (request-target) host content-length content-type x-content-sha256", authorizationHeaders(request))
        assertEquals(request.bodyText().encodeToByteArray().size.toString(), request.headers[HttpHeaders.ContentLength])
        assertNotNull(request.headers["x-content-sha256"])
    }

    private fun authorizationHeaders(request: HttpRequestData): String = request.headers[HttpHeaders.Authorization]
        .orEmpty().substringAfter("headers=\"").substringBefore('"')

    private fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) { engine { addHandler(handler) } }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    companion object {
        private val TEST_SPKI = byteArrayOf(0x30, 0x03, 0x02, 0x01, 0x01)
    }
}

private fun HttpRequestData.bodyText(): String =
    (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString()
        ?: error("Expected byte-array request body")

private fun HttpRequestData.bodyJson(): JsonObject = Json.parseToJsonElement(bodyText()) as JsonObject

private fun JsonObject.requiredObject(name: String): JsonObject = requireNotNull(this[name]) as JsonObject
private fun JsonObject.requiredString(name: String): String = requireNotNull(this[name]).jsonPrimitive.content
private fun JsonObject.requiredInt(name: String): Int = requireNotNull(this[name]).jsonPrimitive.int
