package id.walt.crypto2.kms.vault

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.*
import id.walt.crypto2.keys.*
import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.kms.KmsProviderException
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.crypto2.serialization.StoredKeyCodec
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class VaultTransitKeyProviderTest {
    private val options = VaultTransitOptions(
        apiBaseUrl = "https://vault.example/v1",
        credentialReference = CredentialReference("vault-production"),
        namespace = "tenant-a",
    )
    private val algorithm = SignatureAlgorithm.Ecdsa(
        digest = DigestAlgorithm.SHA_256,
        encoding = EcdsaSignatureEncoding.IEEE_P1363,
    )

    @Test
    fun `existing remote key descriptor pins version and contains no Vault token`() = runTest {
        val spec = KeySpec.Ec(EcCurve.P256)
        val publicKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
            GenerateSoftwareKeyRequest(
                id = KeyId("source"),
                spec = spec,
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            )
        ).capabilities.publicKeyExporter!!.exportPublicKey().toSpkiDer(spec)
        val stored = VaultTransitKeyProvider.storedKeyForExisting(
            id = KeyId("existing-vault-key"),
            spec = spec,
            usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
            options = options,
            remoteName = "remote-key",
            keyVersion = 1,
            publicKey = publicKey,
        )
        val restored = runtime(mockClient { error("Restore must not call Vault") }).restore(
            StoredKeyCodec.decodeFromByteArray(StoredKeyCodec.encodeToByteArray(stored))
        )

        assertNotNull(restored.capabilities.signer)
        assertTrue("vault-production" in stored.providerData.toByteArray().decodeToString())
        assertFalse("vault-token" in stored.providerData.toByteArray().decodeToString())
        assertTrue("\"keyVersion\":1" in stored.providerData.toByteArray().decodeToString())
    }

    @Test
    fun `generation and restored signing pin algorithm encoding digest and version`() = runTest {
        val signature = ByteArray(64) { 7 }
        var requestIndex = 0
        val client = mockClient { request ->
            assertEquals("tenant-a", request.headers["X-Vault-Namespace"])
            assertEquals("vault-token", request.headers["X-Vault-Token"])
            when (requestIndex++) {
                0 -> {
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals("/v1/transit/keys/logical-key", request.url.encodedPath)
                    assertEquals("ecdsa-p256", request.bodyJson().requiredString("type"))
                    respond("", HttpStatusCode.NoContent)
                }

                1 -> {
                    assertEquals(HttpMethod.Get, request.method)
                    assertEquals("/v1/transit/keys/logical-key", request.url.encodedPath)
                    respondJson(keyResponse())
                }

                2 -> {
                    assertEquals("/v1/transit/sign/logical-key", request.url.encodedPath)
                    val body = request.bodyJson()
                    assertContentEquals("message".encodeToByteArray(), Base64.decode(body.requiredString("input")))
                    assertFalse(body.requiredBoolean("prehashed"))
                    assertEquals(3, body.requiredInt("key_version"))
                    assertEquals("sha2-256", body.requiredString("hash_algorithm"))
                    assertEquals("jws", body.requiredString("marshaling_algorithm"))
                    respondJson(signatureResponse(signature))
                }

                3 -> {
                    val body = request.bodyJson()
                    assertContentEquals(ByteArray(32) { 4 }, Base64.decode(body.requiredString("input")))
                    assertTrue(body.requiredBoolean("prehashed"))
                    assertEquals("sha2-256", body.requiredString("hash_algorithm"))
                    respondJson(signatureResponse(signature))
                }

                4 -> {
                    assertEquals("/v1/transit/verify/logical-key", request.url.encodedPath)
                    val body = request.bodyJson()
                    assertEquals("vault:v3:${vaultJwsBase64.encode(signature)}", body.requiredString("signature"))
                    assertFalse(body.requiredBoolean("prehashed"))
                    respondJson("""{"data":{"valid":true}}""")
                }

                else -> error("Unexpected Vault request: ${request.url}")
            }
        }
        val resolver = VaultCredentialResolver { reference ->
            assertEquals(options.credentialReference, reference)
            VaultCredential.Token("vault-token")
        }
        val generated = runtime(client, resolver).generateManagedKey(
            VaultTransitKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("logical-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options.encode(),
            ),
        )
        val storedBytes = StoredKeyCodec.encodeToByteArray(generated.storedKey)
        val providerData = generated.storedKey.providerData.toByteArray().decodeToString()
        assertTrue("vault-production" in providerData)
        assertFalse("vault-token" in providerData)

        val restored = assertIs<ManagedKey>(
            runtime(client, resolver).restore(StoredKeyCodec.decodeFromByteArray(storedBytes))
        )
        val publicKey = assertIs<EncodedKey.SpkiDer>(restored.storedKey.publicKey)
        assertContentEquals(TEST_SPKI, publicKey.data.toByteArray())
        assertContentEquals(
            signature,
            assertNotNull(restored.capabilities.signer).sign("message".encodeToByteArray(), algorithm),
        )
        assertContentEquals(
            signature,
            assertNotNull(restored.capabilities.digestSigner).signDigest(
                DigestValue(DigestAlgorithm.SHA_256, ByteArray(32) { 4 }),
                algorithm,
            ),
        )
        assertTrue(
            assertNotNull(restored.capabilities.verifier)
                .verify("message".encodeToByteArray(), signature, algorithm)
        )
        assertEquals(5, requestIndex)
    }

    @Test
    fun `AppRole login includes namespace and does not persist credentials`() = runTest {
        var requestIndex = 0
        val client = mockClient { request ->
            assertEquals("tenant-a", request.headers["X-Vault-Namespace"])
            when (requestIndex++) {
                0, 2 -> {
                    assertEquals("/v1/auth/approle/login", request.url.encodedPath)
                    assertEquals("role", request.bodyJson().requiredString("role_id"))
                    assertEquals("secret", request.bodyJson().requiredString("secret_id"))
                    respondJson("""{"auth":{"client_token":"leased-token"}}""")
                }

                1 -> {
                    assertEquals("leased-token", request.headers["X-Vault-Token"])
                    respond("", HttpStatusCode.NoContent)
                }

                3 -> {
                    assertEquals("leased-token", request.headers["X-Vault-Token"])
                    respondJson(keyResponse())
                }

                else -> error("Unexpected Vault request")
            }
        }
        val key = runtime(
            client,
            VaultCredentialResolver { VaultCredential.AppRole("role", "secret") },
        ).generateManagedKey(
            VaultTransitKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("logical-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN),
                providerOptions = options.encode(),
            ),
        )

        val providerData = key.storedKey.providerData.toByteArray().decodeToString()
        assertFalse("role" in providerData)
        assertFalse("secret" in providerData)
        assertEquals(4, requestIndex)
    }

    @Test
    fun `RSA encryption preserves opaque versioned ciphertext and deletion is verified`() = runTest {
        val plaintext = "vault plaintext".encodeToByteArray()
        val ciphertextEnvelope = "vault:v3:opaque-ciphertext"
        var requestIndex = 0
        val client = mockClient { request ->
            when (requestIndex++) {
                0 -> respond("", HttpStatusCode.NoContent)
                1 -> respondJson(keyResponse(type = "rsa-2048"))
                2 -> {
                    assertEquals("/v1/transit/encrypt/rsa-key", request.url.encodedPath)
                    val body = request.bodyJson()
                    assertEquals("oaep", body.requiredString("padding_scheme"))
                    assertEquals("sha256", body.requiredString("oaep_hash"))
                    assertEquals(3, body.requiredInt("key_version"))
                    assertContentEquals(plaintext, Base64.decode(body.requiredString("plaintext")))
                    respondJson("""{"data":{"ciphertext":"$ciphertextEnvelope"}}""")
                }

                3 -> {
                    assertEquals("/v1/transit/decrypt/rsa-key", request.url.encodedPath)
                    val body = request.bodyJson()
                    assertEquals(ciphertextEnvelope, body.requiredString("ciphertext"))
                    assertEquals("oaep", body.requiredString("padding_scheme"))
                    assertEquals("sha256", body.requiredString("oaep_hash"))
                    respondJson("""{"data":{"plaintext":"${Base64.encode(plaintext)}"}}""")
                }

                4 -> {
                    assertEquals("/v1/transit/keys/rsa-key/config", request.url.encodedPath)
                    assertTrue(request.bodyJson().requiredBoolean("deletion_allowed"))
                    respond("", HttpStatusCode.NoContent)
                }

                5 -> {
                    assertEquals(HttpMethod.Delete, request.method)
                    assertEquals("/v1/transit/keys/rsa-key", request.url.encodedPath)
                    respond("", HttpStatusCode.NoContent)
                }

                else -> error("Unexpected Vault request")
            }
        }
        val key = runtime(client).generateManagedKey(
            VaultTransitKeyProvider.ID,
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
        assertEquals(VaultTransitKeyProvider.ID, ciphertext.provider)
        assertEquals("3", ciphertext.keyVersion)
        assertFailsWith<IllegalArgumentException> {
            assertNotNull(key.capabilities.decryptor).decrypt(
                ciphertext.copy(keyId = KeyId("other-key")),
                null,
            )
        }
        assertContentEquals(
            plaintext,
            assertNotNull(key.capabilities.decryptor).decrypt(ciphertext, null),
        )
        assertEquals(KeyDeletionResult.Deleted, assertNotNull(key.capabilities.deleter).delete())
        assertEquals(6, requestIndex)
    }

    @Test
    fun `provider failures are sanitized and signature versions are enforced`() = runTest {
        val failingClient = mockClient { respondError(HttpStatusCode.Forbidden, "credential=super-secret") }
        val failure = assertFailsWith<KmsProviderException> {
            runtime(failingClient).generateManagedKey(
                VaultTransitKeyProvider.ID,
                GenerateManagedKeyRequest(
                    id = KeyId("logical-key"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN),
                    providerOptions = options.encode(),
                ),
            )
        }
        assertFalse("super-secret" in failure.message.orEmpty())

        var requestIndex = 0
        val wrongVersionClient = mockClient { _ ->
            when (requestIndex++) {
                0 -> respond("", HttpStatusCode.NoContent)
                1 -> respondJson(keyResponse())
                2 -> respondJson("""{"data":{"signature":"vault:v4:${vaultJwsBase64.encode(ByteArray(64))}"}}""")
                else -> error("Unexpected Vault request")
            }
        }
        val key = runtime(wrongVersionClient).generateManagedKey(
            VaultTransitKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("logical-key"),
                spec = KeySpec.Ec(EcCurve.P256),
                usages = setOf(KeyUsage.SIGN),
                providerOptions = options.encode(),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            assertNotNull(key.capabilities.signer).sign(byteArrayOf(1), algorithm)
        }
    }

    @Test
    fun `jws marshaling uses base64url and asn1 uses the standard alphabet`() = runTest {
        // Signature bytes chosen so that the base64 alphabets actually differ: 0xFB 0xEF encodes to "++8" under the
        // standard alphabet and "--8" under base64url. Decoding a jws payload with the standard alphabet therefore
        // throws on any real Vault response, which is why this needs a fixture that is not symmetric.
        val distinguishing = ByteArray(64) { index -> if (index % 2 == 0) 0xFB.toByte() else 0xEF.toByte() }
        assertTrue('-' in vaultJwsBase64.encode(distinguishing))
        assertTrue('+' in Base64.encode(distinguishing))

        mapOf(
            EcdsaSignatureEncoding.IEEE_P1363 to ("jws" to vaultJwsBase64),
            EcdsaSignatureEncoding.DER to ("asn1" to Base64.Default),
        ).forEach { (encoding, expectation) ->
            val (marshaling, codec) = expectation
            val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256, encoding)
            var requestIndex = 0
            val client = mockClient { request ->
                when (requestIndex++) {
                    0 -> respond("", HttpStatusCode.NoContent)
                    1 -> respondJson(keyResponse())
                    2 -> {
                        assertEquals(marshaling, request.bodyJson().requiredString("marshaling_algorithm"))
                        respondJson("""{"data":{"signature":"vault:v3:${codec.encode(distinguishing)}"}}""")
                    }
                    3 -> {
                        // Round trip: the same codec must be used when handing a signature back for verification.
                        assertEquals(
                            "vault:v3:${codec.encode(distinguishing)}",
                            request.bodyJson().requiredString("signature"),
                        )
                        respondJson("""{"data":{"valid":true}}""")
                    }
                    else -> error("Unexpected Vault request: ${request.url}")
                }
            }
            val key = runtime(client).generateManagedKey(
                VaultTransitKeyProvider.ID,
                GenerateManagedKeyRequest(
                    id = KeyId("logical-key"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                    providerOptions = options.encode(),
                ),
            )

            assertContentEquals(
                distinguishing,
                assertNotNull(key.capabilities.signer).sign("message".encodeToByteArray(), algorithm),
            )
            assertTrue(
                assertNotNull(key.capabilities.verifier)
                    .verify("message".encodeToByteArray(), distinguishing, algorithm)
            )
        }
    }

    @Test
    fun `RSA-PSS requests the salt length it advertises`() = runTest {
        // The provider advertises RsaPss(saltLengthBytes = digest.outputSizeBytes) and rejects anything else, but
        // Vault defaults to salt_length=auto (the maximum). Without asking for "hash" the signature is not
        // PS256-conformant while the API contract claims it is.
        var requestIndex = 0
        val signature = ByteArray(256) { 7 }
        val client = mockClient { request ->
            when (requestIndex++) {
                0 -> respond("", HttpStatusCode.NoContent)
                1 -> respondJson(keyResponse(type = "rsa-2048"))
                2 -> {
                    val body = request.bodyJson()
                    assertEquals("pss", body.requiredString("signature_algorithm"))
                    assertEquals("hash", body.requiredString("salt_length"))
                    respondJson("""{"data":{"signature":"vault:v3:${Base64.encode(signature)}"}}""")
                }
                else -> error("Unexpected Vault request: ${request.url}")
            }
        }
        val key = runtime(client).generateManagedKey(
            VaultTransitKeyProvider.ID,
            GenerateManagedKeyRequest(
                id = KeyId("logical-key"),
                spec = KeySpec.Rsa(2048),
                usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                providerOptions = options.encode(),
            ),
        )

        assertContentEquals(
            signature,
            assertNotNull(key.capabilities.signer).sign(
                "message".encodeToByteArray(),
                SignatureAlgorithm.RsaPss(DigestAlgorithm.SHA_256, saltLengthBytes = 32),
            ),
        )
    }

    private fun runtime(
        client: HttpClient,
        resolver: VaultCredentialResolver = VaultCredentialResolver { VaultCredential.Token("vault-token") },
    ): CryptoRuntime = CryptoRuntime(
        softwareProviders = emptyList(),
        managedProviders = listOf(VaultTransitKeyProvider(client, resolver)),
    )

    private fun mockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient =
        HttpClient(MockEngine) {
            engine { addHandler(handler) }
        }

    private fun MockRequestHandleScope.respondJson(content: String) = respond(
        content = content,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun keyResponse(type: String = "ecdsa-p256"): String = """
        {
          "data": {
            "type": "$type",
            "latest_version": 3,
            "keys": {
              "3": {"public_key": "-----BEGIN PUBLIC KEY-----\n${Base64.encode(TEST_SPKI)}\n-----END PUBLIC KEY-----"}
            }
          }
        }
    """.trimIndent()

    /**
     * Real Vault emits the `jws` marshaling payload with Go's base64.RawURLEncoding (unpadded base64url) and the
     * `asn1` payload with base64.StdEncoding. Encoding both with the standard alphabet made the mock agree with a
     * bug in the provider instead of with Vault.
     */
    private fun signatureResponse(signature: ByteArray): String =
        """{"data":{"signature":"vault:v3:${vaultJwsBase64.encode(signature)}"}}"""

    companion object {
        private val TEST_SPKI = byteArrayOf(0x30, 0x03, 0x02, 0x01, 0x01)
        private val vaultJwsBase64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    }
}

private fun HttpRequestData.bodyJson(): JsonObject {
    val content = body as? OutgoingContent.ByteArrayContent
        ?: error("Expected a byte-array request body")
    return Json.parseToJsonElement(content.bytes().decodeToString()) as JsonObject
}

private fun JsonObject.requiredString(name: String): String =
    requireNotNull(this[name]).jsonPrimitive.content

private fun JsonObject.requiredBoolean(name: String): Boolean =
    requireNotNull(this[name]).jsonPrimitive.boolean

private fun JsonObject.requiredInt(name: String): Int =
    requireNotNull(this[name]).jsonPrimitive.int
