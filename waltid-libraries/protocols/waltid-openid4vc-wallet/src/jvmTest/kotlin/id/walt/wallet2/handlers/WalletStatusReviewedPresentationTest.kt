package id.walt.wallet2.handlers

import com.sun.net.httpserver.HttpServer
import id.walt.credentials.CredentialParser
import id.walt.credentials.status.CredentialStatusKind
import id.walt.credentials.status.StatusTestIssuer
import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.Signer
import id.walt.crypto2.keys.toPublicJwk
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.SdJwtVcMeta
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusReviewChangedException
import id.walt.wallet2.status.WalletCredentialStatusService
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.headersOf
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.selects.select
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class WalletStatusReviewedPresentationTest {
    @Test
    @Timeout(30)
    fun callerCannotAdoptANewStatusWhileTheHolderSignatureIsPending() = runBlocking {
        val fixture = batchTestFixture(true)
        val holder = fixture.keys.first().requireCrypto2Key()
        val signing = CompletableDeferred<Unit>()
        val resumeSigning = CompletableDeferred<Unit>()
        var signatures = 0
        fixture.wallet.keyStores.single().addCrypto2Key(object : Key by holder {
            override val capabilities = holder.capabilities.copy(signer = Signer { data, algorithm ->
                val signature = requireNotNull(holder.capabilities.signer).sign(data, algorithm)
                signatures++
                signing.complete(Unit)
                resumeSigning.await()
                signature
            })
        })
        val issuer = StatusTestIssuer.create()
        val vct = "urn:test:status-reviewed"
        val publicJwk = Json.parseToJsonElement(requireNotNull(holder.capabilities.publicKeyExporter)
            .exportPublicKey().toPublicJwk(holder.spec).data.toByteArray().decodeToString()).jsonObject
        val credential = issuer.sign(buildJsonObject {
            put("iss", issuer.did); put("vct", vct)
            putJsonObject("cnf") { put("jwk", publicJwk) }
            putJsonObject("status") { putJsonObject("status_list") { put("idx", 0); put("uri", issuer.uri) } }
        }, "dc+sd-jwt") + "~"
        fixture.store.addCredential(StoredCredential("resident", CredentialParser.detectAndParse(credential).second))
        var at = Instant.fromEpochSeconds(1_800_000_000)
        var value = 0u
        val client = HttpClient(MockEngine {
            respond(issuer.list(value), headers = headersOf(HttpHeaders.ContentType, "application/statuslist+jwt"))
        })
        val service = WalletCredentialStatusService(fixture.wallet, client, now = { at }, parentScope = this)
        fixture.wallet.attachCredentialStatusService(service)
        val delivered = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/response") { exchange ->
            delivered.incrementAndGet()
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("{}".toByteArray()) }
        }
        server.start()
        try {
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val initial = service.snapshot().credentials.single().presentation
            assertEquals(CredentialStatusKind.CHECKED, initial.kind)
            val reviewed = mutableMapOf("resident" to initial.advisoryRevision)
            val request = AuthorizationRequest(
                clientId = "redirect_uri:https://verifier.example/callback",
                responseUri = "http://127.0.0.1:${server.address.port}/response",
                nonce = "status-reviewed-nonce", responseType = OpenID4VPResponseType.VP_TOKEN,
                responseMode = OpenID4VPResponseMode.DIRECT_POST,
                dcqlQuery = DcqlQuery(listOf(CredentialQuery("identity", CredentialFormat.DC_SD_JWT, meta = SdJwtVcMeta(listOf(vct))))),
            )
            val registry = TransactionDataTypeRegistry()
            val preview = assertIs<PreviewPresentationResult.Ready>(WalletPresentationHandler.previewPresentation(
                fixture.wallet, PreviewPresentationRequest(Url("https://verifier.example/request")),
                executionKey = { fixture.keys.first() }, onEvent = {}, transactionDataTypeRegistry = registry,
                resolveAuthorizationRequest = { ResolvedAuthorizationRequest.Plain(request) },
            ))
            val result = async {
                runCatching {
                    WalletPresentationHandler.submitStatusReviewedPresentation(fixture.wallet,
                        SubmitPresentationRequest(preview.handle, listOf(PresentationCredentialSelection("identity", "resident"))),
                        reviewed, registry)
                }
            }
            select<Unit> {
                signing.onAwait { }
                result.onAwait {
                    it.getOrThrow()
                    fail("Presentation completed before reaching holder signing")
                }
            }
            at += 1.seconds; value = 1u
            service.refresh(mode = CredentialStatusRefreshMode.FORCE)
            val changed = service.snapshot().credentials.single().presentation
            assertEquals(CredentialStatusKind.INVALID, changed.kind)
            assertNotEquals(initial.advisoryRevision, changed.advisoryRevision)
            reviewed["resident"] = changed.advisoryRevision
            resumeSigning.complete(Unit)
            assertIs<CredentialStatusReviewChangedException>(result.await().exceptionOrNull())
            assertEquals(1, signatures, "The test must reach and complete real holder signing")
            assertEquals(0, delivered.get(), "A status change during signing must prevent delivery")
        } finally { resumeSigning.complete(Unit); service.close(); client.close(); server.stop(0) }
    }
}
