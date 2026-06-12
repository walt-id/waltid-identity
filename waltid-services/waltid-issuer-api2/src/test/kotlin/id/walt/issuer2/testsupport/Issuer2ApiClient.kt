package id.walt.issuer2.testsupport

import id.walt.issuer2.models.CredentialOfferCreateRequest
import id.walt.issuer2.models.CredentialOfferCreateResponse
import id.walt.issuer2.domain.CredentialProfile
import id.walt.issuer2.domain.IssuanceSession
import id.walt.openid4vci.offers.AuthenticationMethod
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.offers.CredentialOfferValueMode
import id.walt.openid4vci.offers.IssuerStateMode
import id.walt.openid4vci.offers.TxCode
import id.waltid.openid4vci.wallet.offer.CredentialOfferParser
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

suspend fun HttpClient.listProfiles(): List<CredentialProfile> =
    get("/issuer2/profiles").also {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
    }.body()

suspend fun HttpClient.getProfile(profileId: String): CredentialProfile =
    get("/issuer2/profiles/$profileId").also {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
    }.body()

suspend fun HttpClient.getSession(sessionId: String): IssuanceSession =
    get("/issuer2/sessions/$sessionId").also {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
    }.body()

suspend fun HttpClient.listSessions(): List<IssuanceSession> =
    get("/issuer2/sessions").also {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
    }.body()

suspend fun HttpClient.createCredentialOffer(
    request: CredentialOfferCreateRequest,
): CredentialOfferCreateResponse {
    val response = post("/issuer2/credential-offers") {
        contentType(ContentType.Application.Json)
        setBody(request)
    }
    assertEquals(HttpStatusCode.Created, response.status, response.bodyAsText())
    return response.body()
}

suspend fun HttpClient.createWalletFlowCredentialOffer(
    scenario: Issuer2CredentialScenario,
    authenticationMethod: AuthenticationMethod,
    valueMode: CredentialOfferValueMode = CredentialOfferValueMode.BY_REFERENCE,
    issuerStateMode: IssuerStateMode? = if (authenticationMethod == AuthenticationMethod.AUTHORIZED) {
        IssuerStateMode.INCLUDE
    } else {
        null
    },
    txCodeMode: Issuer2TxCodeMode? = if (authenticationMethod == AuthenticationMethod.PRE_AUTHORIZED) {
        Issuer2TxCodeMode.GENERATED
    } else {
        null
    },
): CredentialOfferCreateResponse =
    createCredentialOffer(
        CredentialOfferCreateRequest(
            profileId = scenario.profileId,
            authMethod = authenticationMethod,
            valueMode = valueMode,
            issuerStateMode = issuerStateMode,
            txCode = txCodeMode?.txCode(),
            txCodeValue = txCodeMode?.txCodeValue(),
        )
    )

suspend fun HttpClient.createWalletFlowCredentialOffer(
    scenario: Issuer2CredentialScenario,
    variant: Issuer2FlowVariant,
): CredentialOfferCreateResponse {
    require(!variant.offerless) { "Offerless variants do not create credential offers" }
    return createWalletFlowCredentialOffer(
        scenario = scenario,
        authenticationMethod = variant.authMethod,
        valueMode = requireNotNull(variant.valueMode),
        issuerStateMode = variant.issuerStateMode,
        txCodeMode = variant.txCodeMode,
    )
}

private fun Issuer2TxCodeMode.txCode(): TxCode? =
    when (this) {
        Issuer2TxCodeMode.NONE -> null
        Issuer2TxCodeMode.GENERATED,
        Issuer2TxCodeMode.PROVIDED -> TxCode(
            inputMode = "numeric",
            length = 6,
            description = "Issuer2 wallet-flow test PIN",
        )
    }

private fun Issuer2TxCodeMode.txCodeValue(): String? =
    when (this) {
        Issuer2TxCodeMode.NONE,
        Issuer2TxCodeMode.GENERATED -> null
        Issuer2TxCodeMode.PROVIDED -> Issuer2FlowVariants.PROVIDED_TX_CODE_VALUE
    }

fun CredentialOfferCreateResponse.inlineOffer(): CredentialOffer {
    val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(credentialOffer)
    assertNull(offerRequest.credentialOfferUri)
    return assertNotNull(offerRequest.credentialOffer)
}

fun CredentialOfferCreateResponse.referencedOfferUri(): String {
    val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(credentialOffer)
    assertNull(offerRequest.credentialOffer)
    return assertNotNull(offerRequest.credentialOfferUri)
}

suspend fun CredentialOfferCreateResponse.resolveOffer(client: HttpClient): CredentialOffer {
    val offerRequest = CredentialOfferParser.parseCredentialOfferUrl(credentialOffer)
    offerRequest.credentialOffer?.let { return it }

    val credentialOfferUri = assertNotNull(offerRequest.credentialOfferUri)
    val url = Url(credentialOfferUri)
    val query = url.encodedQuery.takeIf { it.isNotBlank() }?.let { "?$it" } ?: ""
    return client.get("${url.encodedPath}$query").also {
        assertEquals(HttpStatusCode.OK, it.status, it.bodyAsText())
    }.body()
}