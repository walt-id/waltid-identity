package id.walt.wallet2.server

import id.walt.openid4vci.proofs.Proofs
import id.walt.wallet2.handlers.*
import id.walt.wallet2.server.models.OfferIssuerMetadata
import id.walt.wallet2.server.models.ResolveOfferDetailedResponse
import io.ktor.http.Url
import io.ktor.http.HttpStatusCode
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.routing.get
import id.walt.wallet2.server.handlers.respondIssuanceResult
import id.walt.wallet2.server.handlers.respondFetchCredentialResult
import id.walt.wallet2.server.handlers.respondPollDeferredResult
import kotlinx.serialization.json.*
import kotlin.test.*

class WalletIssuanceHttpContractTest {
    @Test
    fun isolatedResponsesPreserveStrictLegacySuccessAndAllDetailedProgress() = testApplication {
        val endpoint = Url("https://issuer.example/credential")
        val fetchRequest = FetchCredentialRequest(endpoint, "access", "identity")
        val pollRequest = PollDeferredRequest(endpoint, "tx", "access")
        val fetched = FetchCredentialsResult(listOf("raw"), storageOutcome = WalletIssuanceOutcome.Stored("session", listOf("id")))
        val polled = PollDeferredResult(listOf("id"))
        val deferred = FetchCredentialsResult(deferredCredential = DeferredCredentialTransaction(
            "identity", transactionId = "tx", holderBindings = listOf(CredentialHolderBinding()), intervalSeconds = 2))
        val pending = PollDeferredResult(emptyList(), DeferredCredentialPending("tx", 2))
        val failure = WalletIssuanceOutcome.Failed("session", WalletIssuanceError(WalletIssuanceErrorCode.STORAGE, "Storage unavailable"),
            storedCredentialIds = listOf("id"), deferredCredentials = listOf(WalletIssuanceContinuation("handle", "identity")))
        val fetchFailed = fetched.copy(storageOutcome = failure)
        val pollFailed = polled.copy(storageOutcome = failure)
        application {
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing {
                for ((name, fetch, poll) in listOf(Triple("success", fetched, polled),
                    Triple("pending", deferred, pending), Triple("failure", fetchFailed, pollFailed))) {
                    get("/legacy/fetch/$name") { call.respondFetchCredentialResult(fetch, fetchRequest) }
                    get("/legacy/poll/$name") { call.respondPollDeferredResult(poll, pollRequest) }
                    get("/detailed/fetch/$name") {
                        call.respondFetchCredentialResult(fetch, fetchRequest.copy(proofs = Proofs(jwt = listOf("proof"))))
                    }
                    get("/detailed/poll/$name") { call.respondPollDeferredResult(poll, pollRequest.copy(proofRequired = true)) }
                }
            }
        }
        val oldFetch = client.get("/legacy/fetch/success")
        assertEquals(HttpStatusCode.OK, oldFetch.status)
        assertEquals(FetchCredentialResult(listOf("raw")), Json.decodeFromString<FetchCredentialResult>(oldFetch.bodyAsText()))
        val oldPoll = client.get("/legacy/poll/success")
        assertEquals(HttpStatusCode.OK, oldPoll.status)
        assertEquals(ReceiveCredentialResult(listOf("id")), Json.decodeFromString<ReceiveCredentialResult>(oldPoll.bodyAsText()))
        for ((kind, expectedFetch, expectedPoll) in listOf(Triple("success", fetched, polled),
            Triple("pending", deferred, pending), Triple("failure", fetchFailed, pollFailed))) {
            for (contract in listOf("legacy", "detailed")) {
                if (contract == "legacy" && kind == "success") continue
                val expectedStatus = when {
                    contract == "legacy" && kind == "pending" -> HttpStatusCode.Conflict
                    contract == "legacy" -> HttpStatusCode.InternalServerError
                    kind == "failure" -> HttpStatusCode.MultiStatus
                    else -> HttpStatusCode.OK
                }
                val fetch = client.get("/$contract/fetch/$kind")
                val poll = client.get("/$contract/poll/$kind")
                assertEquals(expectedStatus, fetch.status)
                assertEquals(expectedStatus, poll.status)
                assertEquals(expectedFetch, Json.decodeFromString<FetchCredentialsResult>(fetch.bodyAsText()))
                assertEquals(expectedPoll, Json.decodeFromString<PollDeferredResult>(poll.bodyAsText()))
            }
        }
    }

    @Test
    fun releasedOfferCopyKeepsComponentOrderAndWireShape() {
        val issuer = "https://issuer.example"
        val response = ResolveOfferDetailedResponse(issuer, listOf("identity"), txCodeRequired = false,
            credentialEndpoint = Url("$issuer/credential"), issuer = OfferIssuerMetadata(issuer),
            offeredCredentials = emptyList())
        val copy = response.copy(grantType = "authorization_code")
        assertEquals(listOf("identity"), copy.component2())
        assertEquals(copy, Json.decodeFromString<ResolveOfferDetailedResponse>(Json.encodeToString(copy)))
    }

    @Test
    fun singleAuthorizedTargetNormalizesAtThePublicBoundaryAndRejectsAmbiguity() {
        val body = """{"code":"code","credentialIssuer":"https://issuer.example",
            "credentialEndpoint":"https://issuer.example/credential","credentialConfigurationId":"identity"}"""
        val request = Json.decodeFromString<ReceiveAuthorizedCredentialRequest>(body)
        assertEquals("identity", request.credentialConfigurationId)
        val batch = ReceiveAuthorizedCredentialsRequest(listOf(WalletCredentialSelection("identity")),
            code = request.code, credentialIssuer = request.credentialIssuer, credentialEndpoint = request.credentialEndpoint)
        assertFailsWith<IllegalArgumentException> { batch.copy(credentials = emptyList()) }
        assertEquals(batch, Json.decodeFromString<ReceiveAuthorizedCredentialsRequest>(Json.encodeToString(batch)))
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString<ReceiveAuthorizedCredentialRequest>(Json.encodeToString(batch)) }
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString<ReceiveAuthorizedCredentialsRequest>(body) }
    }

    @Test
    fun isolatedSingleProofNormalizesWithoutChangingTheProtocolShape() {
        val request = Json.decodeFromString<FetchCredentialRequest>(
            """{"credentialEndpoint":"https://issuer.example/credential","accessToken":"token",
                "credentialConfigurationId":"identity","proofJwt":"single-proof"}""")
        assertEquals(listOf("single-proof"), request.effectiveProofs?.jwt)
        assertFailsWith<IllegalArgumentException> { request.copy(proofs = Proofs(jwt = listOf("other-proof"))) }
        val single = Json.encodeToJsonElement(SignProofsResult(Proofs(jwt = listOf("single-proof")))).jsonObject
        assertEquals("single-proof", single["proofJwt"]?.jsonPrimitive?.content)
        val batch = Json.encodeToJsonElement(SignProofsResult(Proofs(jwt = listOf("one", "two")))).jsonObject
        assertFalse("proofJwt" in batch)
        assertEquals(2, batch.getValue("proofs").jsonObject.getValue("jwt").jsonArray.size)
    }

    @Test
    fun releasedHttpSuccessAndDetailedProgressRemainDistinct() = testApplication {
        val targets = listOf("a", "b").map { suffix ->
            DeferredCredentialTransaction("identity", "dataset-$suffix", "tx-$suffix",
                listOf(CredentialHolderBinding("key")), 5, deferredCredentialId = "handle-$suffix")
        }
        val single = ReceiveCredentialsResult(listOf("stored"), targets.take(1))
        val multiple = single.copy(deferredCredentials = targets)
        application {
            install(ContentNegotiation) { json(Json { encodeDefaults = true }) }
            routing {
                get("/released") { call.respondIssuanceResult(single, legacy = true) }
                get("/detailed") { call.respondIssuanceResult(single) }
                get("/unrepresentable") { call.respondIssuanceResult(multiple, legacy = true) }
            }
        }
        val released = client.get("/released")
        assertEquals(HttpStatusCode.OK, released.status)
        val oldClient = Json.decodeFromString<ReceiveCredentialResult>(released.bodyAsText())
        assertEquals(listOf("stored"), oldClient.credentialIds)
        assertEquals(mapOf("identity" to "tx-a"), oldClient.deferredTransactionIds)
        val detailed = client.get("/detailed")
        assertEquals(HttpStatusCode.OK, detailed.status)
        assertEquals(single, Json.decodeFromString<ReceiveCredentialsResult>(detailed.bodyAsText()))
        val incompatible = client.get("/unrepresentable")
        assertEquals(HttpStatusCode.Conflict, incompatible.status)
        assertEquals(multiple, Json.decodeFromString<ReceiveCredentialsResult>(incompatible.bodyAsText()))
    }

    @Test
    fun legacyResponseProjectionsNeverDropRepeatedDatasetsOrConfigurations() {
        val targets = listOf(
            DeferredCredentialTransaction("identity", "dataset-a", "tx-a", listOf(CredentialHolderBinding("key-a")), 5),
            DeferredCredentialTransaction("identity", "dataset-b", "tx-b", listOf(CredentialHolderBinding("key-b")), 5),
        )
        val single = Json.encodeToJsonElement(ReceiveCredentialResult(ReceiveCredentialsResult(emptyList(), targets.take(1)))).jsonObject
        assertEquals("tx-a", single.getValue("deferredTransactionIds").jsonObject["identity"]?.jsonPrimitive?.content)
        val batch = Json.encodeToJsonElement(ReceiveCredentialsResult(emptyList(), targets)).jsonObject
        assertFalse("deferredTransactionIds" in batch)
        assertFailsWith<CredentialReceiveException> { ReceiveCredentialResult(ReceiveCredentialsResult(emptyList(), targets)) }
        assertEquals(2, batch.getValue("deferredCredentials").jsonArray.size)
        val authorization = GenerateAuthorizationUrlResult(Url("https://issuer.example/authorize"), "state",
            credentialConfigurationId = "identity", credentialIssuerBaseUrl = "https://issuer.example")
        val singleJson = Json.encodeToJsonElement(authorization).jsonObject
        assertEquals("identity", singleJson["credentialConfigurationId"]?.jsonPrimitive?.content)
        assertFalse("credentialConfigurationIds" in singleJson)
        val multiple = GenerateBatchAuthorizationUrlResult(authorization.authorizationUrl, authorization.state,
            credentialConfigurationIds = listOf("identity", "other"), credentialIssuerBaseUrl = "https://issuer.example")
        val multipleJson = Json.encodeToJsonElement(multiple).jsonObject
        assertFalse("credentialConfigurationId" in multipleJson)
        assertEquals(2, multipleJson.getValue("credentialConfigurationIds").jsonArray.size)
    }
}
