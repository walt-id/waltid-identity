package id.waltid.openid4vp.wallet

import id.walt.cose.coseCompliantCbor
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.migration.v1.V1KeyMigration
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.mdoc.objects.handover.OpenID4VPDCAPIHandoverInfo
import id.walt.mdoc.objects.sha256
import id.walt.openid4vp.clientidprefix.ClientIdError
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DcApiWalletTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `unsigned request ignores client id and expected origins`() = runTest {
        val request = DcApiWallet.resolveRequest(
            protocol = "openid4vp-v1-unsigned",
            data = unsignedRequestData(
                extra = """
                    "client_id": "attacker-supplied",
                    "expected_origins": ["https://attacker.example"],
                """.trimIndent(),
            ),
            origin = "https://verifier.example",
        )

        assertEquals(DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED, request.protocol)
        assertNull(request.authorizationRequest.clientId)
        assertNull(request.authorizationRequest.expectedOrigins)
        assertEquals("origin:https://verifier.example", request.holderBindingAudience)
    }

    @Test
    fun `unsigned and compact signed protocols are accepted`() = runTest {
        DcApiWallet.resolveRequest(
            protocol = "openid4vp-v1-unsigned",
            data = unsignedRequestData(),
            origin = "https://verifier.example",
        )
        val (data, trust) = signedRequest(JWKKey.generate(KeyType.Ed25519))
        DcApiWallet.resolveRequest(
            protocol = "openid4vp-v1-signed",
            data = data,
            origin = "https://verifier.example",
            trustConfiguration = trust,
        )
        listOf("openid4vp-v1-multisigned", "org-iso-mdoc", "").forEach { protocol ->
            assertFailsWith<UnsupportedDcApiProtocolException>("protocol '$protocol' must be rejected") {
                DcApiWallet.resolveRequest(
                    protocol = protocol,
                    data = unsignedRequestData(),
                    origin = "https://verifier.example",
                )
            }
        }
    }

    @Test
    fun `signed request authenticates client id and binds the platform origin`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val (data, trust) = signedRequest(key)
        val request = DcApiWallet.resolveRequest(
            protocol = "openid4vp-v1-signed",
            data = data,
            origin = "https://verifier.example",
            trustConfiguration = trust,
        )

        assertEquals(DcApiRequestProtocol.OPENID4VP_V1_SIGNED, request.protocol)
        assertEquals("verifier2", request.authorizationRequest.clientId)
        assertEquals(listOf("https://verifier.example"), request.authorizationRequest.expectedOrigins)
        assertEquals("origin:https://verifier.example", request.holderBindingAudience)
    }

    @Test
    fun `signed request rejects an origin missing from expected origins`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val (data, trust) = signedRequest(key, origin = "https://verifier.example")
        assertFailsWith<IllegalArgumentException> {
            DcApiWallet.resolveRequest(
                protocol = "openid4vp-v1-signed",
                data = data,
                origin = "https://attacker.example",
                trustConfiguration = trust,
            )
        }
    }

    @Test
    fun `signed request rejects an invalid signature`() = runTest {
        val trustedKey = JWKKey.generate(KeyType.Ed25519)
        val attackerKey = JWKKey.generate(KeyType.Ed25519)
        val (data, _) = signedRequest(attackerKey)
        val trust = ClientIdTrustConfiguration(
            preRegisteredClients = mapOf(
                "verifier2" to ClientMetadata(
                    jwks = ClientMetadata.Jwks(listOf(trustedKey.getPublicKey().exportJWKObject())),
                )
            ),
        )
        val error = assertFailsWith<AuthorizationRequestResolver.SignedAuthorizationRequestValidationException> {
            DcApiWallet.resolveRequest(
                protocol = "openid4vp-v1-signed",
                data = data,
                origin = "https://verifier.example",
                trustConfiguration = trust,
            )
        }
        assertEquals(ClientIdError.InvalidSignature, error.clientIdError)
    }

    @Test
    fun `signed request with encrypted response wraps members in a jwe`() = runTest {
        val key = JWKKey.generate(KeyType.Ed25519)
        val (data, trust) = signedRequest(
            key = key,
            responseMode = "dc_api.jwt",
            clientMetadata = encryptionClientMetadata(),
        )
        val request = DcApiWallet.resolveRequest(
            protocol = "openid4vp-v1-signed",
            data = data,
            origin = "https://verifier.example",
            trustConfiguration = trust,
        )
        val response = DcApiWallet.buildResponse(
            request = request,
            vpToken = """{"pid":["presentation"]}""",
        )

        assertEquals("openid4vp-v1-signed", response.protocol)
        assertEquals(setOf("response"), response.data.keys)
        val jwe = assertNotNull(response.data["response"]?.jsonPrimitive?.content)
        assertEquals(5, jwe.split('.').size, "response is not a compact JWE")
        assertFalse(jwe.contains("presentation"), "the vp_token leaked into the JWE in cleartext")
    }

    @Test
    fun `only the two dc api response modes are accepted`() {
        listOf(OpenID4VPResponseMode.DC_API, OpenID4VPResponseMode.DC_API_JWT).forEach { responseMode ->
            DcApiWallet.validateAuthorizationRequest(
                authorizationRequest(responseMode = responseMode, clientMetadata = encryptionClientMetadata()),
            )
        }
        listOf(
            OpenID4VPResponseMode.DIRECT_POST,
            OpenID4VPResponseMode.DIRECT_POST_JWT,
            OpenID4VPResponseMode.FRAGMENT,
        ).forEach { responseMode ->
            assertFailsWith<IllegalArgumentException>("response_mode '$responseMode' must be rejected") {
                DcApiWallet.validateAuthorizationRequest(authorizationRequest(responseMode = responseMode))
            }
        }
    }

    @Test
    fun `cleartext response is returned to the platform without transport`() = runTest {
        val request = ResolvedDcApiRequest(
            protocol = DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
            origin = "https://verifier.example",
            authorizationRequest = authorizationRequest(),
        )

        val response = DcApiWallet.buildResponse(
            request = request,
            vpToken = """{"pid":["presentation"]}""",
        )

        assertEquals("openid4vp-v1-unsigned", response.protocol)
        assertEquals(
            "presentation",
            response.data["vp_token"]?.jsonObject?.get("pid")?.jsonArray?.single()?.jsonPrimitive?.content,
        )
        assertTrue(response.data.containsKey("vp_token"))
        assertFalse(response.data.containsKey("response"))
    }

    @Test
    fun `dc api ignores parameters that are not defined by appendix A`() = runTest {
        val authorizationRequest = authorizationRequest(
            state = "state-123",
            redirectUri = "https://verifier.example/redirect",
            responseUri = "https://verifier.example/direct-post",
        )
        DcApiWallet.validateAuthorizationRequest(authorizationRequest)
        val response = DcApiWallet.buildResponse(
            request = ResolvedDcApiRequest(
                protocol = DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
                origin = "https://verifier.example",
                authorizationRequest = authorizationRequest,
            ),
            vpToken = "{}",
        )

        assertFalse(response.data.containsKey("state"))
        assertEquals(setOf("vp_token"), response.data.keys)
    }

    /**
     * Under `dc_api.jwt` the operating system and the website that called `getCredential` both relay the
     * response, so the disclosed claims must be readable by neither. The member set is asserted because
     * a builder emitting `vp_token` alongside `response` would encrypt nothing in practice.
     */
    @Test
    fun `an encrypted response mode wraps the members in a jwe the platform cannot read`() = runTest {
        val response = DcApiWallet.buildResponse(
            request = ResolvedDcApiRequest(
                protocol = DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
                origin = "https://verifier.example",
                authorizationRequest = authorizationRequest(
                    responseMode = OpenID4VPResponseMode.DC_API_JWT,
                    clientMetadata = encryptionClientMetadata(),
                ),
            ),
            vpToken = """{"pid":["presentation"]}""",
        )

        assertEquals(setOf("response"), response.data.keys)
        val jwe = assertNotNull(response.data["response"]?.jsonPrimitive?.content)
        // Compact JWE: five base64url segments, and the disclosed claim appears in none of them.
        assertEquals(5, jwe.split('.').size, "response is not a compact JWE")
        assertFalse(jwe.contains("presentation"), "the vp_token leaked into the JWE in cleartext")
        assertEquals(
            VERIFIER_KEY_ID,
            Json.parseToJsonElement(jwe.substringBefore('.').decodeBase64Url())
                .jsonObject["kid"]?.jsonPrimitive?.content,
            "the JWE protected header must name the verifier key the wallet encrypted to",
        )
    }

    /**
     * The verifier can actually decrypt what the test above only proves *looks* encrypted. The key pair
     * is fixed rather than generated: a wallet encrypting to the wrong key would still round-trip if both
     * halves came from key material this test chose itself.
     */
    @Test
    fun `the verifier decrypts the encrypted response back to the vp token it asked for`() = runTest {
        val vpToken = """{"pid":["presentation"]}"""
        val response = DcApiWallet.buildResponse(
            request = ResolvedDcApiRequest(
                protocol = DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
                origin = "https://verifier.example",
                authorizationRequest = authorizationRequest(
                    responseMode = OpenID4VPResponseMode.DC_API_JWT,
                    clientMetadata = ClientMetadata(
                        jwks = ClientMetadata.Jwks(listOf(json.parseToJsonElement(DECRYPTABLE_PUBLIC_JWK).jsonObject)),
                        encryptedResponseEncValuesSupported = listOf("A256GCM"),
                    ),
                ),
            ),
            vpToken = vpToken,
        )

        val recipientKey = CryptoRuntime(defaultSoftwareKeyProviders()).restore(
            V1KeyMigration().migrate(
                recordId = KeyId("verifier-response-key"),
                serialized = buildJsonObject {
                    put("type", "jwk")
                    put("jwk", json.parseToJsonElement(DECRYPTABLE_PRIVATE_JWK).jsonObject)
                },
                usages = setOf(KeyUsage.KEY_AGREEMENT),
            )
        )
        val decrypted = CompactJwe.decrypt(
            compactJwe = assertNotNull(response.data["response"]?.jsonPrimitive?.content),
            recipientKey = recipientKey,
            allowedContentEncryptions = setOf(JweContentEncryption.A256GCM),
        )

        assertEquals("A256GCM", decrypted.protectedHeader["enc"]?.jsonPrimitive?.content)
        assertEquals(
            buildJsonObject { put("vp_token", json.parseToJsonElement(vpToken)) },
            Json.parseToJsonElement(decrypted.plaintext.decodeToString()),
        )
    }

    /**
     * `dc_api.jwt` without usable `client_metadata` keys must fail rather than silently degrade to a
     * cleartext response the verifier would still accept as an answer to its encrypted request.
     */
    @Test
    fun `an encrypted response mode without verifier encryption keys fails closed`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            DcApiWallet.buildResponse(
                request = ResolvedDcApiRequest(
                    protocol = DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
                    origin = "https://verifier.example",
                    authorizationRequest = authorizationRequest(responseMode = OpenID4VPResponseMode.DC_API_JWT),
                ),
                vpToken = "{}",
            )
        }
    }

    @Test
    fun `a non dc api response mode cannot reach the response builder`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            DcApiWallet.buildResponse(
                request = ResolvedDcApiRequest(
                    protocol = DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
                    origin = "https://verifier.example",
                    authorizationRequest = authorizationRequest(responseMode = OpenID4VPResponseMode.DIRECT_POST),
                ),
                vpToken = "{}",
            )
        }
    }

    @Test
    fun `protocol error response has only the error member`() {
        val response = DcApiWallet.buildErrorResponse(
            DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
            WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST,
        )

        assertEquals("openid4vp-v1-unsigned", response.protocol)
        assertEquals(setOf("error"), response.data.keys)
        assertEquals("invalid_request", response.data["error"]?.jsonPrimitive?.content)
    }

    /**
     * The full accepted matrix, as a table: this is the sole origin canonicalizer, and its output is
     * hashed into the mdoc session transcript that the verifier reconstructs from `expected_origins`
     * without ever seeing the wallet's copy. A platform-local implementation disagreeing by one
     * character produces a device signature no verifier can reproduce, visible only as an opaque
     * `device-auth` rejection.
     */
    @Test
    fun `platform origin canonicalization accepts and normalizes every supported origin shape`() {
        mapOf(
            // An Android app origin is opaque and passes through verbatim; altering it would break the
            // verifier's exact match.
            "android:apk-key-hash:abc123" to "android:apk-key-hash:abc123",
            "https://verifier.example" to "https://verifier.example",
            // Scheme and host are case-insensitive per RFC 3986, so both are folded down.
            "HTTPS://verifier.example" to "https://verifier.example",
            "https://VERIFIER.example" to "https://verifier.example",
            "https://Verifier.Example:8443" to "https://verifier.example:8443",
            // A default port is dropped and a non-default one kept, for both schemes, because
            // `https://x` and `https://x:443` are the same origin and must hash identically.
            "https://verifier.example:443" to "https://verifier.example",
            "https://verifier.example:8443" to "https://verifier.example:8443",
            "http://localhost" to "http://localhost",
            "http://localhost:80" to "http://localhost",
            "http://localhost:8080" to "http://localhost:8080",
            // A trailing empty path is not part of an origin.
            "https://verifier.example/" to "https://verifier.example",
            // The loopback forms a browser treats as a secure context, so a local verifier works.
            "http://127.0.0.1:9000" to "http://127.0.0.1:9000",
            "http://[::1]:8080" to "http://[::1]:8080",
            "http://verifier.localhost:8080" to "http://verifier.localhost:8080",
        ).forEach { (raw, expected) ->
            assertEquals(expected, DcApiWallet.canonicalizePlatformOrigin(raw), "canonicalizing '$raw'")
            // Idempotent, so an adapter can pass this output on without the value drifting.
            assertEquals(expected, DcApiWallet.canonicalizePlatformOrigin(expected), "not idempotent for '$raw'")
        }
    }

    /**
     * The rejected matrix. Each entry carries something an origin cannot: a component beyond
     * scheme/host/port, or a transport that is not a secure context. Accepting one would let two
     * different requesters canonicalize to the same origin, or bind the session transcript to a
     * plaintext caller as though it were authenticated.
     */
    @Test
    fun `platform origin canonicalization rejects anything that is not scheme host and port`() {
        listOf(
            // Non-loopback HTTP: the OS cannot attest a plaintext caller's identity.
            "http://verifier.example",
            "http://verifier.example:8080",
            // `localhost` as a *suffix* is a different registrable domain, not loopback.
            "http://notlocalhost",
            "http://localhost.example",
            // Components an origin does not have. A path, query or fragment would otherwise be
            // silently dropped, making two distinct URLs canonicalize to one origin.
            "https://verifier.example/path",
            "https://verifier.example/?a=b",
            "https://verifier.example?a=b",
            "https://verifier.example/#fragment",
            "https://verifier.example#fragment",
            // Credentials in the authority: `user@host` is attacker-controlled text that reads as the
            // host in a UI, and is not part of the origin.
            "https://user@verifier.example",
            "https://user:password@verifier.example",
            // Neither a web origin nor the Android app form.
            "verifier.example",
            "ftp://verifier.example",
            "android:apk-key-hash:not+base64url",
            "android:apk-key-hash:",
            // Blank or untrimmed: whitespace would change the hash while looking identical.
            "",
            "   ",
            " https://verifier.example",
            "https://verifier.example ",
        ).forEach { raw ->
            assertFailsWith<IllegalArgumentException>("should have rejected '$raw'") {
                DcApiWallet.canonicalizePlatformOrigin(raw)
            }
        }
    }

    @Test
    fun `a request object is rejected for the unsigned protocol`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            DcApiWallet.resolveRequest(
                protocol = "openid4vp-v1-unsigned",
                data = JsonObject(mapOf("request" to JsonPrimitive("a.b.c"))),
                origin = "https://verifier.example",
            )
        }
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun `mdoc DC API handover binds raw origin nonce and encryption thumbprint`() {
        val origin = "https://verifier.example"
        val nonce = "nonce-123"
        val thumbprint = "AQIDBA"
        val transcript = MdocPresenter.buildDcApiSessionTranscript(origin, nonce, thumbprint)
        val expectedInfo = OpenID4VPDCAPIHandoverInfo(
            origin = origin,
            nonce = nonce,
            jwkThumbprint = byteArrayOf(1, 2, 3, 4),
        )

        assertEquals("OpenID4VPDCAPIHandover", transcript.oid4VPHandover?.identifier)
        assertTrue(
            transcript.oid4VPHandover?.infoHash?.contentEquals(
                coseCompliantCbor.encodeToByteArray(expectedInfo).sha256(),
            ) == true,
        )
    }

    /**
     * Pins `OpenID4VPDCAPIHandoverInfo` to its OID4VP 1.0 Appendix B.2.6.2 wire bytes.
     *
     * The expected values are hand-derived from the spec rather than produced by
     * [coseCompliantCbor], so a serializer change that alters the encoding — field order, a map
     * instead of an array, a tag on the thumbprint — fails here instead of silently producing a
     * transcript no verifier can reproduce.
     */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    @Test
    fun `DC API handover info matches the spec CBOR encoding byte for byte`() {
        val origin = "https://verifier.example"
        val nonce = "nonce-123"

        // 83                              array(3)
        //   78 18 "https://verifier.example"  text(24)
        //   69 "nonce-123"                    text(9)
        //   44 01020304                       bytes(4)
        val encrypted = coseCompliantCbor.encodeToByteArray(
            OpenID4VPDCAPIHandoverInfo(origin, nonce, jwkThumbprint = byteArrayOf(1, 2, 3, 4)),
        )
        assertEquals(
            "83781868747470733a2f2f76657269666965722e6578616d706c65696e6f6e63652d3132334401020304",
            encrypted.toHexString(),
        )

        // Same, with the thumbprint absent: f6 (null) rather than an omitted element, because the
        // array arity is fixed at 3.
        val unencrypted = coseCompliantCbor.encodeToByteArray(
            OpenID4VPDCAPIHandoverInfo(origin, nonce, jwkThumbprint = null),
        )
        assertEquals(
            "83781868747470733a2f2f76657269666965722e6578616d706c65696e6f6e63652d313233f6",
            unencrypted.toHexString(),
        )

        // The handover the transcript carries is [ "OpenID4VPDCAPIHandover", sha256(handoverInfo) ].
        val transcript = MdocPresenter.buildDcApiSessionTranscript(origin, nonce, "AQIDBA")
        assertEquals(
            "206b7a9625010915900a6fd3a3ef28b0ece0ec15c4207ad74f731f6ed03f73e5",
            transcript.oid4VPHandover?.infoHash?.toHexString(),
        )
    }

    private suspend fun signedRequest(
        key: JWKKey,
        origin: String = "https://verifier.example",
        clientId: String = "verifier2",
        responseMode: String = "dc_api",
        clientMetadata: ClientMetadata? = null,
    ): Pair<JsonObject, ClientIdTrustConfiguration> {
        val payload = buildJsonObject {
            put("client_id", clientId)
            put("response_type", "vp_token")
            put("response_mode", responseMode)
            put("nonce", "nonce-123")
            put("aud", AuthorizationRequestResolver.DEFAULT_REQUEST_OBJECT_AUDIENCE)
            put("expected_origins", buildJsonArray { add(JsonPrimitive(origin)) })
            put("dcql_query", unsignedRequestData()["dcql_query"]!!)
            clientMetadata?.let {
                put("client_metadata", json.encodeToJsonElement(ClientMetadata.serializer(), it))
            }
        }
        val requestObject = key.signJws(
            payload.toString().encodeToByteArray(),
            mapOf("typ" to JsonPrimitive("oauth-authz-req+jwt")),
        )
        return buildJsonObject { put("request", JsonPrimitive(requestObject)) } to ClientIdTrustConfiguration(
            preRegisteredClients = mapOf(
                clientId to ClientMetadata(
                    jwks = ClientMetadata.Jwks(listOf(key.getPublicKey().exportJWKObject())),
                )
            ),
        )
    }

    private fun unsignedRequestData(extra: String = ""): JsonObject = json.parseToJsonElement(
        """
        {
          $extra
          "response_type": "vp_token",
          "response_mode": "dc_api",
          "nonce": "nonce-123",
          "dcql_query": {
            "credentials": [{
              "id": "pid",
              "format": "mso_mdoc",
              "meta": {"doctype_value": "eu.europa.ec.eudi.pid.1"}
            }]
          }
        }
        """.trimIndent(),
    ).jsonObject

    private fun authorizationRequest(
        responseMode: OpenID4VPResponseMode = OpenID4VPResponseMode.DC_API,
        state: String? = null,
        redirectUri: String? = null,
        responseUri: String? = null,
        clientMetadata: ClientMetadata? = null,
    ): AuthorizationRequest = json.decodeFromJsonElement(
        AuthorizationRequest.serializer(),
        unsignedRequestData().toMutableMap().apply {
            this["response_mode"] = Json.parseToJsonElement("\"${responseMode.serialized()}\"")
            state?.let { this["state"] = JsonPrimitive(it) }
            redirectUri?.let { this["redirect_uri"] = JsonPrimitive(it) }
            responseUri?.let { this["response_uri"] = JsonPrimitive(it) }
        }.let(::JsonObject),
    ).copy(clientMetadata = clientMetadata)

    /** Verifier response-encryption metadata in the shape `ResponseEncryption.resolveCrypto2` needs. */
    private fun encryptionClientMetadata(): ClientMetadata = ClientMetadata(
        jwks = ClientMetadata.Jwks(
            listOf(
                Json.parseToJsonElement(
                    """{
                        "kty":"EC",
                        "crv":"P-256",
                        "x":"y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
                        "y":"jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg",
                        "use":"enc",
                        "kid":"$VERIFIER_KEY_ID",
                        "alg":"ECDH-ES"
                    }""",
                ).jsonObject,
            ),
        ),
        encryptedResponseEncValuesSupported = listOf("A256GCM"),
    )

    private fun OpenID4VPResponseMode.serialized(): String =
        Json.encodeToString(OpenID4VPResponseMode.serializer(), this).trim('"')

    private fun String.decodeBase64Url(): String = decodeFromBase64Url().decodeToString()

    private companion object {
        private const val VERIFIER_KEY_ID = "enc-key"

        /**
         * A fixed P-256 key pair standing in for the verifier's published response-encryption key. The
         * public half goes into `client_metadata`; the private half only ever decrypts in the test.
         */
        private const val DECRYPTABLE_PUBLIC_JWK = """{
            "kty":"EC",
            "crv":"P-256",
            "x":"i5_Mav2at_Apor6AD8pFLoEZIy5YVxzZLD8PyvGZCo4",
            "y":"8KWjGEMdQu0tnKqvZ2lQJUsbz9nLskFuIjcv9FV0kvA",
            "use":"enc",
            "kid":"verifier-response-key",
            "alg":"ECDH-ES"
        }"""

        private const val DECRYPTABLE_PRIVATE_JWK = """{
            "kty":"EC",
            "crv":"P-256",
            "x":"i5_Mav2at_Apor6AD8pFLoEZIy5YVxzZLD8PyvGZCo4",
            "y":"8KWjGEMdQu0tnKqvZ2lQJUsbz9nLskFuIjcv9FV0kvA",
            "d":"cTw-m-LcAE7LbpHLTipjpm6Pr91liLjUubeoVACq29I",
            "use":"enc",
            "kid":"verifier-response-key",
            "alg":"ECDH-ES"
        }"""
    }
}
