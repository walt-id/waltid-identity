@file:OptIn(ExperimentalUuidApi::class)

import id.walt.commons.testing.utils.ServiceTestUtils.loadResource
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.oid4vc.OpenID4VCIVersion
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class EBSIVector(
    private val client: HttpClient,
    private val wallet: Uuid,
) {

    private val draft11IssuanceRequestOfferedCredentialsByValue = Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request.json")).copy(
        standardVersion = OpenID4VCIVersion.DRAFT11,
        draft11EncodeOfferedCredentialsByReference = false,
    )

    private val draft11IssuanceRequestOfferedCredentialsById = Json.decodeFromString<IssuanceRequest>(loadResource("issuance/openbadgecredential-issuance-request.json")).copy(
        standardVersion = OpenID4VCIVersion.DRAFT11,
    )

    suspend fun runTest() {

        val exchangeApi = ExchangeApi(client)
        val issuerApi = IssuerApi(client)
        lateinit var offerUri: String


        issuerApi.jwt(draft11IssuanceRequestOfferedCredentialsByValue) {
            offerUri = it
        }

        var credentialOfferUri = Url(offerUri).parameters["credential_offer_uri"]!!

        client.get(credentialOfferUri).expectSuccess().bodyAsText().let {
            println(it)
        }

        exchangeApi.useOfferRequest(
            wallet,
            offerUri,
            1,
            false
        )

        issuerApi.jwt(draft11IssuanceRequestOfferedCredentialsById) {
            offerUri = it
        }

        credentialOfferUri = Url(offerUri).parameters["credential_offer_uri"]!!

        client.get(credentialOfferUri).expectSuccess().bodyAsText().let {
            println(it)
        }

        exchangeApi.useOfferRequest(
            wallet,
            offerUri,
            1,
            false
        )
    }
}