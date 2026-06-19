package id.walt.issuer2.notifications

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
import id.walt.issuer2.models.CredentialOfferCreateResponse
import id.walt.issuer2.models.CredentialOfferRuntimeOverrides
import id.walt.issuer2.controller.openapi.Issuer2RequestExamples
import id.walt.issuer2.testsupport.Issuer2TestNotificationServer
import id.walt.issuer2.testsupport.apiClient
import id.walt.issuer2.testsupport.assertBearerAccessToken
import id.walt.issuer2.testsupport.assertJwtVcJsonCredentialPayload
import id.walt.issuer2.testsupport.clearIssuer2TestEnvironment
import id.walt.issuer2.testsupport.createCredentialOffer
import id.walt.issuer2.testsupport.credentialRequest
import id.walt.issuer2.testsupport.installIssuer2WithConfigFiles
import id.walt.issuer2.testsupport.issuer2TestJson
import id.walt.issuer2.testsupport.resolveOffer
import id.walt.ktornotifications.core.KtorSessionUpdate
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.proof.JwtProofBuilder
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration.Companion.milliseconds

class IssuanceNotificationRouteTest {

    @AfterEach
    fun clearConfig() {
        clearIssuer2TestEnvironment()
    }

    @Test
    fun webhookReceiverGetsEnterpriseNotificationEnvelopeForPreAuthorizedEvents() = testApplication {
        val notificationServer = Issuer2TestNotificationServer()
        notificationServer.startServer()

        try {
            installIssuer2WithConfigFiles()
            val client = apiClient()
            val createdOffer = client.createCredentialOffer(
                Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE.copy(
                    runtimeOverrides = CredentialOfferRuntimeOverrides(
                        notifications = IssuanceNotifications(
                            webhook = IssuanceNotifications.WebhookNotification(
                                url = notificationServer.webhookUrl(),
                            ),
                        ),
                    ),
                )
            )

            val credentialPayload = client.completePreAuthorizedJwtIssuance(createdOffer)
            assertJwtVcJsonCredentialPayload(credentialPayload)

            val expectedEvents = listOf(
                IssuanceSessionEvent.resolved_credential_offer,
                IssuanceSessionEvent.requested_token,
                IssuanceSessionEvent.jwt_issue,
                IssuanceSessionEvent.issuance_status,
            )
            expectedEvents.forEach { notificationServer.awaitEvent(createdOffer.offerId, it) }

            val receivedUpdates = notificationServer.getReceivedUpdates()
                .filter { it.target == createdOffer.offerId }
            assertEquals(expectedEvents.map { it.toString() }, receivedUpdates.map { it.event })

            val requestedToken = assertNotNull(
                receivedUpdates.first { it.event == IssuanceSessionEvent.requested_token.toString() }
                    .session
            )
            assertEquals(createdOffer.offerId, requestedToken["sessionId"]?.jsonPrimitive?.contentOrNull)
            assertEquals("UniversityDegree_jwt_vc_json", requestedToken["credentialConfigurationId"]?.jsonPrimitive?.contentOrNull)

            val jwtIssue = receivedUpdates.first { it.event == IssuanceSessionEvent.jwt_issue.toString() }
            assertEquals(createdOffer.offerId, jwtIssue.session["sessionId"]?.jsonPrimitive?.contentOrNull)

            val status = receivedUpdates.first { it.event == IssuanceSessionEvent.issuance_status.toString() }
            assertEquals("SUCCESSFUL", status.session["status"]?.jsonPrimitive?.contentOrNull)
            assertEquals("true", status.session["isClosed"]?.jsonPrimitive?.contentOrNull)
        } finally {
            notificationServer.stopServer()
        }
    }

    @Test
    fun sseRouteStreamsEnterpriseNotificationEnvelope() = testApplication {
        installIssuer2WithConfigFiles()
        val client = apiClient()
        val createdOffer = client.createCredentialOffer(Issuer2RequestExamples.PROFILE_PRE_AUTHORIZED_OFFER_BY_REFERENCE)

        var resolvedOfferCredentialIssuer: String? = null
        val update = client.readFirstSseUpdate(createdOffer.offerId) {
            resolvedOfferCredentialIssuer = createdOffer.resolveOffer(client).credentialIssuer
        }

        assertEquals(createdOffer.offerId, update.target)
        assertEquals(IssuanceSessionEvent.resolved_credential_offer.toString(), update.event)
        assertEquals(createdOffer.offerId, update.session["sessionId"]?.jsonPrimitive?.contentOrNull)
        assertEquals("UniversityDegree_jwt_vc_json", update.session["credentialConfigurationId"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            resolvedOfferCredentialIssuer,
            update.session["credentialOffer"]?.jsonObject?.get("credential_issuer")?.jsonPrimitive?.contentOrNull,
        )
    }

    private suspend fun HttpClient.completePreAuthorizedJwtIssuance(
        createdOffer: CredentialOfferCreateResponse,
    ): JsonObject {
        val resolvedOffer = createdOffer.resolveOffer(this)
        val preAuthorizedCode = assertNotNull(resolvedOffer.grants?.preAuthorizedCode?.preAuthorizedCode)
        val tokenResponse = TokenRequestBuilder(walletClientConfig, this).exchangePreAuthorizedCode(
            tokenEndpoint = "/openid4vci/token",
            preAuthorizedCode = preAuthorizedCode,
            txCode = null,
        )
        assertBearerAccessToken(tokenResponse)

        val issuerMetadata = get("/.well-known/openid-credential-issuer/openid4vci").also {
            assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        }.body<CredentialIssuerMetadata>()
        val nonceResponse = post("/openid4vci/nonce").also {
            assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
        }.body<JsonObject>()
        val nonce = assertNotNull(nonceResponse["c_nonce"]?.jsonPrimitive?.contentOrNull)
        val proofKey = JWKKey.generate(KeyType.secp256r1)
        val holderDid = DidJwkRegistrar()
            .registerByKey(proofKey, DidJwkCreateOptions(KeyType.secp256r1))
            .did
        val proofs = JwtProofBuilder().buildJwtProof(
            key = proofKey,
            audience = issuerMetadata.credentialIssuer,
            nonce = nonce,
            keyId = "$holderDid#0",
        )

        val credentialResponse = post("/openid4vci/credential") {
            bearerAuth(tokenResponse.access_token)
            contentType(ContentType.Application.Json)
            setBody(
                credentialRequest(
                    credentialConfigurationId = resolvedOffer.credentialConfigurationIds.single(),
                    proofs = proofs,
                )
            )
        }

        assertEquals(HttpStatusCode.OK, credentialResponse.status, credentialResponse.bodyAsText())
        return credentialResponse.body()
    }

    private suspend fun HttpClient.readFirstSseUpdate(
        sessionId: String,
        trigger: suspend () -> Unit,
    ): KtorSessionUpdate =
        withTimeout(15_000.milliseconds) {
            prepareGet("/issuer2/sessions/$sessionId/events") {
                accept(ContentType.Text.EventStream)
            }.execute { response ->
                assertEquals(HttpStatusCode.OK, response.status)
                val channel = response.bodyAsChannel()
                assertEquals("{}", readNextSseData(channel))
                trigger()
                readFirstSseUpdate(channel)
            }
        }

    private suspend fun readFirstSseUpdate(channel: ByteReadChannel): KtorSessionUpdate {
        while (true) {
            val data = readNextSseData(channel)
            if (data.isNotEmpty() && data != "{}") {
                return issuer2TestJson.decodeFromString(data)
            }
        }
    }

    private suspend fun readNextSseData(channel: ByteReadChannel): String {
        while (true) {
            val line = channel.readUTF8Line()
                ?: error("SSE stream closed before an event was received")
            if (line.startsWith("data:")) {
                return line.removePrefix("data:").trim()
            }
        }
    }

    private companion object {
        val walletClientConfig = ClientConfiguration(
            clientId = "issuer2-notification-test",
            redirectUris = listOf("https://wallet.example/callback"),
        )
    }
}