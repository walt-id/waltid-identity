import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyType
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.webwallet.db.models.WalletCredential
import id.walt.webwallet.db.models.WalletDid
import id.walt.webwallet.web.controllers.UsePresentationRequest
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.uuid.UUID
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class LspPotentialWallet(val client: HttpClient, val walletId: String) {
  private var issuedMdocId: String = ""
  private var issuedSDJwtVCId: String = ""
  private lateinit var generatedKeyId: String
  private lateinit var generatedDid: String

  init {
    // === create EC256 key and DID:JWK (did is not necessarily required, but currently needed for wallet initialization) ===
    val keysApi = KeysApi(client)

    runBlocking {
      keysApi.generate(UUID(walletId), KeyGenerationRequest(keyType = KeyType.secp256r1)) { generatedKeyId = it }
      DidsApi(client).create(UUID(walletId), DidsApi.DidCreateRequest("jwk", keyId = generatedKeyId)) {
        generatedDid = it
      }
    }
  }

  fun testMDocIssuance() = runBlocking {
    // === get credential offer from test issuer API ===
    val offerResp = client.get("/lsp-potential/lspPotentialCredentialOfferT1")
    assert(offerResp.status == HttpStatusCode.OK)
    val offerUri = offerResp.bodyAsText()

    // === resolve credential offer ===
    val resolvedOffer = client.post("/wallet-api/wallet/$walletId/exchange/resolveCredentialOffer") {
      setBody(offerUri)
    }.expectSuccess().let {
      it.body<CredentialOffer>()
    }
    assertEquals(1, resolvedOffer.credentialConfigurationIds.size)
    assertEquals("org.iso.18013.5.1.mDL", resolvedOffer.credentialConfigurationIds.first())

    // === resolve issuer metadata ===
    val issuerMetadata = client.get("${resolvedOffer.credentialIssuer}/.well-known/openid-credential-issuer").expectSuccess().let {
      it.body<OpenIDProviderMetadata>()
    }
    assertEquals(issuerMetadata.issuer, resolvedOffer.credentialIssuer)
    assertContains(issuerMetadata.credentialConfigurationsSupported!!.keys, resolvedOffer.credentialConfigurationIds.first())

    // === use credential offer request ===
    val issuedCred = client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest?did=$generatedDid") {
      setBody(offerUri)
    }.expectSuccess().body<List<WalletCredential>>().first()

    assertEquals(CredentialFormat.mso_mdoc, issuedCred.format)

    // === get issued credential from wallet-api
    val fetchedCredential = client.get("/wallet-api/wallet/$walletId/credentials/${issuedCred.id}")
      .expectSuccess().body<WalletCredential>()
    assertEquals(issuedCred.format, fetchedCredential.format)
    issuedMdocId = fetchedCredential.id
  }

  fun testMdocPresentation() = runBlocking {
    val createReqResponse = client.post("/openid4vc/verify") {
      header("authorizeBaseUrl", "mdoc-openid4vp://")
      header("responseMode", "direct_post_jwt")
      contentType(ContentType.Application.Json)
      setBody(
        buildJsonObject {
          put("request_credentials", JsonArray(listOf(JsonPrimitive("org.iso.18013.5.1.mDL"))))
        })
    }
    assertEquals(200, createReqResponse.status.value)
    val presReqUrl = createReqResponse.bodyAsText()

    // === resolve presentation request ===
    val parsedRequest = client.post("/wallet-api/wallet/$walletId/exchange/resolvePresentationRequest") {
      setBody(presReqUrl)
    }.expectSuccess().let { response ->
      response.body<String>().let { AuthorizationRequest.fromHttpParameters(parseQueryString(Url(it).encodedQuery).toMap()) }
    }
    assertNotNull(parsedRequest.presentationDefinition)

    // === find matching credential ===
//    val matchingCreds = client.post("/wallet-api/wallet/$walletId/exchange/matchCredentialsForPresentationDefinition") {
//      setBody(parsedRequest.presentationDefinition!!)
//    }.expectSuccess().let { response -> response.body<List<WalletCredential>>()}
//    assertNotEquals(0, matchingCreds.size)

    client.post("/wallet-api/wallet/$walletId/exchange/usePresentationRequest") {
      setBody(UsePresentationRequest(generatedDid, presReqUrl, listOf(issuedMdocId)))
    }.expectSuccess()
  }

  fun testSDJwtVCIssuance() = runBlocking {
    // === get credential offer from test issuer API ===
    val offerResp = client.get("/lsp-potential/lspPotentialCredentialOfferT2")
    assert(offerResp.status == HttpStatusCode.OK)
    val offerUri = offerResp.bodyAsText()

    // === resolve credential offer ===
    val resolvedOffer = client.post("/wallet-api/wallet/$walletId/exchange/resolveCredentialOffer") {
      setBody(offerUri)
    }.expectSuccess().let {
      it.body<CredentialOffer>()
    }
    assertEquals(1, resolvedOffer.credentialConfigurationIds.size)
    assertEquals("urn:eu.europa.ec.eudi:pid:1", resolvedOffer.credentialConfigurationIds.first())

    // === resolve issuer metadata ===
    val issuerMetadata = client.get("${resolvedOffer.credentialIssuer}/.well-known/openid-credential-issuer").expectSuccess().let {
      it.body<OpenIDProviderMetadata>()
    }
    assertEquals(issuerMetadata.issuer, resolvedOffer.credentialIssuer)
    assertContains(issuerMetadata.credentialConfigurationsSupported!!.keys, resolvedOffer.credentialConfigurationIds.first())

    // === use credential offer request ===
    val issuedCred = client.post("/wallet-api/wallet/$walletId/exchange/useOfferRequest?did=$generatedDid") {
      setBody(offerUri)
    }.expectSuccess().body<List<WalletCredential>>().first()

    assertEquals(CredentialFormat.sd_jwt_vc, issuedCred.format)

    // === get issued credential from wallet-api
    val fetchedCredential = client.get("/wallet-api/wallet/$walletId/credentials/${issuedCred.id}")
      .expectSuccess().body<WalletCredential>()
    assertEquals(issuedCred.format, fetchedCredential.format)
    issuedSDJwtVCId = fetchedCredential.id
  }

  fun testSDJwtPresentation() = runBlocking {
    val createReqResponse = client.post("/openid4vc/verify") {
      header("authorizeBaseUrl", "haip://")
      header("responseMode", "direct_post")
      contentType(ContentType.Application.Json)
      setBody(
        buildJsonObject {
          put("request_credentials", JsonArray(listOf(JsonPrimitive("urn:eu.europa.ec.eudi:pid:1"))))
        })
    }
    assertEquals(200, createReqResponse.status.value)
    val presReqUrl = createReqResponse.bodyAsText()

    // === resolve presentation request ===
    val parsedRequest = client.post("/wallet-api/wallet/$walletId/exchange/resolvePresentationRequest") {
      setBody(presReqUrl)
    }.expectSuccess().let { response ->
      response.body<String>().let { AuthorizationRequest.fromHttpParameters(parseQueryString(Url(it).encodedQuery).toMap()) }
    }
    assertNotNull(parsedRequest.presentationDefinition)

    // === find matching credential ===
//    val matchingCreds = client.post("/wallet-api/wallet/$walletId/exchange/matchCredentialsForPresentationDefinition") {
//      setBody(parsedRequest.presentationDefinition!!)
//    }.expectSuccess().let { response -> response.body<List<WalletCredential>>()}
//    assertNotEquals(0, matchingCreds.size)

    client.post("/wallet-api/wallet/$walletId/exchange/usePresentationRequest") {
      setBody(UsePresentationRequest(generatedDid, presReqUrl, listOf(issuedSDJwtVCId)))
    }.expectSuccess()
  }


}
