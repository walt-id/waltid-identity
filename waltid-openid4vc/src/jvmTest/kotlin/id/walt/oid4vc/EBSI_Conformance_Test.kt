package id.walt.oid4vc

import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.servicematrix.ServiceMatrix
import io.kotest.common.runBlocking
import io.kotest.core.spec.style.AnnotationSpec
import io.kotest.core.spec.style.Test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.JsonObject

class EBSI_Conformance_Test: AnnotationSpec() {

  lateinit var credentialWallet: EBSITestWallet

  val ktorClient = HttpClient(Java) {
    install(ContentNegotiation) {
      json()
    }
    followRedirects = false
  }

  @BeforeAll
  fun init() {
    ServiceMatrix("service-matrix.properties")
    credentialWallet = EBSITestWallet(CredentialWalletConfig("https://blank/"))
  }

  @Test
  fun testReceiveCredential() {
    val initCredentialOfferUrl = "https://api-conformance.ebsi.eu/conformance/v3/issuer-mock/initiate-credential-offer?credential_type=CTWalletCrossInTime&client_id=${credentialWallet.TEST_DID}&credential_offer_endpoint=openid-credential-offer://"
    val inTimeCredentialOfferRequestUri = runBlocking { ktorClient.get(Url(initCredentialOfferUrl)).bodyAsText() }
    val credentialOfferRequest = CredentialOfferRequest.fromHttpQueryString(Url(inTimeCredentialOfferRequestUri).encodedQuery)

    val credentialResponses = credentialWallet.executeFullAuthIssuance(credentialOfferRequest, credentialWallet.TEST_DID, OpenIDClientConfig(credentialWallet.TEST_DID, null, credentialWallet.config.redirectUri, useCodeChallenge = true))
    credentialResponses.size shouldNotBe 0
  }
}