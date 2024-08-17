import id.walt.commons.interop.LspPotentialInterop
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.issuer.issuance.IssuanceExamples
import id.walt.issuer.issuance.IssuanceRequest
import id.walt.issuer.issuance.createCredentialOfferUri
import id.walt.issuer.lspPotential.LspPotentialIssuanceInterop
import id.walt.oid4vc.data.AuthenticationMethod
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.verifier.lspPotential.LspPotentialVerificationInterop
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
import kotlinx.serialization.json.*
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

  suspend fun testMDocIssuance() = E2ETestWebService.test("test mdoc issuance") {
    // === get credential offer from test issuer API ===
    val issuanceReq = Json.decodeFromString<IssuanceRequest>(IssuanceExamples.mDLCredentialIssuanceData).copy(
      authenticationMethod = AuthenticationMethod.PRE_AUTHORIZED
    )
    val offerResp = client.post("/openid4vc/sdjwt/issue") {
      contentType(ContentType.Application.Json)
      setBody(Json.encodeToJsonElement(issuanceReq).toString())
    }
    assert(offerResp.status == HttpStatusCode.OK)
    val offerUri = offerResp.bodyAsText()

    // === resolve credential offer ===
    val resolvedOffer = client.post("/wallet-api/wallet/$walletId/exchange/resolveCredentialOffer") {
      setBody(offerUri)
    }.expectSuccess().body<CredentialOffer>()

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
    runBlocking { issuedMdocId = fetchedCredential.id }
  }

  suspend fun testMdocPresentation() = E2ETestWebService.test("test mdoc presentation") {
    val createReqResponse = client.post("/openid4vc/verify") {
      header("authorizeBaseUrl", "mdoc-openid4vp://")
      header("responseMode", "direct_post_jwt")
      contentType(ContentType.Application.Json)
      setBody(
        buildJsonObject {
          put("request_credentials", JsonArray(listOf(JsonPrimitive("org.iso.18013.5.1.mDL"))))
          put("trusted_root_cas", JsonArray(listOf(JsonPrimitive(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT))))
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
    val matchingCreds = client.post("/wallet-api/wallet/$walletId/exchange/matchCredentialsForPresentationDefinition") {
      setBody(parsedRequest.presentationDefinition!!)
    }.expectSuccess().let { response -> response.body<List<WalletCredential>>()}
    assertNotEquals(0, matchingCreds.size)

    client.post("/wallet-api/wallet/$walletId/exchange/usePresentationRequest") {
      setBody(UsePresentationRequest(generatedDid, presReqUrl, listOf(issuedMdocId)))
    }.expectSuccess()
  }

  suspend fun testSDJwtVCIssuance() = E2ETestWebService.test("test sd-jwt-vc issuance") {
    // === get credential offer from test issuer API ===

    val issuanceReq = IssuanceRequest(
      Json.parseToJsonElement(KeySerialization.serializeKey(LspPotentialIssuanceInterop.POTENTIAL_ISSUER_JWK_KEY)).jsonObject,
      "",
      "identity_credential_vc+sd-jwt",
      credentialData = W3CVC(buildJsonObject {
        put("family_name", "Doe")
        put("given_name", "John")
      }),
      null,
      x5Chain = listOf(LspPotentialInterop.POTENTIAL_ISSUER_CERT),
      trustedRootCAs = listOf(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT)
    )
    val offerResp = client.post("/openid4vc/sdjwt/issue") {
      contentType(ContentType.Application.Json)
      setBody(Json.encodeToJsonElement(issuanceReq).toString())
    }
    assert(offerResp.status == HttpStatusCode.OK)
    val offerUri = offerResp.bodyAsText()

    // === resolve credential offer ===
    val resolvedOffer = client.post("/wallet-api/wallet/$walletId/exchange/resolveCredentialOffer") {
      setBody(offerUri)
    }.expectSuccess().let {
      it.body<CredentialOffer>()
    }
    assertEquals(1, resolvedOffer.credentialConfigurationIds.size)
    assertEquals("identity_credential_vc+sd-jwt", resolvedOffer.credentialConfigurationIds.first())

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
    runBlocking { issuedSDJwtVCId = fetchedCredential.id }
  }

  suspend fun testSDJwtPresentation() = E2ETestWebService.test("test sd-jwt-vc presentation") {
    val createReqResponse = client.post("/openid4vc/verify") {
      header("authorizeBaseUrl", "openid4vp://")
      header("openId4VPProfile","HAIP")
      header("responseMode", "direct_post")
      contentType(ContentType.Application.Json)
      setBody(
        buildJsonObject {
          put("request_credentials", JsonArray(listOf(JsonPrimitive("identity_credential_vc+sd-jwt"))))
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
    val matchingCreds = client.post("/wallet-api/wallet/$walletId/exchange/matchCredentialsForPresentationDefinition") {
      setBody(parsedRequest.presentationDefinition!!)
    }.expectSuccess().let { response -> response.body<List<WalletCredential>>()}
    assertNotEquals(0, matchingCreds.size)

    client.post("/wallet-api/wallet/$walletId/exchange/usePresentationRequest") {
      setBody(UsePresentationRequest(generatedDid, presReqUrl, listOf(issuedSDJwtVCId)))
    }.expectSuccess()
  }


}
