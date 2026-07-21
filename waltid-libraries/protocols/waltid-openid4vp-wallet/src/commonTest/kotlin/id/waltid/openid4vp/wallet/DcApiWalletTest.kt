package id.waltid.openid4vp.wallet

import id.walt.cose.coseCompliantCbor
import id.walt.mdoc.objects.handover.OpenID4VPDCAPIHandoverInfo
import id.walt.mdoc.objects.sha256
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.waltid.openid4vp.wallet.presentation.MdocPresenter
import id.waltid.openid4vp.wallet.request.AuthorizationRequestResolver
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
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
    fun `signed request requires exact expected origin`() {
        val request = authorizationRequest(
            clientId = "x509_san_dns:verifier.example",
            expectedOrigins = listOf("https://verifier.example"),
        )

        DcApiWallet.validateAuthorizationRequest(request, signed = true, origin = "https://verifier.example")
        assertFailsWith<DcApiOriginMismatchException> {
            DcApiWallet.validateAuthorizationRequest(request, signed = true, origin = "https://evil.example")
        }
        assertFailsWith<DcApiOriginMismatchException> {
            DcApiWallet.validateAuthorizationRequest(request, signed = true, origin = "https://verifier.example/")
        }
    }

    @Test
    fun `signed request requires client id and expected origins`() {
        assertFailsWith<IllegalArgumentException> {
            DcApiWallet.validateAuthorizationRequest(
                authorizationRequest(clientId = null, expectedOrigins = listOf("https://verifier.example")),
                signed = true,
                origin = "https://verifier.example",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            DcApiWallet.validateAuthorizationRequest(
                authorizationRequest(clientId = "did:example:verifier", expectedOrigins = emptyList()),
                signed = true,
                origin = "https://verifier.example",
            )
        }
    }

    @Test
    fun `signed protocol obtains resolver client id from its request object`() = runTest {
        val clientId = "x509_hash:abc123"
        val requestObject = unsignedJwt(
            header = """{"alg":"none","typ":"oauth-authz-req+jwt"}""",
            payload = """
                {
                  "response_type":"vp_token",
                  "client_id":"$clientId",
                  "response_mode":"dc_api",
                  "nonce":"nonce-123",
                  "aud":"https://self-issued.me/v2",
                  "expected_origins":["https://verifier.example"],
                  "dcql_query":{"credentials":[{
                    "id":"pid",
                    "format":"mso_mdoc",
                    "meta":{"doctype_value":"eu.europa.ec.eudi.pid.1"}
                  }]}
                }
            """.trimIndent(),
        )

        assertFailsWith<AuthorizationRequestResolver.UnsignedAuthorizationRequestNotAllowedException> {
            DcApiWallet.resolveRequest(
                protocol = "openid4vp-v1-signed",
                data = JsonObject(mapOf("request" to JsonPrimitive(requestObject))),
                origin = "https://verifier.example",
            )
        }
    }

    @Test
    fun `only dc api response modes are accepted`() {
        assertFailsWith<IllegalArgumentException> {
            DcApiWallet.validateAuthorizationRequest(
                authorizationRequest(responseMode = OpenID4VPResponseMode.DIRECT_POST),
                signed = false,
                origin = "https://verifier.example",
            )
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
    fun `dc api response echoes authorization request state`() = runTest {
        val authorizationRequest = authorizationRequest(state = "state-123")
        val response = DcApiWallet.buildResponse(
            request = ResolvedDcApiRequest(
                protocol = DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
                origin = "https://verifier.example",
                authorizationRequest = authorizationRequest,
            ),
            vpToken = "{}",
        )

        assertEquals("state-123", response.data["state"]?.jsonPrimitive?.content)
    }

    @Test
    fun `dc api jwt returns only a compact encrypted response`() = runTest {
        val authorizationRequest = authorizationRequest(
            responseMode = OpenID4VPResponseMode.DC_API_JWT,
            clientMetadata = ClientMetadata(
                jwks = ClientMetadata.Jwks(keys = listOf(testEncryptionJwk)),
                encryptedResponseEncValuesSupported = listOf("A128GCM"),
            ),
        )
        val response = DcApiWallet.buildResponse(
            request = ResolvedDcApiRequest(
                protocol = DcApiRequestProtocol.OPENID4VP_V1_UNSIGNED,
                origin = "https://verifier.example",
                authorizationRequest = authorizationRequest,
            ),
            vpToken = "{}",
        )

        assertEquals(setOf("response"), response.data.keys)
        assertEquals(5, response.data.getValue("response").jsonPrimitive.content.split('.').size)
    }

    @Test
    fun `protocol error response has only the error member`() {
        val response = DcApiWallet.buildErrorResponse(
            DcApiRequestProtocol.OPENID4VP_V1_SIGNED,
            WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST,
        )

        assertEquals("openid4vp-v1-signed", response.protocol)
        assertEquals(setOf("error"), response.data.keys)
        assertEquals("invalid_request", response.data["error"]?.jsonPrimitive?.content)
    }

    @Test
    fun `platform origin rejects complex or insecure web values and accepts Android app origins`() {
        assertEquals(
            "android:apk-key-hash:abc123",
            DcApiWallet.validatePlatformOrigin("android:apk-key-hash:abc123"),
        )
        assertFailsWith<IllegalArgumentException> {
            DcApiWallet.validatePlatformOrigin("https://verifier.example/path")
        }
        assertFailsWith<IllegalArgumentException> {
            DcApiWallet.validatePlatformOrigin("http://verifier.example")
        }
        assertEquals("http://localhost:8080", DcApiWallet.validatePlatformOrigin("http://localhost:8080"))
    }

    @Test
    fun `multisigned request fails closed with an explicit capability reason`() = runTest {
        assertFailsWith<UnsupportedDcApiMultisignedRequestException> {
            DcApiWallet.resolveRequest(
                protocol = "openid4vp-v1-multisigned",
                data = JsonObject(emptyMap()),
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
        clientId: String? = null,
        expectedOrigins: List<String>? = null,
        state: String? = null,
        clientMetadata: ClientMetadata? = null,
    ): AuthorizationRequest = json.decodeFromJsonElement(
        AuthorizationRequest.serializer(),
        unsignedRequestData().toMutableMap().apply {
            this["response_mode"] = Json.parseToJsonElement("\"${responseMode.serialized()}\"")
            clientId?.let { this["client_id"] = Json.parseToJsonElement("\"$it\"") }
            expectedOrigins?.let {
                this["expected_origins"] = Json.parseToJsonElement(
                    it.joinToString(prefix = "[", postfix = "]") { value -> "\"$value\"" },
                )
            }
            state?.let { this["state"] = JsonPrimitive(it) }
            clientMetadata?.let {
                this["client_metadata"] = json.encodeToJsonElement(ClientMetadata.serializer(), it)
            }
        }.let(::JsonObject),
    )

    private val testEncryptionJwk: JsonObject = json.parseToJsonElement(
        """
        {
          "kty": "EC",
          "crv": "P-256",
          "x": "y4ajD4aIXGiLGqiF81nN5HvBFvBEvrZcgFsp5VIJO30",
          "y": "jyrZRfxKz113LQNg2x5f7Nu4fwW5Ov5gCzhPaTZuTCg",
          "use": "enc",
          "alg": "ECDH-ES",
          "kid": "dc-api-test-key"
        }
        """.trimIndent(),
    ).jsonObject

    private fun OpenID4VPResponseMode.serialized(): String =
        Json.encodeToString(OpenID4VPResponseMode.serializer(), this).trim('"')

    private fun unsignedJwt(header: String, payload: String): String =
        "${header.encodeToByteArray().encodeToBase64Url()}.${payload.encodeToByteArray().encodeToBase64Url()}."
}
