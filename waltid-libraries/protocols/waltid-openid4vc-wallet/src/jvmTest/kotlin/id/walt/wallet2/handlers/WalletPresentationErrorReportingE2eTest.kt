package id.walt.wallet2.handlers

import com.sun.net.httpserver.HttpServer
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.NoMeta
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.waltid.openid4vp.wallet.UnsafePresentationErrorResponseException
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class WalletPresentationErrorReportingE2eTest {
    @Test
    fun unsafeErrorsStayLocalThroughBothPreviewsAndDirectPresentation() = runBlocking {
        val posts = CopyOnWriteArrayList<String>()
        val server = responseServer(posts)
        try {
            val request = AuthorizationRequest(
                clientId = "verifier",
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                responseUri = "http://127.0.0.1:${server.address.port}/response",
                nonce = null,
            )
            val resolved = ResolvedAuthorizationRequest.Plain(request)
            val wallet = Wallet(
                "unsafe-reporting",
                staticKey = JWKKey.generate(KeyType.secp256r1),
                credentialStores = listOf(InMemoryCredentialStore()),
            )
            val previewRequest = PreviewPresentationRequest(request.toHttpUrl())
            val failures = listOf(
                assertFailsWith<UnsafePresentationErrorResponseException> {
                    WalletPresentationHandler.previewPresentation(
                        wallet, previewRequest,
                        executionKey = { error("Invalid request must not resolve the signing key") },
                        onEvent = {}, transactionDataTypeRegistry = TransactionDataTypeRegistry(),
                        resolveAuthorizationRequest = { resolved },
                    )
                },
                assertFailsWith<UnsafePresentationErrorResponseException> {
                    WalletPresentationHandler.previewPresentationStateless(
                        wallet, previewRequest, transactionDataTypeRegistry = TransactionDataTypeRegistry(),
                        resolveAuthorizationRequest = { resolved },
                    )
                },
            )
            for (failure in failures) {
                assertEquals("invalid_request", failure.error.code.code)
                assertEquals("Authorization Request nonce is required", failure.error.message)
                assertNotNull(failure.responseSafetyFailure.message)
            }
            val unsupported = request.copy(
                nonce = "nonce", responseType = OpenID4VPResponseType.CODE,
                dcqlQuery = DcqlQuery(credentials = listOf(CredentialQuery("pid", CredentialFormat.JWT_VC_JSON, meta = NoMeta))),
            )
            val holderKey = CryptoRuntime(defaultSoftwareKeyProviders()).generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("validation-holder"), spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY),
                ),
            )
            val directFailure = assertFailsWith<UnsafePresentationErrorResponseException> {
                WalletPresentFunctionality2.walletPresentHandling(
                    presentationRequestUrl = unsupported.toHttpUrl(),
                    holderKey = holderKey, holderDid = null,
                    holderPoliciesToRun = null, runPolicies = null,
                    resolvedAuthorizationRequest = ResolvedAuthorizationRequest.Plain(unsupported),
                    selectCredentialsForQuery = { error("Invalid request must not select credentials") },
                    transactionDataTypeRegistry = TransactionDataTypeRegistry(),
                )
            }
            assertEquals("unsupported_response_type", directFailure.error.code.code)
            assertEquals("Unsupported response_type 'code'", directFailure.error.message)
            val unavailable = request.copy(nonce = "nonce", dcqlQuery = DcqlQuery(credentials = listOf(
                CredentialQuery("pid", CredentialFormat.JWT_VC_JSON, meta = NoMeta),
            )))
            val availabilityFailure = assertFailsWith<UnsafePresentationErrorResponseException> {
                WalletPresentationHandler.previewPresentationStateless(
                    wallet, PreviewPresentationRequest(unavailable.toHttpUrl()),
                    transactionDataTypeRegistry = TransactionDataTypeRegistry(),
                    resolveAuthorizationRequest = { ResolvedAuthorizationRequest.Plain(unavailable) },
                )
            }
            assertEquals("access_denied", availabilityFailure.error.code.code)
            assertTrue(posts.isEmpty(), "Unsafe error reporting must never contact the response endpoint")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun unsignedBoundAndAuthenticatedRequestsReportOriginalErrorsOnlyAfterRejection() = runBlocking {
        val posts = CopyOnWriteArrayList<String>()
        val server = responseServer(posts)
        try {
            val responseUri = "http://127.0.0.1:${server.address.port}/response"
            val verifierKey = JWKKey.generate(KeyType.secp256r1)
            val trust = ClientIdTrustConfiguration(preRegisteredClients = mapOf(
                "verifier" to ClientMetadata(jwks = ClientMetadata.Jwks(listOf(verifierKey.getPublicKey().exportJWKObject()))),
            ))
            val wallet = Wallet("safe-reporting")
            val cases = listOf(
                buildJsonObject { put("response_type", "vp_token") } to "invalid_request",
                buildJsonObject { put("response_type", "vp_token"); put("nonce", "nonce") } to "invalid_request",
                buildJsonObject { put("response_type", "vp_token"); put("nonce", "nonce"); put("scope", "unknown") } to "invalid_scope",
                buildJsonObject { put("response_type", "code"); put("nonce", "nonce") } to "unsupported_response_type",
            )
            for (signed in listOf(false, true)) {
                for ((parameters, expectedCode) in cases) {
                    val clientId = if (signed) "verifier" else "redirect_uri:$responseUri"
                    val payload = buildJsonObject {
                        put("client_id", clientId)
                        put("response_mode", "direct_post")
                        put("response_uri", responseUri)
                        put("state", "session-$signed-$expectedCode")
                        if (signed) put("aud", "https://self-issued.me/v2")
                        parameters.forEach { (key, value) -> put(key, value) }
                    }
                    val url = if (signed) {
                        val jwt = verifierKey.signJws(payload.toString().encodeToByteArray(), mapOf(
                            "typ" to JsonPrimitive("oauth-authz-req+jwt"),
                        ))
                        URLBuilder("openid4vp://authorize").apply {
                            this.parameters.append("client_id", clientId)
                            this.parameters.append("request", jwt)
                        }.build()
                    } else {
                        Json.decodeFromString<AuthorizationRequest>(payload.toString()).toHttpUrl()
                    }
                    val before = posts.size
                    val preview = WalletPresentationHandler.previewPresentationWithTrust(
                        wallet, PreviewPresentationRequest(url),
                        transactionDataTypeRegistry = TransactionDataTypeRegistry(), clientIdTrustConfiguration = trust,
                    )
                    val invalid = assertIs<PreviewPresentationResult.Invalid>(preview)
                    if (signed) {
                        assertIs<ResolvedAuthorizationRequest.AuthenticatedRequestObject>(invalid.resolvedAuthorizationRequest)
                    } else {
                        assertIs<ResolvedAuthorizationRequest.Plain>(invalid.resolvedAuthorizationRequest)
                    }
                    assertEquals(expectedCode, invalid.error.code.code)
                    assertEquals(before, posts.size, "Preview must not report errors before consent")
                    val result = WalletPresentationHandler.rejectPresentation(wallet, RejectPresentationRequest(invalid.handle))
                    assertEquals(true, result.transmissionSuccess)
                    assertEquals(before + 1, posts.size)
                    val form = parseQueryString(posts.last())
                    assertEquals(expectedCode, form["error"])
                    assertEquals("session-$signed-$expectedCode", form["state"])
                    assertEquals(null, form["error_description"])
                    assertEquals(null, form["vp_token"])
                }
            }
        } finally {
            server.stop(0)
        }
    }

    private fun responseServer(posts: MutableList<String>): HttpServer =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/response") { exchange ->
                exchange.use {
                    posts += it.requestBody.readAllBytes().decodeToString()
                    val body = "{}".encodeToByteArray()
                    it.responseHeaders.add("Content-Type", "application/json")
                    it.sendResponseHeaders(200, body.size.toLong())
                    it.responseBody.write(body)
                }
            }
            start()
        }
}
