@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.expectSuccess
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.FilterData
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class ExchangeApi(private val e2e: E2ETest, private val client: HttpClient) {

    suspend fun resolveCredentialOfferRaw(walletId: Uuid, offerUrl: String) =
        client.post("/wallet-api/wallet/$walletId/exchange/resolveCredentialOffer") {
            setBody(offerUrl)
        }

    suspend fun resolveCredentialOffer(walletId: Uuid, offerUrl: String): JsonObject {
        return resolveCredentialOfferRaw(walletId, offerUrl)
            .expectSuccess()
            .body<JsonObject>()
    }

    suspend fun claimCredentialRaw(walletId: Uuid, offerUrl: String) =
        client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest") {
            setBody(offerUrl)
        }

    suspend fun claimCredential(walletId: Uuid, offerUrl: String): List<WalletCredential> {
        return claimCredentialRaw(walletId, offerUrl)
            .expectSuccess()
            .body<List<WalletCredential>>()
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun useOfferRequest(
        wallet: Uuid,
        offerUrl: String,
        numberOfExpected: Int,
        requireUserInput: Boolean = false,
        output: ((List<WalletCredential>) -> Unit)? = null,
    ) = e2e.test("/wallet-api/wallet/{wallet}/exchange/useOfferRequest - claim credential from issuer") {
        client.post("/wallet-api/wallet/$wallet/exchange/useOfferRequest") {
            setBody(offerUrl)
        }.expectSuccess().apply {
            val newCredentials = body<List<WalletCredential>>()
            assert(newCredentials.size == numberOfExpected) { "should have received a number of $numberOfExpected credential(s), but received ${newCredentials.size}" }
            output?.invoke(newCredentials)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun resolvePresentationRequest(
        wallet: Uuid,
        presentationRequestUrl: String,
        output: ((String) -> Unit)? = null,
    ) = e2e.test("/wallet-api/wallet/{wallet}/exchange/resolvePresentationRequest - get presentation definition") {
        client.post("/wallet-api/wallet/$wallet/exchange/resolvePresentationRequest") {
            contentType(ContentType.Text.Plain)
            setBody(presentationRequestUrl)
        }.expectSuccess().apply {
            val resolvedPresentationOfferString = body<String>()
            assert(resolvedPresentationOfferString.contains("presentation_definition="))
            output?.invoke(resolvedPresentationOfferString)
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun matchCredentialsForPresentationDefinition(
        wallet: Uuid,
        presentationDefinition: String,
        expectedCredentialIds: List<String> = emptyList(),
        output: ((List<WalletCredential>) -> Unit)? = null,
    ) =
        e2e.test("/wallet-api/wallet/{wallet}/exchange/matchCredentialsForPresentationDefinition - should match OpenBadgeCredential in wallet") {
            client.post("/wallet-api/wallet/$wallet/exchange/matchCredentialsForPresentationDefinition") {
                setBody(presentationDefinition)
            }.expectSuccess().apply {
                val matched = body<List<WalletCredential>>()
                assert(matched.size == expectedCredentialIds.size) { "presentation definition should match $expectedCredentialIds credential(s), but have ${matched.size}" }
                assert(matched.map { it.id }
                    .containsAll(expectedCredentialIds)) { "matched credentials does not contain all of the expected ones" }
                output?.invoke(matched)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun unmatchedCredentialsForPresentationDefinition(
        wallet: Uuid,
        presentationDefinition: String,
        expectedData: List<FilterData> = emptyList(),
        output: ((List<FilterData>) -> Unit)? = null,
    ) =
        e2e.test("/wallet-api/wallet/{wallet}/exchange/unmatchedCredentialsForPresentationDefinition - none should be missing") {
            client.post("/wallet-api/wallet/$wallet/exchange/unmatchedCredentialsForPresentationDefinition") {
                setBody(presentationDefinition)
            }.expectSuccess().apply {
                val unmatched = body<List<FilterData>>()
                assert(unmatched.containsAll(expectedData)) { "the unmatched filters does not contain all of the expected filters" }
                output?.invoke(unmatched)
            }
        }

    @OptIn(ExperimentalUuidApi::class)
    suspend fun usePresentationRequest(
        wallet: Uuid,
        request: UsePresentationRequest,
        expectStatus: suspend HttpResponse.() -> HttpResponse = expectSuccess,
    ) = e2e.test("/wallet-api/wallet/{wallet}/exchange/usePresentationRequest - present credentials") {
        client.post("/wallet-api/wallet/$wallet/exchange/usePresentationRequest") {
            setBody(request)
        }.also { println("usePresentationRequest result: ${it.bodyAsText()}") }.expectStatus()
    }
}