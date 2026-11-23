package id.walt.wallet2.handlers

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import id.walt.credentials.CredentialParser
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.StoredCredential
import id.waltid.openid4vp.wallet.presentation.ScaAuthenticationMethods
import id.waltid.openid4vp.wallet.presentation.ScaPresentation
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.http.Url
import io.ktor.http.parseQueryString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class WalletBatchScaPresentationTest {
    private val vct = "https://webuildconsortium.eu/sca/sca-iban/1.0"
    private val registry = TransactionDataTypeRegistry("urn:eudi:sca:payment:1")
    // Simulated factors; these tests do not exercise device authentication.
    private val factors = ScaAuthenticationMethods.KnowledgeAndPossession(
        ScaAuthenticationMethods.Knowledge.PIN_6_OR_MORE_DIGITS,
        ScaAuthenticationMethods.Possession.KEY_IN_LOCAL_NATIVE_WSCD,
    )

    @Test fun scaAuthenticationAndProofUseTheSelectedBatchKey() = runBlocking {
        val fixture = batchTestFixture(true)
        val issued = WalletIssuanceHandler.receiveCredentials(fixture.wallet,
            ReceiveCredentialRequest(offerJson = batchTestOffer(), credentials = listOf(fixture.selection(2))),
            httpClient = batchTestClient(metadata = batchTestMetadata().replace("\"vct\":\"identity\"", "\"vct\":\"$vct\""),
                credential = { batchTestResponse(it.batchProofs().reversed(), vct) }))
        val credentialId = issued.credentialIds.first()
        val credential = assertNotNull(fixture.store.getCredential(credentialId))
        val holder = fixture.keys[1].requireCrypto2Key()
        val delivered = AtomicReference<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/response") { exchange ->
            delivered.set(exchange.requestBody.bufferedReader().readText())
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        server.start()
        try {
            val request = paymentRequest("http://127.0.0.1:${server.address.port}/response")
            suspend fun build(authorizer: WalletScaPresentationAuthorizer? = null) =
                buildPaymentVpToken(fixture, request, credentialId, fixture.keys.first().keyId, authorizer)

            assertFailsWith<IllegalArgumentException> { build() }
            val isolatedProofs = mutableListOf<ScaPresentation>()
            val isolated = build(WalletScaPresentationAuthorizer { key, proof ->
                assertSame(holder, key)
                assertEquals(credentialId, proof.credentialId)
                isolatedProofs += proof
                factors
            })
            assertPaymentProof(isolated.vpToken, credential, request, isolatedProofs.single())
            assertFailsWith<IllegalStateException> { build(WalletScaPresentationAuthorizer { _, _ -> error("denied") }) }
            assertFailsWith<CancellationException> {
                build(WalletScaPresentationAuthorizer { _, _ -> throw CancellationException("cancelled") })
            }

            val preview = assertIs<PreviewPresentationResult.Ready>(WalletPresentationHandler.previewPresentation(
                wallet = fixture.wallet,
                request = PreviewPresentationRequest(Url("https://verifier.example/request")),
                executionKey = { fixture.keys.first() }, onEvent = {}, transactionDataTypeRegistry = registry,
                resolveAuthorizationRequest = { ResolvedAuthorizationRequest.Plain(request) },
            ))
            val authenticated = mutableListOf<ScaPresentation>()
            val result = WalletPresentationHandler.submitPresentation(fixture.wallet,
                SubmitPresentationRequest(preview.handle, listOf(PresentationCredentialSelection("payment", credentialId))),
                transactionDataTypeRegistry = registry,
                scaAuthorizer = WalletScaPresentationAuthorizer { key, presentation ->
                    assertSame(holder, key)
                    authenticated += presentation
                    factors
                })
            assertEquals(true, result.transmissionSuccess)
            val proof = authenticated.single()
            assertEquals(holder.id.value, proof.holderKeyId)
            assertEquals(credentialId, proof.credentialId)
            assertEquals(request.transactionData, proof.transactionData)
            val vpToken = assertNotNull(parseQueryString(assertNotNull(delivered.get()))["vp_token"])
            assertPaymentProof(vpToken, credential, request, proof)
        } finally { server.stop(0) }
    }

    @Test fun scaAuthenticationAndProofUseTheRequestKeyWithoutAStoredBinding() = runBlocking {
        val fixture = batchTestFixture(true)
        val key = fixture.keys[1]
        val requestHolder = key.requireCrypto2Key()
        val raw = batchTestCredential(key)
        val stored = StoredCredential("unbound-payment", CredentialParser.detectAndParse(raw).second)
        fixture.store.addCredential(stored)
        assertNull(assertNotNull(fixture.store.getCredential(stored.id)).holderKeyBinding)
        val request = paymentRequest(vct = "identity")
        val proofs = mutableListOf<ScaPresentation>()
        val result = buildPaymentVpToken(fixture, request, stored.id, key.keyId, WalletScaPresentationAuthorizer { holder, proof ->
            assertSame(requestHolder, holder)
            assertEquals(requestHolder.id.value, proof.holderKeyId)
            assertEquals(stored.id, proof.credentialId)
            proofs += proof
            factors
        })
        assertPaymentProof(result.vpToken, stored, request, proofs.single())
    }

    private fun paymentRequest(
        responseUri: String = "https://verifier.example/callback",
        vct: String = this.vct,
    ): AuthorizationRequest {
        val transaction = buildJsonObject {
            put("type", "urn:eudi:sca:payment:1")
            putJsonArray("credential_ids") { add("payment") }
            putJsonArray("transaction_data_hashes_alg") { add("sha-256") }
            putJsonObject("payload") {
                put("transaction_id", "batch-payment")
                put("currency", "EUR")
                put("amount", 12.5)
                putJsonObject("payee") {
                    put("name", "Test shop")
                    put("id", "test-shop")
                }
            }
        }.toString().encodeToByteArray().encodeToBase64Url()
        return AuthorizationRequest(
            clientId = "redirect_uri:https://verifier.example/callback",
            responseUri = responseUri,
            nonce = "batch-payment-nonce",
            responseType = OpenID4VPResponseType.VP_TOKEN,
            responseMode = OpenID4VPResponseMode.DIRECT_POST,
            transactionData = listOf(transaction),
            dcqlQuery = DcqlQuery(listOf(CredentialQuery(
                id = "payment", format = CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf(vct)),
            ))),
        )
    }

    private suspend fun buildPaymentVpToken(
        fixture: BatchTestFixture,
        request: AuthorizationRequest,
        credentialId: String,
        keyId: String,
        authorizer: WalletScaPresentationAuthorizer?,
    ) = WalletPresentationHandler.buildVpToken(
        wallet = fixture.wallet,
        request = BuildVpTokenRequest(
            requestUrl = Url("https://verifier.example/request"),
            selectedCredentialIds = mapOf("payment" to listOf(credentialId)),
            keyId = keyId,
        ),
        scaAuthorizer = authorizer,
        transactionDataTypeRegistry = registry,
        resolveAuthorizationRequest = { ResolvedAuthorizationRequest.Plain(request) },
    )

    private suspend fun assertPaymentProof(
        vpToken: String,
        stored: StoredCredential,
        request: AuthorizationRequest,
        proof: ScaPresentation,
    ) {
        val vp = Json.parseToJsonElement(vpToken).jsonObject.getValue("payment").jsonArray.single().jsonPrimitive.content
        val holder = assertNotNull(stored.credential.getHolderCrypto2Key())
        val verified = CompactJws.verify(vp.substringAfterLast('~'), holder, JwsAlgorithm.ES256)
        val payload = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
        assertEquals(proof.proofId, payload.getValue("jti").jsonPrimitive.content)
        assertEquals(request.nonce, payload.getValue("nonce").jsonPrimitive.content)
        assertEquals(request.clientId, payload.getValue("aud").jsonPrimitive.content)
        assertEquals(Json.parseToJsonElement("""[{"knowledge":"pin_6_or_more_digits"},{"possession":"key_in_local_native_wscd"}]"""), payload["amr"])
        val encodedTransaction = assertNotNull(request.transactionData).single()
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(encodedTransaction.encodeToByteArray()).encodeToBase64Url()
        assertEquals(JsonArray(listOf(JsonPrimitive(expectedHash))), payload["transaction_data_hashes"])
        assertEquals(JsonPrimitive("sha-256"), payload["transaction_data_hashes_alg"])
    }
}
