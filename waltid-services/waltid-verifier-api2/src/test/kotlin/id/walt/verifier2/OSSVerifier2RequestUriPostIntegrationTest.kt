@file:OptIn(ExperimentalSerializationApi::class)

package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.testing.E2ETest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJwe
import id.walt.crypto2.jose.JweContentEncryption
import id.walt.crypto2.jose.exportPublicJwkObject
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.JwtVcJsonMeta
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier2.data.CrossDeviceFlowSetup
import id.walt.verifier2.data.DcApiAnnexDFlowSetup
import id.walt.verifier2.data.GeneralFlowConfig
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.handlers.sessioncreation.VerificationSessionCreationResponse
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.server.application.Application
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*

class OSSVerifier2RequestUriPostIntegrationTest {

    @Test
    fun `request URI POST uses configured OSS service key and binds wallet nonce`() {
        val host = "127.0.0.1"
        val port = freePort()
        val configuredKey = KeyManager.resolveSerializedKeyBlocking(TEST_SIGNING_KEY)

        E2ETest(host, port, true).testBlock(
            features = listOf(OSSVerifier2FeatureCatalog),
            preload = {
                ConfigManager.preloadConfig(
                    "verifier-service",
                    OSSVerifier2ServiceConfig(
                        clientId = "configured-oss-verifier",
                        clientMetadata = ClientMetadata(clientName = "Configured OSS verifier"),
                        urlPrefix = "http://$host:$port/verification-session",
                        urlHost = "openid4vp://authorize",
                        key = Json.parseToJsonElement(TEST_SIGNING_KEY).jsonObject,
                    )
                )
            },
            init = {},
            module = Application::verifierModule,
        ) {
            val http = testHttpClient()
            assertEquals(HttpStatusCode.NotFound, http.get("/verification-session/missing-session/request").status)
            for (encrypted in listOf(false, true)) {
                val setup: VerificationSessionSetup = CrossDeviceFlowSetup(
                    core = GeneralFlowConfig(
                        dcqlQuery = DcqlQuery(
                            credentials = listOf(
                                CredentialQuery(
                                    id = "credential",
                                    format = CredentialFormat.JWT_VC_JSON,
                                    meta = JwtVcJsonMeta(typeValues = listOf(listOf("VerifiableCredential"))),
                                )
                            )
                        ),
                        signedRequest = !encrypted,
                        encryptedRequest = encrypted,
                    )
                )
                val created = http.post("/verification-session/create") {
                    setBody(setup)
                }.body<VerificationSessionCreationResponse>()
                val requestUri = Url(created.bootstrapAuthorizationRequestUrl.toString()).parameters["request_uri"]
                    ?: error("Missing request_uri in bootstrap authorization request")

                val walletKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(GenerateSoftwareKeyRequest(
                    id = KeyId("wallet-key"), spec = KeySpec.Ec(EcCurve.P256), usages = setOf(KeyUsage.KEY_AGREEMENT),
                ))
                val metadata = buildJsonObject {
                    put("jwks", buildJsonObject { put("keys", buildJsonArray { add(walletKey.exportPublicJwkObject()) }) })
                    put("request_object_encryption_alg_values_supported", buildJsonArray { add("ECDH-ES") })
                    put("request_object_encryption_enc_values_supported", buildJsonArray { add("A128GCM") })
                }
                if (encrypted) {
                    val dcApiSetup: VerificationSessionSetup = DcApiAnnexDFlowSetup(
                        core = setup.core, expectedOrigins = listOf("https://verifier.example"),
                    )
                    assertEquals(HttpStatusCode.BadRequest, http.post("/verification-session/create") {
                        setBody(dcApiSetup)
                    }.status)
                    assertEquals(created.bootstrapAuthorizationRequestUrl, created.fullAuthorizationRequestUrl)
                    assertEquals(HttpStatusCode.BadRequest, http.get(requestUri).status)
                    val invalidPointMetadata = JsonObject(metadata + ("jwks" to buildJsonObject {
                        put("keys", buildJsonArray {
                            add(buildJsonObject {
                                walletKey.exportPublicJwkObject().forEach { (name, value) -> put(name, value) }
                                put("x", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                                put("y", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                            })
                        })
                    }))
                    for (metadata in listOf(null, "{}", "not-json", "{\"jwks\":{\"keys\":[]}}", invalidPointMetadata.toString())) {
                        val failure = http.submitForm(url = requestUri, formParameters = Parameters.build {
                            append("wallet_nonce", "negative-nonce")
                            metadata?.let { append("wallet_metadata", it) }
                        })
                        assertEquals(HttpStatusCode.BadRequest, failure.status)
                    }
                }
                val expectedNonce = "configured-service-wallet-nonce"
                val response = http.submitForm(
                    url = requestUri,
                    formParameters = Parameters.build {
                        append("wallet_nonce", expectedNonce)
                        if (encrypted) append("wallet_metadata", metadata.toString())
                    },
                )

                test("configured service key signs the nonce-bound request (encrypted=$encrypted)") {
                    assertEquals(HttpStatusCode.OK, response.status)
                    assertEquals(
                        ContentType.parse("application/oauth-authz-req+jwt"),
                        response.contentType()?.withoutParameters(),
                    )
                    val body = response.body<String>()
                    val requestObject = if (encrypted) CompactJwe.decrypt(body, walletKey, setOf(JweContentEncryption.A128GCM))
                        .plaintext.decodeToString() else body
                    configuredKey.getPublicKey().verifyJws(requestObject).getOrThrow()
                    val decoded = requestObject.decodeJws()
                    assertEquals("oauth-authz-req+jwt", decoded.header["typ"]?.jsonPrimitive?.content)
                    assertEquals(configuredKey.getKeyId(), decoded.header["kid"]?.jsonPrimitive?.content)
                    assertEquals("ES256", decoded.header["alg"]?.jsonPrimitive?.content)
                    assertEquals(expectedNonce, decoded.payload["wallet_nonce"]?.jsonPrimitive?.content)
                    assertEquals("configured-oss-verifier", decoded.payload["client_id"]?.jsonPrimitive?.content)
                    assertNotNull(decoded.payload["dcql_query"])
                }
            }
        }
    }

    private companion object {
        const val TEST_SIGNING_KEY =
            """{"type":"jwk","jwk":{"kty":"EC","d":"AEb4k1BeTR9xt2NxYZggdzkFLLUkhyyWvyUOq3qSiwA","crv":"P-256","kid":"_nd-T2YRYLSmuKkJZlRI641zrCIJLTpiHeqMwXuvdug","x":"G_TgBc0BkmMipiQ_6gkamIn3mmp7hcTrZuyrLTmknP0","y":"VkRMZdXYXSMff5AJLrnHiN0x5MV6u_8vrAcytGUe4z4"}}"""
    }
}
