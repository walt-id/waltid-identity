import E2ETestWebService.test
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.usecase.exchange.FilterData
import id.walt.webwallet.web.controllers.UsePresentationRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.uuid.UUID

class ExchangeApi(private val client: HttpClient) {
    suspend fun resolveCredentialOffer(wallet: UUID, offerUrl: String, output: ((String) -> Unit)? = null) =
        test("/wallet-api/wallet/{wallet}/exchange/resolveCredentialOffer - resolve credential offer") {
            client.post("/wallet-api/wallet/$wallet/exchange/resolveCredentialOffer") {
                setBody(offerUrl)
            }.expectSuccess().apply {
                output?.invoke(bodyAsText())
            }
        }

    suspend fun useOfferRequest(
        wallet: UUID,
        offerUrl: String,
        numberOfExpected: Int,
        requireUserInput: Boolean = false,
        output: ((List<WalletCredential>) -> Unit)? = null
    ) = test("/wallet-api/wallet/{wallet}/exchange/useOfferRequest - claim credential from issuer") {
        client.post("/wallet-api/wallet/$wallet/exchange/useOfferRequest") {
            setBody(offerUrl)
        }.expectSuccess().apply {
            val newCredentials = body<List<WalletCredential>>()
            assert(newCredentials.size == numberOfExpected) { "should have received a number of $numberOfExpected credential(s), but received ${newCredentials.size}" }
            output?.invoke(newCredentials)
        }
    }

    suspend fun resolvePresentationRequest(
        wallet: UUID,
        presentationRequestUrl: String,
        output: ((String) -> Unit)? = null
    ) = test("/wallet-api/wallet/{wallet}/exchange/resolvePresentationRequest - get presentation definition") {
        client.post("/wallet-api/wallet/$wallet/exchange/resolvePresentationRequest") {
            contentType(ContentType.Text.Plain)
            setBody(presentationRequestUrl)
        }.expectSuccess().apply {
            val resolvedPresentationOfferString = body<String>()
            assert(resolvedPresentationOfferString.contains("presentation_definition="))
            output?.invoke(resolvedPresentationOfferString)
        }
    }

    suspend fun matchCredentialsForPresentationDefinition(
        wallet: UUID,
        presentationDefinition: String,
        expectedCredentialIds: List<String> = emptyList(),
        output: ((List<WalletCredential>) -> Unit)? = null
    ) =
        test("/wallet-api/wallet/{wallet}/exchange/matchCredentialsForPresentationDefinition - should match OpenBadgeCredential in wallet") {
            client.post("/wallet-api/wallet/$wallet/exchange/matchCredentialsForPresentationDefinition") {
                setBody(presentationDefinition)
            }.expectSuccess().apply {
                val matched = body<List<WalletCredential>>()
                assert(matched.size == expectedCredentialIds.size) { "presentation definition should match $expectedCredentialIds credential(s), but have ${matched.size}" }
                assert(matched.map { it.id }.containsAll(expectedCredentialIds)) { "matched credentials does not contain all of the expected ones" }
                output?.invoke(matched)
            }
        }

    suspend fun unmatchedCredentialsForPresentationDefinition(
        wallet: UUID,
        presentationDefinition: String,
        expectedData: List<FilterData> = emptyList(),
        output: ((List<FilterData>) -> Unit)? = null
    ) =
        test("/wallet-api/wallet/{wallet}/exchange/unmatchedCredentialsForPresentationDefinition - none should be missing") {
            client.post("/wallet-api/wallet/$wallet/exchange/unmatchedCredentialsForPresentationDefinition") {
                setBody(presentationDefinition)
            }.expectSuccess().apply {
                val unmatched = body<List<FilterData>>()
                assert(unmatched.containsAll(expectedData)) { "the unmatched filters does not contain all of the expected filters" }
                output?.invoke(unmatched)
            }
        }

    suspend fun usePresentationRequest(
        wallet: UUID,
        did: String? = null,
        presentationRequestUrl: String,
        credentialIds: List<String>
    ) = test("/wallet-api/wallet/{wallet}/exchange/usePresentationRequest - present credentials") {
        client.post("/wallet-api/wallet/$wallet/exchange/usePresentationRequest") {
            setBody(
                UsePresentationRequest(
                    did = did,
                    presentationRequest = presentationRequestUrl,
                    selectedCredentials = credentialIds,
                )
            )
        }.expectSuccess()
    }
}