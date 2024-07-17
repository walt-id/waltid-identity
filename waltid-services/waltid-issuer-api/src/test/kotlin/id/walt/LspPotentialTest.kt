package id.walt

import COSE.AlgorithmID
import cbor.Cbor
import com.nimbusds.jose.crypto.impl.ECDSA
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.did.dids.registrar.LocalRegistrar
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.did.helpers.WaltidServices
import id.walt.issuer.base.config.ConfigManager
import id.walt.issuer.base.config.WebConfig
import id.walt.issuer.issuerModule
import id.walt.issuer.utils.LspPotentialInteropEvent
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.issuersigned.IssuerSigned
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialOfferRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.CredentialResponse
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
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.kotlincrypto.hash.sha2.SHA256
import java.security.KeyPairGenerator
import java.security.PublicKey
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
    //val offerResp = http.get("http://${webConfig.webHost}:${webConfig.webPort}/openid4vc/lspPotentialCredentialOffer")
    val offerResp = http.get("https://issuer.potential.walt-test.cloud/openid4vc/lspPotentialCredentialOffer")
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

    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(256)
    val deviceKeyPair = kpg.genKeyPair()
    // ### steps 19-22: credential issuance

    // TODO: move COSE signing functionality to crypto lib?
    val credReq = CredentialRequest.forOfferedCredential(offeredCredential, ProofOfPossession.CWTProofBuilder(
      issuerUrl = parsedOffer.credentialIssuer, clientId = authReq.clientId, nonce = tokenResp.cNonce,
      coseKeyAlgorithm = COSE.AlgorithmID.ECDSA_256.AsCBOR().toString(),
      coseKey = deviceKeyPair.public.encoded, null
    ).build(SimpleCOSECryptoProvider(listOf(COSECryptoProviderKeyInfo("device-key", AlgorithmID.ECDSA_256,
      deviceKeyPair.public, deviceKeyPair.private))), "device-key"))

    val cwt = Cbor.decodeFromByteArray(COSESign1.serializer(), credReq.proof!!.cwt!!.base64UrlDecode())
    assertNotNull(cwt.payload)
    val cwtPayload = Cbor.decodeFromByteArray<MapElement>(cwt.payload!!)
    assertEquals(DEType.textString, cwtPayload.value.get(MapKey(ProofOfPossession.CWTProofBuilder.LABEL_ISS))?.type)
    val cwtProtectedHeader = Cbor.decodeFromByteArray<MapElement>(cwt.protectedHeader)
    assertEquals(cwtProtectedHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_ALG)]!!.value, -7L)
    assertEquals(cwtProtectedHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_CONTENT_TYPE)]!!.value, "openid4vci-proof+cwt")
    assertContentEquals((cwtProtectedHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_COSE_KEY)] as ByteStringElement).value, deviceKeyPair.public.encoded)

    val credResp = http.post(providerMetadata.credentialEndpoint!!) {
      contentType(ContentType.Application.Json)
      bearerAuth(tokenResp.accessToken!!)
      setBody(credReq.toJSON())
    }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }

    assertTrue(credResp.isSuccess)
    assertContains(credResp.customParameters.keys, "credential_encoding")
    assertEquals("issuer-signed", credResp.customParameters["credential_encoding"]!!.jsonPrimitive.content)
    assertNotNull(credResp.credential)
    val mdoc = MDoc(credReq.docType!!.toDE(), IssuerSigned.fromMapElement(
      Cbor.decodeFromByteArray(credResp.credential!!.jsonPrimitive.content.base64UrlDecode())
    ), null)
    assertEquals(credReq.docType, mdoc.docType.value)
    assertNotNull(mdoc.issuerSigned)
    assertTrue(mdoc.verifySignature(SimpleCOSECryptoProvider(listOf(
      LspPotentialInteropEvent.loadPotentialIssuerKeys()
    )), LspPotentialInteropEvent.POTENTIAL_ISSUER_KEY_ID))
  }

  @Test
  fun testParseCWTExample() {
    val data = "d28443a10126a104524173796d6d657472696345434453413235365850a701756" +
        "36f61703a2f2f61732e6578616d706c652e636f6d02656572696b77037818636f" +
        "61703a2f2f6c696768742e6578616d706c652e636f6d041a5612aeb0051a5610d" +
        "9f0061a5610d9f007420b7158405427c1ff28d23fbad1f29c4c7c6a555e601d6f" +
        "a29f9179bc3d7438bacaca5acd08c8d4d4f96131680c429a01f85951ecee743a5" +
        "2b9b63632c57209120e1c9e30"
    val parsedCwt = Cbor.decodeFromHexString(COSESign1.serializer(), data)
    parsedCwt != null

  }
}
