package id.walt.wallet2.handlers

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.waltid.openid4vp.wallet.presentation.ScaAuthenticationMethods
import id.waltid.openid4vp.wallet.presentation.ScaPresentation
import io.ktor.http.Url
import io.ktor.http.parseQueryString
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class WalletBatchScaPresentationTest {
    @Test fun scaAuthenticationAndProofUseTheSelectedBatchKey() = runBlocking {
        val fixture = batchTestFixture(true)
        val vct = "https://webuildconsortium.eu/sca/sca-iban/1.0"
        val issued = WalletIssuanceHandler.receiveCredentials(fixture.wallet,
            ReceiveCredentialRequest(offerJson = batchTestOffer(), credentials = listOf(fixture.selection(2))),
            httpClient = batchTestClient(metadata = batchTestMetadata().replace("\"vct\":\"identity\"", "\"vct\":\"$vct\""),
                credential = { batchTestResponse(it.batchProofs().reversed(), vct) }))
        val credentialId = issued.credentialIds.first()
        val holder = fixture.keys[1].requireCrypto2Key()
        val transaction = buildJsonObject {
            put("type", "urn:eudi:sca:payment:1")
            putJsonArray("credential_ids") { add("payment") }
            putJsonArray("transaction_data_hashes_alg") { add("sha-256") }
            putJsonObject("payload") {
                put("transaction_id", "batch-payment"); put("currency", "EUR"); put("amount", 12.5)
                putJsonObject("payee") { put("name", "Test shop"); put("id", "test-shop") }
            }
        }.toString().encodeToByteArray().encodeToBase64Url()
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
            val request = id.walt.verifier.openid.models.authorization.AuthorizationRequest(
                clientId = "redirect_uri:https://verifier.example/callback",
                responseUri = "http://127.0.0.1:${server.address.port}/response",
                nonce = "batch-payment-nonce",
                responseType = id.walt.verifier.openid.models.openid.OpenID4VPResponseType.VP_TOKEN,
                responseMode = id.walt.verifier.openid.models.openid.OpenID4VPResponseMode.DIRECT_POST,
                transactionData = listOf(transaction),
                dcqlQuery = id.walt.dcql.models.DcqlQuery(listOf(id.walt.dcql.models.CredentialQuery(
                    id = "payment", format = id.walt.dcql.models.CredentialFormat.DC_SD_JWT,
                    meta = id.walt.dcql.models.meta.SdJwtVcMeta(listOf(vct)),
                ))),
            )
            val registry = TransactionDataTypeRegistry("urn:eudi:sca:payment:1")
            val preview = assertIs<PreviewPresentationResult.Ready>(WalletPresentationHandler.previewPresentation(
                wallet = fixture.wallet,
                request = PreviewPresentationRequest(Url("https://verifier.example/request")),
                executionKey = { fixture.keys.first() }, onEvent = {}, transactionDataTypeRegistry = registry,
                resolveAuthorizationRequest = { id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest.Plain(request) },
            ))
            val factors = ScaAuthenticationMethods.KnowledgeAndPossession(
                ScaAuthenticationMethods.Knowledge.PIN_6_OR_MORE_DIGITS,
                ScaAuthenticationMethods.Possession.KEY_IN_LOCAL_NATIVE_WSCD,
            )
            val authenticated = mutableListOf<ScaPresentation>()
            val result = WalletPresentationHandler.submitPresentation(fixture.wallet,
                SubmitPresentationRequest(preview.handle, listOf(PresentationCredentialSelection("payment", credentialId))),
                transactionDataTypeRegistry = registry,
                scaAuthorizer = WalletScaPresentationAuthorizer { key, presentation ->
                    assertSame(holder, key)
                    authenticated += presentation
                    factors // Simulated factors; this test does not exercise device authentication.
                })
            assertEquals(true, result.transmissionSuccess)
            val proof = authenticated.single()
            assertEquals(holder.id.value, proof.holderKeyId)
            assertEquals(credentialId, proof.credentialId)
            assertEquals(listOf(transaction), proof.transactionData)
            val vp = Json.parseToJsonElement(assertNotNull(parseQueryString(assertNotNull(delivered.get()))["vp_token"]))
                .jsonObject.getValue("payment").jsonArray.single().jsonPrimitive.content
            val verified = CompactJws.verify(vp.substringAfterLast('~'), holder, JwsAlgorithm.ES256)
            val payload = Json.parseToJsonElement(verified.payload.decodeToString()).jsonObject
            assertEquals(proof.proofId, payload.getValue("jti").jsonPrimitive.content)
            assertEquals(Json.parseToJsonElement("""[{"knowledge":"pin_6_or_more_digits"},{"possession":"key_in_local_native_wscd"}]"""), payload["amr"])
        } finally { server.stop(0) }
    }

}
