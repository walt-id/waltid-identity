import E2ETestWebService.test
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.FilterData
import id.walt.webwallet.web.controllers.exchange.UsePresentationRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.uuid.UUID

class ExchangeApi(private val client: HttpClient, val wallet: UUID) {
    suspend fun resolveCredentialOffer(offerUrl: String) =
        client.post("/wallet-api/wallet/$wallet/exchange/resolveCredentialOffer") {
            setBody(offerUrl)
        }.expectSuccess().run {
            bodyAsText()
        }

    suspend fun useOfferRequest(
        offerUrl: String,
        numberOfExpected: Int,
        requireUserInput: Boolean = false,
    ) =
        client.post("/wallet-api/wallet/$wallet/exchange/useOfferRequest") {
            setBody(offerUrl)
        }.expectSuccess().run {
            val newCredentials = body<List<WalletCredential>>()
            assert(newCredentials.size == numberOfExpected) { "should have received a number of $numberOfExpected credential(s), but received ${newCredentials.size}" }
            newCredentials
        }

    suspend fun resolvePresentationRequest(presentationRequestUrl: String) =
        client.post("/wallet-api/wallet/$wallet/exchange/resolvePresentationRequest") {
            contentType(ContentType.Text.Plain)
            setBody(presentationRequestUrl)
        }.expectSuccess().run {
            body<String>().also { assert(it.contains("presentation_definition=")) }
        }

    suspend fun matchCredentialsForPresentationDefinition(
        presentationDefinition: String,
        expectedCredentialIds: List<String> = emptyList(),
    ) = client.post("/wallet-api/wallet/$wallet/exchange/matchCredentialsForPresentationDefinition") {
        setBody(presentationDefinition)
    }.expectSuccess().run {
        val matched = body<List<WalletCredential>>()
        assert(matched.size == expectedCredentialIds.size) { "presentation definition should match $expectedCredentialIds credential(s), but have ${matched.size}" }
        assert(matched.map { it.id }
            .containsAll(expectedCredentialIds)) { "matched credentials does not contain all of the expected ones" }
        matched
    }

    suspend fun unmatchedCredentialsForPresentationDefinition(
        presentationDefinition: String,
        expectedData: List<FilterData> = emptyList(),
    ) =
        test("/wallet-api/wallet/{wallet}/exchange/unmatchedCredentialsForPresentationDefinition - none should be missing") {
            client.post("/wallet-api/wallet/$wallet/exchange/unmatchedCredentialsForPresentationDefinition") {
                setBody(presentationDefinition)
            }.expectSuccess().run {
                val unmatched = body<List<FilterData>>()
                assert(unmatched.containsAll(expectedData)) { "the unmatched filters does not contain all of the expected filters" }
                unmatched
            }
        }

    suspend fun usePresentationRequest(
        request: UsePresentationRequest,
        expectStatus: suspend HttpResponse.() -> HttpResponse = expectSuccess
    ) = client.post("/wallet-api/wallet/$wallet/exchange/usePresentationRequest") {
        setBody(request)
    }.expectStatus()
}
