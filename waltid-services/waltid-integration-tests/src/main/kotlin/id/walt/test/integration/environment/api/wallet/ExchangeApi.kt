@file:OptIn(ExperimentalUuidApi::class)

package id.walt.test.integration.environment.api.wallet

import id.walt.commons.testing.E2ETest
import id.walt.test.integration.environment.api.ResponseError
import id.walt.test.integration.expectError
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
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.assertEquals
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

    suspend fun claimCredentialRaw(
        walletId: Uuid,
        offerUrl: String,
        didString: String? = null,
        requireUserInput: Boolean? = null,
        pinOrTxCode: String? = null
    ) =
        client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest") {
            url {
                didString?.also { parameter("did", it) }
                requireUserInput?.also { parameter("requireUserInput", it.toString()) }
                pinOrTxCode?.also { parameter("pinOrTxCode", it) }
            }
            setBody(offerUrl)
        }

    suspend fun claimCredential(
        walletId: Uuid,
        offerUrl: String,
        didString: String? = null,
        requireUserInput: Boolean? = null,
        pinOrTxCode: String? = null
    ): List<WalletCredential> {
        return claimCredentialRaw(walletId, offerUrl, didString, requireUserInput, pinOrTxCode)
            .expectSuccess()
            .body<List<WalletCredential>>()
    }

    suspend fun resolvePresentationRequestRaw(
        wallet: Uuid,
        presentationRequestUrl: String
    ) = client.post("/wallet-api/wallet/$wallet/exchange/resolvePresentationRequest") {
        contentType(ContentType.Text.Plain)
        setBody(presentationRequestUrl)
    }

    suspend fun resolvePresentationRequest(
        wallet: Uuid,
        presentationRequestUrl: String,
    ) = resolvePresentationRequestRaw(wallet, presentationRequestUrl).let {
        it.expectSuccess()
        val resolvedPresentationOfferString = it.body<String>()
        assertTrue(resolvedPresentationOfferString.contains("presentation_definition="))
        resolvedPresentationOfferString
    }

    suspend fun matchCredentialsForPresentationDefinitionRaw(
        wallet: Uuid,
        presentationDefinition: String
    ) =
        client.post("/wallet-api/wallet/$wallet/exchange/matchCredentialsForPresentationDefinition") {
            setBody(presentationDefinition)
        }

    suspend fun matchCredentialsForPresentationDefinition(
        walletId: Uuid,
        presentationDefinition: String,
    ) = matchCredentialsForPresentationDefinitionRaw(walletId, presentationDefinition).let {
        it.expectSuccess()
        it.body<List<WalletCredential>>()
    }

    suspend fun unmatchedCredentialsForPresentationDefinitionRaw(
        walletId: Uuid,
        presentationDefinition: String
    ) =
        client.post("/wallet-api/wallet/$walletId/exchange/unmatchedCredentialsForPresentationDefinition") {
            setBody(presentationDefinition)
        }

    suspend fun unmatchedCredentialsForPresentationDefinition(
        walletId: Uuid,
        presentationDefinition: String
    ) = unmatchedCredentialsForPresentationDefinitionRaw(walletId, presentationDefinition).let {
        it.expectSuccess()
        it.body<List<FilterData>>()
    }

    // TODO: It returns error 400 bad request when policy validation fails, although everything is ok with the request
    suspend fun usePresentationRequestRaw(
        walletId: Uuid,
        request: UsePresentationRequest,
    ) = client.post("/wallet-api/wallet/$walletId/exchange/usePresentationRequest") {
        setBody(request)
    }

    suspend fun usePresentationRequest(
        walletId: Uuid,
        request: UsePresentationRequest
    ) {
        usePresentationRequestRaw(walletId, request).expectSuccess()
    }

    suspend fun usePresentationRequestExpectError(
        walletId: Uuid,
        request: UsePresentationRequest
    ): ResponseError {
        return ResponseError.of(usePresentationRequestRaw(walletId, request).expectError())
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
            assertEquals(
                numberOfExpected,
                newCredentials.size,
                "should have received a number of $numberOfExpected credential(s), but received ${newCredentials.size}"
            )
            output?.invoke(newCredentials)
        }
    }

    @Deprecated("Old API")
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
            assertTrue(resolvedPresentationOfferString.contains("presentation_definition="))
            output?.invoke(resolvedPresentationOfferString)
        }
    }

    @Deprecated("Old API")
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
                assertEquals(
                    matched.size,
                    expectedCredentialIds.size,
                    "presentation definition should match $expectedCredentialIds credential(s), but have ${matched.size}"
                )
                assertTrue(matched.map { it.id }
                    .containsAll(expectedCredentialIds)) { "matched credentials does not contain all of the expected ones" }
                output?.invoke(matched)
            }
        }

    @Deprecated("Old API")
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
                assertTrue(
                    unmatched.containsAll(expectedData),
                    "the unmatched filters does not contain all of the expected filters"
                )
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
        }.expectStatus()
    }
}