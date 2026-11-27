package id.walt.crypto2.kms.csc

import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.EcdsaSignatureEncoding
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.ManagedKey
import id.walt.crypto2.kms.CredentialReference
import id.walt.crypto2.kms.KmsProviderException
import id.walt.crypto2.kms.digest
import id.walt.crypto2.providers.GenerateManagedKeyRequest
import id.walt.crypto2.serialization.BinaryData
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class CscKeyProviderTest {
    private val options = CscOptions(
        serviceUrl = "https://rssp.example/csc/v2",
        credentialReference = CredentialReference("qtsp-seal"),
    )
    private val credentials = CscCredentials(CLIENT_ID, CLIENT_SECRET, mapOf("PIN" to PIN))
    private val message = "JWS signing input".encodeToByteArray()

    @Test
    fun `attaches a usable credential and restores it without the RSSP`() = runTest {
        val rssp = TestRsspKey.generate()
        val chain = testChain(rssp.key, ca = TestRsspKey.generate().key)
        val stub = CscStub(chain)

        val stored = provider(stub).storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID)

        assertEquals(listOf(INFO_PATH, TOKEN_PATH, CREDENTIAL_INFO_PATH), stub.requests)
        assertEquals("rssp.example", stub.tokenHost)
        val token = stub.body(TOKEN_PATH)
        assertTrue("grant_type=client_credentials" in token)
        assertTrue("client_id=$CLIENT_ID" in token)
        assertTrue("client_secret=$CLIENT_SECRET" in token)
        val credentialInfo = stub.json(CREDENTIAL_INFO_PATH)
        assertEquals(CREDENTIAL_ID, credentialInfo.string("credentialID"))
        assertEquals("chain", credentialInfo.string("certificates"))
        assertEquals(CscKeyProvider.ID, stored.provider)
        assertEquals(KeySpec.Ec(EcCurve.P256), stored.spec)
        assertEquals(setOf(KeyUsage.SIGN, KeyUsage.VERIFY), stored.usages)
        assertContentEquals(rssp.spki, (stored.publicKey as EncodedKey.SpkiDer).data.toByteArray())
        assertEquals(chain.map(::BinaryData), CscKeyProvider.certificateChain(stored))

        val offline = CscKeyProvider(
            client = HttpClient(MockEngine) { engine { addHandler { error("restore must not call the RSSP") } } },
            credentialResolver = { error("restore must not resolve credentials") },
            now = { NOW },
        )
        val key = CryptoRuntime(emptyList(), listOf(offline)).restore(stored) as ManagedKey
        assertEquals(stored, key.storedKey)
        val exported = requireNotNull(key.capabilities.publicKeyExporter).exportPublicKey() as EncodedKey.SpkiDer
        assertContentEquals(rssp.spki, exported.data.toByteArray())
        val signature = rssp.sign(message, EcdsaSignatureEncoding.IEEE_P1363)
        assertTrue(requireNotNull(key.capabilities.verifier).verify(message, signature, es256(EcdsaSignatureEncoding.IEEE_P1363)))
    }

    @Test
    fun `rejects credentials outside the supported scope`() = runTest {
        val rssp = TestRsspKey.generate()
        val leaf = testCertificate(rssp.key)
        val expired = testCertificate(rssp.key, notAfter = NOW - 1.seconds)
        val notYetValid = testCertificate(rssp.key, notBefore = NOW + 1.seconds)
        val p384 = testCertificate(
            TestRsspKey.generate(EcCurve.P384).key,
            signatureAlgorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_384, EcdsaSignatureEncoding.DER),
        )
        // RSSP answers the provider cannot use are provider failures; caller input and configuration are not.
        val remote = KmsProviderException::class
        val input = IllegalArgumentException::class
        val cases = listOf(
            Rejection("CSC 2.1", "Unsupported CSC API version: 2.1.0.0", remote) { specs = "2.1.0.0" },
            Rejection("CSC 1.0", "Unsupported CSC API version: 1.0.4.0", remote) { specs = "1.0.4.0" },
            Rejection("no client credentials", "oauth2client", remote) { authTypes = listOf("basic", "oauth2code") },
            Rejection("no oauth2 base URI", "configure tokenEndpoint", input) { oauth2 = null },
            Rejection("oauth2 on another host", "configure tokenEndpoint", input) { oauth2 = "https://auth.example" },
            Rejection("oauth2 over HTTP", "configure tokenEndpoint", input) { oauth2 = "http://rssp.example" },
            Rejection("disabled key", "key is disabled", remote) { keyStatus = "disabled" },
            Rejection("RSA key", "ECDSA with SHA-256", remote) { keyAlgorithms = listOf("1.2.840.113549.1.1.11") },
            Rejection("P-384 key", "curve P-256", remote) { keyCurve = "1.3.132.0.34" },
            Rejection("revoked certificate", "certificate is revoked", remote) { certificateStatus = "revoked" },
            Rejection("oauth2code authorization", "authorization mode: oauth2code", remote) { authMode = "oauth2code" },
            Rejection("dynamic OTP", "object OTP needs user interaction", remote) {
                authObjects = """[{"type":"Password","id":"PIN"},{"type":"Password","id":"OTP","generator":"totp"}]"""
            },
            Rejection("out-of-band object", "object PIN needs user interaction", remote) {
                authObjects = """[{"type":"PasswordOOB","id":"PIN"}]"""
            },
            Rejection("PIN that is not stored", "object SIGNING_PIN", input) {
                authObjects = """[{"type":"Password","id":"SIGNING_PIN"}]"""
            },
            Rejection("exclusive alternative", "needs a choice", remote) {
                authObjects = TWO_PASSWORDS
                authExpression = "\"PIN XOR SECOND_PIN\""
            },
            Rejection("inclusive alternative", "needs a choice", remote) {
                authObjects = TWO_PASSWORDS
                authExpression = "\"(PIN AND SECOND_PIN) OR SECOND_PIN\""
            },
            Rejection("expression with an unknown object", "unknown object OTP", remote) {
                authExpression = "\"PIN AND OTP\""
            },
            Rejection("incomplete expression", "Unsupported CSC authorization expression", remote) {
                authExpression = "\"(PIN AND\""
            },
            Rejection("expression that is not a string", "not a string: expression", remote) {
                authExpression = "[\"PIN\"]"
            },
            Rejection("explicit authorization without objects", "missing array: objects", remote) {
                authObjects = null
            },
            Rejection("objects that are not an array", "missing array: objects", remote) { authObjects = "{}" },
            Rejection("expired leaf", "not valid now", remote) {
                certificates = listOf(expired)
            },
            Rejection("leaf not yet valid", "not valid now", remote) {
                certificates = listOf(notYetValid)
            },
            Rejection("malformed leaf", "invalid leaf certificate", remote) { certificates = listOf(byteArrayOf(0x30, 0x00)) },
            Rejection("leaf with a P-384 key", "P-256 public key", remote) { certificates = listOf(p384) },
            Rejection("leaf with an undecodable P-256 point", "usable P-256 public key", remote) {
                certificates = listOf(withUndecodablePoint(leaf, rssp.spki))
            },
        )
        cases.forEach { case ->
            val stub = CscStub(listOf(leaf)).apply(case.change)
            val failure = assertFails(case.name) {
                provider(stub).storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID)
            }
            assertTrue(case.type.isInstance(failure), "${case.name}: ${failure::class.simpleName}: ${failure.message}")
            assertTrue(case.expectedMessage in failure.message.orEmpty(), "${case.name}: ${failure.message}")
        }

        val stub = CscStub(listOf(testCertificate(rssp.key)))
        assertFailsWith<IllegalArgumentException>("encryption usage") {
            provider(stub).storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID, setOf(KeyUsage.ENCRYPT))
        }
        assertFailsWith<UnsupportedOperationException> {
            provider(stub).generate(GenerateManagedKeyRequest(KeyId("new"), KeySpec.Ec(EcCurve.P256), setOf(KeyUsage.SIGN)))
        }
        assertTrue(stub.requests.isEmpty())
    }

    @Test
    fun `restore rejects a descriptor whose public key differs from its leaf certificate`() = runTest {
        val rssp = TestRsspKey.generate()
        val other = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key)))
        val stored = provider(stub).storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID)

        val failure = assertFailsWith<IllegalArgumentException> {
            provider(stub).restore(stored.copy(publicKey = EncodedKey.SpkiDer(BinaryData(other.spki))))
        }
        assertEquals("Stored CSC leaf certificate does not hold the stored public key", failure.message)
    }

    @Test
    fun `restore rejects a descriptor whose leaf certificate cannot be parsed`() = runTest {
        val rssp = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key)))
        val stored = provider(stub).storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID)
        val data = Json.parseToJsonElement(stored.providerData.toByteArray().decodeToString()).jsonObject
        val corrupted = JsonObject(
            data + ("certificateChain" to JsonArray(listOf(JsonPrimitive(Base64.Default.encode(byteArrayOf(0x30, 0x00))))))
        )

        val failure = assertFailsWith<IllegalArgumentException> {
            provider(stub).restore(stored.copy(providerData = BinaryData(corrupted.toString().encodeToByteArray())))
        }
        assertEquals("Stored CSC leaf certificate is invalid", failure.message)
        assertTrue(failure.cause != null)
    }

    @Test
    fun `signs through authorize and signHash and accepts raw or DER signatures`() = runTest {
        val rssp = TestRsspKey.generate()
        val expectedHash = digest(message, DigestAlgorithm.SHA_256).value.toByteArray()
        for (returned in EcdsaSignatureEncoding.entries) {
            for (requested in EcdsaSignatureEncoding.entries) {
                val stub = CscStub(listOf(testCertificate(rssp.key)))
                stub.signatureFor = { hash ->
                    assertContentEquals(expectedHash, hash)
                    rssp.sign(message, returned)
                }
                val key = attachAndRestore(stub)

                val signature = requireNotNull(key.capabilities.signer).sign(message, es256(requested))

                assertTrue(rssp.verify(message, signature, requested), "RSSP $returned, requested $requested")
                assertTrue(requireNotNull(key.capabilities.verifier).verify(message, signature, es256(requested)))
                val authorize = stub.json(AUTHORIZE_PATH)
                assertEquals(CREDENTIAL_ID, authorize.string("credentialID"))
                assertEquals(1, authorize["numSignatures"]!!.jsonPrimitive.int)
                assertEquals(JsonArray(listOf(JsonPrimitive(Base64.Default.encode(expectedHash)))), authorize["hashes"])
                assertEquals(CscClient.SHA256_OID, authorize.string("hashAlgorithmOID"))
                assertEquals(
                    JsonArray(listOf(buildJsonObject { put("id", "PIN"); put("value", PIN) })),
                    authorize["authData"],
                )
                val signHash = stub.json(SIGN_HASH_PATH)
                assertEquals(CREDENTIAL_ID, signHash.string("credentialID"))
                assertEquals(SAD, signHash.string("SAD"))
                assertEquals(authorize["hashes"], signHash["hashes"])
                assertEquals(CscClient.ECDSA_SHA256_OID, signHash.string("signAlgo"))
                assertNull(signHash["operationMode"])
            }
        }
    }

    @Test
    fun `authorizes explicitly when the credential needs no authentication object`() = runTest {
        val rssp = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key)))
        stub.authObjects = "[]"
        stub.signatureFor = { rssp.sign(message, EcdsaSignatureEncoding.IEEE_P1363) }
        val key = attachAndRestore(stub, credentials = CscCredentials(CLIENT_ID, CLIENT_SECRET))

        requireNotNull(key.capabilities.signer).sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363))

        assertEquals(JsonArray(emptyList()), stub.json(AUTHORIZE_PATH)["authData"])
    }

    @Test
    fun `sends only the authentication objects that an AND expression requires`() = runTest {
        val rssp = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key)))
        stub.authObjects = """[{"type":"Password","id":"PIN"},{"type":"Password","id":"SECOND_PIN"},
            |{"type":"Password","id":"OTP","generator":"totp"}]""".trimMargin()
        stub.authExpression = "\"(PIN AND SECOND_PIN)\""
        stub.signatureFor = { rssp.sign(message, EcdsaSignatureEncoding.IEEE_P1363) }
        val credentials = CscCredentials(CLIENT_ID, CLIENT_SECRET, mapOf("PIN" to PIN, "SECOND_PIN" to "0815"))
        val key = attachAndRestore(stub, credentials)

        requireNotNull(key.capabilities.signer).sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363))

        assertEquals(
            JsonArray(
                listOf(
                    buildJsonObject { put("id", "PIN"); put("value", PIN) },
                    buildJsonObject { put("id", "SECOND_PIN"); put("value", "0815") },
                )
            ),
            stub.json(AUTHORIZE_PATH)["authData"],
        )
    }

    @Test
    fun `sends the access token only when it is a Bearer token`() = runTest {
        val rssp = TestRsspKey.generate()
        for (tokenType in listOf(null, "mac")) {
            val stub = CscStub(listOf(testCertificate(rssp.key))).apply { this.tokenType = tokenType }
            val failure = assertFailsWith<KmsProviderException>("token_type $tokenType") {
                provider(stub).storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID)
            }
            assertEquals("CSC token response must have token_type Bearer", failure.message)
            assertEquals(listOf(INFO_PATH, TOKEN_PATH), stub.requests)
        }

        val stub = CscStub(listOf(testCertificate(rssp.key))).apply { tokenType = "bEaReR" }
        provider(stub).storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID)
        assertEquals(listOf(INFO_PATH, TOKEN_PATH, CREDENTIAL_INFO_PATH), stub.requests)
    }

    @Test
    fun `uses a configured token endpoint on another host`() = runTest {
        val rssp = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key))).apply { oauth2 = "https://auth.example" }

        provider(stub).storedKeyForExisting(
            KeyId("qeaa-seal"),
            options.copy(tokenEndpoint = "https://auth.example/oauth2/token"),
            CREDENTIAL_ID,
        )

        assertEquals("auth.example", stub.tokenHost)
    }

    @Test
    fun `a token rejected at attach is fetched again on the next attach`() = runTest {
        val rssp = TestRsspKey.generate()
        for (validateResponses in listOf(false, true)) {
            val stub = CscStub(listOf(testCertificate(rssp.key)), validateResponses)
            val provider = provider(stub)

            stub.credentialInfoStatus = HttpStatusCode.Unauthorized
            val failure = assertFailsWith<KmsProviderException> {
                provider.storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID)
            }
            assertEquals(401, failure.httpStatus, "validateResponses=$validateResponses")
            stub.credentialInfoStatus = HttpStatusCode.OK
            provider.storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID)

            assertEquals(2, stub.count(TOKEN_PATH), "validateResponses=$validateResponses")
        }
    }

    @Test
    fun `authorize answers other than 200 fail before any signature is requested`() = runTest {
        val rssp = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key)))
        val key = attachAndRestore(stub)
        val signer = requireNotNull(key.capabilities.signer)

        stub.authorizeStatus = HttpStatusCode.Accepted
        val outOfBand = assertFailsWith<KmsProviderException> {
            signer.sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363))
        }
        assertTrue("out-of-band" in outOfBand.message.orEmpty(), outOfBand.message)
        stub.authorizeStatus = HttpStatusCode.Created
        val unexpected = assertFailsWith<KmsProviderException> {
            signer.sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363))
        }
        assertTrue("unexpected HTTP 201" in unexpected.message.orEmpty(), unexpected.message)

        assertEquals(2, stub.count(AUTHORIZE_PATH))
        assertEquals(0, stub.count(SIGN_HASH_PATH))
        assertEquals(1, stub.count(TOKEN_PATH))
    }

    @Test
    fun `rejects a signature that does not verify against the pinned key`() = runTest {
        val rssp = TestRsspKey.generate()
        val other = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key)))
        val key = attachAndRestore(stub)
        val signer = requireNotNull(key.capabilities.signer)

        stub.signatureFor = { other.sign(message, EcdsaSignatureEncoding.DER) }
        assertFailsWith<KmsProviderException> { signer.sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363)) }
        stub.signatureFor = { ByteArray(10) { 1 } }
        assertFailsWith<KmsProviderException> { signer.sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363)) }
        assertEquals(2, stub.count(SIGN_HASH_PATH))
    }

    @Test
    fun `an expired leaf certificate costs no request`() = runTest {
        val rssp = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key)))
        val stored = provider(stub).storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID)
        val requests = stub.requests.size

        val key = provider(stub, now = NOT_AFTER + 1.seconds).restore(stored)

        assertFailsWith<IllegalStateException> {
            requireNotNull(key.capabilities.signer).sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363))
        }
        assertEquals(requests, stub.requests.size)
    }

    @Test
    fun `reuses the access token and authorizes every signature`() = runTest {
        val rssp = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key)))
        stub.signatureFor = { rssp.sign(message, EcdsaSignatureEncoding.DER) }
        val key = attachAndRestore(stub)
        val signer = requireNotNull(key.capabilities.signer)

        repeat(2) { signer.sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363)) }

        assertEquals(1, stub.count(TOKEN_PATH))
        assertEquals(2, stub.count(AUTHORIZE_PATH))
        assertEquals(2, stub.count(SIGN_HASH_PATH))
    }

    @Test
    fun `a 401 from the RSSP drops the access token without retrying`() = runTest {
        val rssp = TestRsspKey.generate()
        for (validateResponses in listOf(false, true)) {
            val stub = CscStub(listOf(testCertificate(rssp.key)), validateResponses)
            stub.signatureFor = { rssp.sign(message, EcdsaSignatureEncoding.IEEE_P1363) }
            val key = attachAndRestore(stub)
            val signer = requireNotNull(key.capabilities.signer)

            stub.authorizeStatus = HttpStatusCode.Unauthorized
            val failure = assertFailsWith<KmsProviderException> {
                signer.sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363))
            }
            assertEquals(401, failure.httpStatus, "validateResponses=$validateResponses")
            assertEquals(1, stub.count(AUTHORIZE_PATH))
            stub.authorizeStatus = HttpStatusCode.OK
            signer.sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363))

            assertEquals(2, stub.count(TOKEN_PATH), "validateResponses=$validateResponses")
            assertEquals(1, stub.count(SIGN_HASH_PATH))
        }
    }

    @Test
    fun `other RSSP failures keep the access token`() = runTest {
        val rssp = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key)))
        stub.signatureFor = { rssp.sign(message, EcdsaSignatureEncoding.IEEE_P1363) }
        val key = attachAndRestore(stub)
        val signer = requireNotNull(key.capabilities.signer)

        stub.signHashStatus = HttpStatusCode.InternalServerError
        val failure = assertFailsWith<KmsProviderException> { signer.sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363)) }
        assertEquals(500, failure.httpStatus)
        stub.signHashStatus = HttpStatusCode.OK
        signer.sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363))

        assertEquals(1, stub.count(TOKEN_PATH))
        assertEquals(2, stub.count(SIGN_HASH_PATH))
    }

    @Test
    fun `failures never expose secrets`() = runTest {
        val rssp = TestRsspKey.generate()
        val other = TestRsspKey.generate()
        // The stub's error bodies echo the client secret and the SAD; a validating client puts them in its exceptions.
        for (validateResponses in listOf(false, true)) {
            val failures = mutableListOf<Throwable>()
            val signWith: suspend (CscStub.() -> Unit) -> Unit = { change ->
                val stub = CscStub(listOf(testCertificate(rssp.key)), validateResponses)
                val key = attachAndRestore(stub)
                stub.change()
                failures += assertFails {
                    requireNotNull(key.capabilities.signer).sign(message, es256(EcdsaSignatureEncoding.IEEE_P1363))
                }
            }
            signWith { authorizeStatus = HttpStatusCode.Unauthorized }
            signWith { signHashStatus = HttpStatusCode.BadRequest }
            signWith { signatureFor = { other.sign(message, EcdsaSignatureEncoding.IEEE_P1363) } }
            val rejectedClient = CscStub(listOf(testCertificate(rssp.key)), validateResponses)
                .apply { tokenStatus = HttpStatusCode.Unauthorized }
            failures += assertFails {
                provider(rejectedClient).storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID)
            }

            val messages = failures.flatMap { failure ->
                generateSequence(failure) { it.cause }.mapNotNull { it.message }
            }
            assertEquals(4, failures.size)
            assertTrue(failures.all { it is KmsProviderException }, "Unexpected failure types: $failures")
            assertTrue("HTTP 401" in failures[0].message.orEmpty())
            listOf(CLIENT_SECRET, PIN, ACCESS_TOKEN, SAD).forEach { secret ->
                assertFalse(messages.any { secret in it }, "validation $validateResponses exposes $secret: $messages")
            }
        }
    }

    @Test
    fun `keys sign and verify only and have no deleter`() = runTest {
        val rssp = TestRsspKey.generate()
        val stub = CscStub(listOf(testCertificate(rssp.key)))
        val key = attachAndRestore(stub)
        assertNull(key.capabilities.deleter)
        assertNull(key.capabilities.digestSigner)
        assertEquals(EcdsaSignatureEncoding.entries.map(::es256).toSet(), key.capabilities.signatureAlgorithms)

        val provider = provider(stub)
        val verifyOnly = provider.restore(
            provider.storedKeyForExisting(KeyId("verify-only"), options, CREDENTIAL_ID, setOf(KeyUsage.VERIFY))
        )
        assertNull(verifyOnly.capabilities.signer)
        assertTrue(verifyOnly.capabilities.verifier != null)
    }

    private fun provider(
        stub: CscStub,
        now: Instant = NOW,
        credentials: CscCredentials = this.credentials,
    ) = CscKeyProvider(
        client = stub.client,
        credentialResolver = { reference ->
            assertEquals(options.credentialReference, reference)
            credentials
        },
        now = { now },
    )

    private suspend fun attachAndRestore(stub: CscStub, credentials: CscCredentials = this.credentials): ManagedKey {
        val provider = provider(stub, credentials = credentials)
        return provider.restore(provider.storedKeyForExisting(KeyId("qeaa-seal"), options, CREDENTIAL_ID))
    }

    private class Rejection(
        val name: String,
        val expectedMessage: String,
        val type: KClass<out Throwable>,
        val change: CscStub.() -> Unit,
    )
}

private fun kotlinx.serialization.json.JsonObject.string(name: String): String = requireNotNull(this[name]).jsonPrimitive.content

private const val TWO_PASSWORDS = """[{"type":"Password","id":"PIN"},{"type":"Password","id":"SECOND_PIN"}]"""
