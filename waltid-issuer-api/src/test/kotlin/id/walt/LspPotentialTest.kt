package id.walt

import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.did.helpers.WaltidServices
import id.walt.issuer.base.config.ConfigManager
import id.walt.issuer.base.config.WebConfig
import id.walt.issuer.issuerModule
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.randomUUID
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.*
import kotlin.text.toByteArray

class LspPotentialTest {
  val http = HttpClient {
    install(ContentNegotiation) {
      json()
    }
    followRedirects = false
  }
  var webConfig: WebConfig = WebConfig("dummy")
  init {
    runBlocking {
      WaltidServices.minimalInit()
    }

    ConfigManager.loadConfigs(arrayOf())
    webConfig = ConfigManager.getConfig<WebConfig>()
    embeddedServer(
      CIO,
      port = webConfig.webPort,
      host = webConfig.webHost,
      module = Application::issuerModule
    ).start(wait = false)
  }

  @OptIn(ExperimentalEncodingApi::class)
  @Test
  fun testLspPotentialTrack1(): Unit = runBlocking {
    // ### steps 1-6
    val offerResp = http.get("http://${webConfig.webHost}:${webConfig.webPort}/openid4vc/lspPotentialCredentialOffer")
    assertEquals(HttpStatusCode.OK, offerResp.status)

    val offerUri = offerResp.bodyAsText()

    // -------- WALLET ----------
    // as WALLET: receive credential offer, either being called via deeplink or by scanning QR code
    // parse credential URI
    val parsedOffer = OpenID4VCI.parseAndResolveCredentialOfferRequestUrl(offerUri)
    assertContains(parsedOffer.credentialConfigurationIds, "potential.light.profile")

    // ### get issuer metadata, steps 7-10
    val providerMetadataUri = OpenID4VCI.getCIProviderMetadataUrl(parsedOffer.credentialIssuer)
    val providerMetadata = http.get(providerMetadataUri).body<OpenIDProviderMetadata>()
    assertNotNull(providerMetadata.credentialsSupported)
    assertContains(providerMetadata.codeChallengeMethodsSupported.orEmpty(), "S256")
    assertNotNull(providerMetadata.tokenEndpoint)

    // resolve offered credentials
    val offeredCredentials = OpenID4VCI.resolveOfferedCredentials(parsedOffer, providerMetadata)
    val offeredCredential = offeredCredentials.first()
    assertEquals(CredentialFormat.mso_mdoc, offeredCredential.format)
    assertEquals("org.iso.18013.5.1.mDL", offeredCredential.types?.first())

    // ### step 11: confirm issuance (nothing to do)

    // ### step 12-15: authorization
    val codeVerifier = randomUUID()

    val codeChallenge =
      codeVerifier.let { Base64.UrlSafe.encode(SHA256().digest(it.toByteArray(Charsets.UTF_8))).trimEnd('=') }

    val authReq = AuthorizationRequest(
      responseType = setOf(ResponseType.Code),
      clientId = "test-wallet",
      redirectUri = "https://test-wallet.org",
      scope = setOf("openid"),
      issuerState = parsedOffer.grants[GrantType.authorization_code.value]?.issuerState,
      authorizationDetails = offeredCredentials.map {
        AuthorizationDetails.fromOfferedCredential(
          it,
          providerMetadata.credentialIssuer
        )
      },
      codeChallenge = codeChallenge,
      codeChallengeMethod = "S256"
    )

    val authResp = http.get(providerMetadata.authorizationEndpoint!!) {
      authReq.toHttpParameters().forEach {
        parameter(it.key, it.value.first())
      }
    }
    assertEquals(HttpStatusCode.Found, authResp.status)
    var location = Url(authResp.headers[HttpHeaders.Location]!!)
    assertContains(location.parameters.names(), "code")

    // ### step 16-18: access token retrieval
    val tokenResp = http.submitForm(providerMetadata.tokenEndpoint!!,
      parametersOf(
        TokenRequest(
          GrantType.authorization_code, authReq.clientId,
          authReq.redirectUri, location.parameters["code"]!!,
          codeVerifier = codeVerifier
      ).toHttpParameters())).let { TokenResponse.fromJSON(it.body<JsonObject>()) }
    assertNotNull(tokenResp.accessToken)
    assertTrue(tokenResp.isSuccess)

    val deviceKey = KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1))
    // ### steps 19-22: credential issuance
    val credReq = CredentialRequest.forOfferedCredential(offeredCredential, ProofOfPossession.CWTProofBuilder(
      issuerUrl = parsedOffer.credentialIssuer, clientId = authReq.clientId, nonce = tokenResp.cNonce,
      coseKeyAlgorithm = COSE.AlgorithmID.ECDSA_256.AsCBOR().toString(),
      coseKey = deviceKey.getPublicKeyRepresentation(), null
    ).build(deviceKey))

  }
}
