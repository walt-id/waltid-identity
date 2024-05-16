package id.walt

import COSE.AlgorithmID
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.Base64URL
import id.walt.credentials.verification.PolicyManager
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.did.dids.resolver.LocalResolver
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.EncodedCBORElement
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.NullElement
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.doc.MDocVerificationParams
import id.walt.mdoc.doc.VerificationType
import id.walt.mdoc.docrequest.MDocRequestBuilder
import id.walt.mdoc.mdocauth.DeviceAuthentication
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.util.http
import id.walt.verifier.base.config.ConfigManager
import id.walt.verifier.base.config.WebConfig
import id.walt.verifier.oidc.LspPotentialInteropEvent
import id.walt.verifier.policies.PresentationDefinitionPolicy
import id.walt.verifier.verifierModule
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OidcTest {

//  init {
//    runBlocking {
//      DidService.apply {
//        registerResolver(LocalResolver())
//        updateResolversForMethods()
//      }
//    }
//    PolicyManager.registerPolicies(PresentationDefinitionPolicy())
//
//    ConfigManager.loadConfigs(arrayOf())
//
//    val webConfig = ConfigManager.getConfig<WebConfig>()
//
//    embeddedServer(CIO, port = webConfig.webPort, host = webConfig.webHost, module = Application::verifierModule)
//      .start(wait = false)
//  }

  val baseUrl = "http://localhost:7003"

  //@Test
  fun testPotentialInteropFlow() {
    println("Starting test")

    runBlocking {
      // Step 1: Fetch mdoc
      val holderKey = KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1))
      assertEquals(KeyType.secp256r1, holderKey.getPublicKey().keyType)
      assertTrue(holderKey.hasPrivateKey)
      val holderKeyPubJwk = holderKey.getPublicKey().exportJWK()
      val issueResponse = http.submitForm("$baseUrl/lsp-potential/issueMdl", parametersOf(
        "jwk", holderKeyPubJwk
      ))
      assertEquals(200, issueResponse.status.value)
      val mdoc = MDoc.fromCBORHex(issueResponse.bodyAsText())
      assertEquals("org.iso.18013.5.1.mDL", mdoc.docType.value)

      // Step 2: Create an openid4vc verification request
      val createReqResponse = http.post("$baseUrl/openid4vc/verify") {
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
      assertTrue(presReqUrl.startsWith("mdoc-openid4vp://"))
      val presReq = AuthorizationRequest.fromHttpParametersAuto(parseQueryString(Url(presReqUrl).encodedQuery).toMap())
      assertNotNull(presReq.presentationDefinition)

      // Step 4: Get client_metadata for encrypted response
      assertNotNull(presReq.clientMetadata?.jwks)
      assertEquals("ECDH-ES", presReq.clientMetadata!!.authorizationEncryptedResponseAlg!!)
      assertEquals("A256GCM", presReq.clientMetadata!!.authorizationEncryptedResponseEnc!!)

      // Step 5: Create encrypted presentation response
      val ephemeralReaderKey = JWKKey.importJWK(presReq.clientMetadata!!.jwks!!["keys"]!!.jsonArray.first().toString()).getOrNull()!!
      val mdocNonce = UUID.generateUUID().toString()
      val mdocHandover = OpenID4VP.generateMDocOID4VPHandover(presReq, mdocNonce)
      val holderKeyNimbus = ECKey.parse(holderKey.exportJWK())
      val deviceCryptoProvider = SimpleCOSECryptoProvider(listOf(
        LspPotentialInteropEvent.POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO,
        COSECryptoProviderKeyInfo(holderKey.getKeyId(), AlgorithmID.ECDSA_256, holderKeyNimbus.toECPublicKey(),
          holderKeyNimbus.toECPrivateKey())
      ))
      val deviceAuthentication = DeviceAuthentication(sessionTranscript = ListElement(listOf(
        NullElement(),
        NullElement(), //EncodedCBORElement(ephemeralReaderKey.getPublicKeyRepresentation()),
        mdocHandover
      )), mdoc.docType.value, EncodedCBORElement(MapElement(mapOf()))
      )
      val presentedMdoc = mdoc.presentWithDeviceSignature(
        MDocRequestBuilder(mdoc.docType.value).also {
          presReq.presentationDefinition!!.inputDescriptors.forEach { inputDescriptor ->
            inputDescriptor.constraints!!.fields!!.forEach { field ->
              field.addToMdocRequest(it)
            }
          }
        }.build(),
        deviceAuthentication, deviceCryptoProvider, holderKey.getKeyId())

      val verificationResult = presentedMdoc.verify(
        MDocVerificationParams(
          VerificationType.forPresentation,
          issuerKeyID = LspPotentialInteropEvent.POTENTIAL_ISSUER_KEY_ID, deviceKeyID = holderKey.getKeyId(),
          deviceAuthentication = DeviceAuthentication(
            ListElement(listOf(NullElement(), NullElement(), mdocHandover)),
            presReq.presentationDefinition?.inputDescriptors?.first()?.id!!, EncodedCBORElement(MapElement(mapOf()))
          )
        ), deviceCryptoProvider)
      assertTrue(verificationResult)

      // Step 6: Submit response
      val deviceResponse = DeviceResponse(listOf(presentedMdoc))
      val oid4vpResponse = OpenID4VP.generatePresentationResponse(
        PresentationResult(
        presentations = listOf(JsonPrimitive(deviceResponse.toCBORBase64URL())),
        presentationSubmission = PresentationSubmission(
          "response_1", "request_1",
          listOf(DescriptorMapping(mdoc.docType.value, VCFormat.mso_mdoc, "$"))
        )
      )
      )
      assertNotNull(oid4vpResponse.vpToken)
      assertEquals(presentedMdoc.toCBORHex(), DeviceResponse.fromCBORBase64URL(oid4vpResponse.vpToken!!.jsonPrimitive.content).documents.first().toCBORHex())
      val encKey = presReq.clientMetadata?.jwks?.get("keys")?.jsonArray?.first {
          jwk -> JWK.parse(jwk.toString()).keyUse?.equals(KeyUse.ENCRYPTION) ?: false }?.jsonObject ?: throw Exception("No ephemeral reader key found")

      val ephemeralWalletKey = runBlocking { KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1)) }
      val formParams = oid4vpResponse.toDirecPostJWTParameters(encKey,
        alg = presReq.clientMetadata!!.authorizationEncryptedResponseAlg!!,
        enc = presReq.clientMetadata!!.authorizationEncryptedResponseEnc!!,
        mapOf(
          "epk" to runBlocking{ ephemeralWalletKey.getPublicKey().exportJWKObject() },
          "apu" to JsonPrimitive(Base64URL.encode(mdocNonce).toString()),
          "apv" to JsonPrimitive(Base64URL.encode(presReq.nonce!!).toString())
        )
      )
      val presResponse = http.submitForm(presReq.responseUri!!, parametersOf(formParams))
      assertEquals(200, presResponse.status.value)
    }
  }

    /*
    fun testClientFlow() {

        // -------- WALLET ----------
        // as WALLET: receive credential offer, either being called via deeplink or by scanning QR code
        // parse credential URI
        val parsedOfferReq = CredentialOfferRequest.fromHttpParameters(Url(offerUri).parameters.toMap())

        // get verfier metadata
        val providerMetadataUri = credentialWallet.getCIProviderMetadataUrl(parsedOfferReq.credentialOffer!!.credentialVerfier)
        val providerMetadata = ktorClient.get(providerMetadataUri).call.body<OpenIDProviderMetadata>()
        providerMetadata.credentialsSupported shouldNotBe null

        // resolve offered credentials
        val offeredCredentials = parsedOfferReq.credentialOffer!!.resolveOfferedCredentials(providerMetadata)
        val offeredCredential = offeredCredentials.first()

        // go through full authorization code flow to receive offered credential
        // auth request (short-cut, without pushed authorization request)
        val authReq = AuthorizationRequest(
            ResponseType.code.name, testCIClientConfig.clientID,
            redirectUri = credentialWallet.config.redirectUri,
            verfierState = parsedOfferReq.credentialOffer!!.grants[GrantType.authorization_code.value]!!.verfierState
        )
        val authResp = ktorClient.get(providerMetadata.authorizationEndpoint!!) {
            url {
                parameters.appendAll(parametersOf(authReq.toHttpParameters()))
            }
        }
        authResp.status shouldBe HttpStatusCode.Found
        val location = Url(authResp.headers[HttpHeaders.Location]!!)
        location.parameters.names() shouldContain ResponseType.code.name

        // token req
        val tokenReq =
            TokenRequest(GrantType.authorization_code, testCIClientConfig.clientID, code = location.parameters[ResponseType.code.name]!!)
        val tokenResp = ktorClient.submitForm(
            providerMetadata.tokenEndpoint!!,
            formParameters = parametersOf(tokenReq.toHttpParameters())
        ).body<JsonObject>().let { TokenResponse.fromJSON(it) }
        tokenResp.isSuccess shouldBe true
        tokenResp.accessToken shouldNotBe null
        tokenResp.cNonce shouldNotBe null

        // receive credential
        ciTestProvider.deferIssuance = false
        var nonce = tokenResp.cNonce!!

        val credReq = CredentialRequest.forOfferedCredential(
            offeredCredential,
            credentialWallet.generateDidProof(credentialWallet.TEST_DID, ciTestProvider.baseUrl, nonce)
        )

        val credentialResp = ktorClient.post(providerMetadata.credentialEndpoint!!) {
            contentType(ContentType.Application.Json)
            bearerAuth(tokenResp.accessToken!!)
            setBody(credReq.toJSON())
        }.body<JsonObject>().let { CredentialResponse.fromJSON(it) }

        credentialResp.isSuccess shouldBe true
        credentialResp.isDeferred shouldBe false
        credentialResp.format!! shouldBe CredentialFormat.jwt_vc_json
        credentialResp.credential.shouldBeInstanceOf<JsonPrimitive>()

        // parse and verify credential
        val credential = VerifiableCredential.fromString(credentialResp.credential!!.jsonPrimitive.content)
        println("Issued credential: $credential")
        credential.verfier?.id shouldBe ciTestProvider.CI_VERIFIER_DID
        credential.credentialSubject?.id shouldBe credentialWallet.TEST_DID
        Auditor.getService().verify(credential, listOf(SignaturePolicy())).result shouldBe true
    }

     */

}
