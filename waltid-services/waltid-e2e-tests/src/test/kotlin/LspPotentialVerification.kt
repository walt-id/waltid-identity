@file:OptIn(ExperimentalUuidApi::class)

import COSE.AlgorithmID
import COSE.OneKey
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.Base64URL
import id.walt.commons.interop.LspPotentialInterop
import id.walt.credentials.utils.VCFormat
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.EncodedCBORElement
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.NullElement
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.doc.MDocTypes
import id.walt.mdoc.doc.MDocVerificationParams
import id.walt.mdoc.doc.VerificationType
import id.walt.mdoc.docrequest.MDocRequestBuilder
import id.walt.mdoc.mdocauth.DeviceAuthentication
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SimpleJWTCryptoProvider
import id.walt.sdjwt.SimpleMultiKeyJWTCryptoProvider
import id.walt.verifier.lspPotential.LspPotentialVerificationInterop
import id.walt.verifier.oidc.RequestedCredential
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*


import java.security.PublicKey
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class LspPotentialVerification(private val client: HttpClient) {

  suspend fun testPotentialInteropTrack3() = E2ETestWebService.test("test track 3") {
     println("Starting test")

    runBlocking {
      // Step 1: Fetch mdoc
      val holderKey = KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1))
      assertEquals(KeyType.secp256r1, holderKey.getPublicKey().keyType)
      assertTrue(holderKey.hasPrivateKey)
      val holderKeyPubJwk = holderKey.getPublicKey().exportJWK()
      val issueResponse = client.submitForm("/lsp-potential/issueMdl", parametersOf(
        "jwk", holderKeyPubJwk
      ))
      assertEquals(200, issueResponse.status.value)
      val mdoc = MDoc.fromCBORHex(issueResponse.bodyAsText())
      assertEquals(MDocTypes.ISO_MDL, mdoc.docType.value)

      // Step 2: Create an openid4vc verification request
      val createReqResponse = client.post("/openid4vc/verify") {
        header("authorizeBaseUrl", "mdoc-openid4vp://")
        header("responseMode", "direct_post_jwt")
        contentType(ContentType.Application.Json)
        setBody(
          buildJsonObject {
            put("request_credentials", JsonArray(listOf(RequestedCredential(format = VCFormat.mso_mdoc, docType = MDocTypes.ISO_MDL).let { Json.encodeToJsonElement(it) })))
            put("trusted_root_cas", JsonArray(listOf(JsonPrimitive(LspPotentialInterop.POTENTIAL_ROOT_CA_CERT))))

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
      val mdocNonce = Uuid.random().toString()
      val mdocHandover = OpenID4VP.generateMDocOID4VPHandover(presReq, mdocNonce)
      val holderKeyNimbus = ECKey.parse(holderKey.exportJWK())
      val deviceCryptoProvider = SimpleCOSECryptoProvider(listOf(
        LspPotentialVerificationInterop.POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO,
        COSECryptoProviderKeyInfo(holderKey.getKeyId(), AlgorithmID.ECDSA_256, holderKeyNimbus.toECPublicKey(),
          holderKeyNimbus.toECPrivateKey())
      ))
      val deviceAuthentication = DeviceAuthentication(sessionTranscript = ListElement(
        listOf(NullElement(), NullElement(), mdocHandover)
      ), mdoc.docType.value, EncodedCBORElement(MapElement(mapOf()))
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
          issuerKeyID = LspPotentialInterop.POTENTIAL_ISSUER_KEY_ID, deviceKeyID = holderKey.getKeyId(),
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
      val presResponse = client.submitForm(presReq.responseUri!!, parametersOf(formParams))
      assertEquals(200, presResponse.status.value)
    }
  }

  suspend fun testPotentialInteropTrack4() = E2ETestWebService.test("test track 4") {
    runBlocking {
      // 1. holder key
      val holderKey = KeyManager.createKey(KeyGenerationRequest(keyType = KeyType.secp256r1))
      assertEquals(KeyType.secp256r1, holderKey.getPublicKey().keyType)
      assertTrue(holderKey.hasPrivateKey)
      val holderKeyPubJwk = holderKey.getPublicKey().exportJWK()

      // 2. issue sd-jwt-vc (issuer)
      val issueResponse = client.submitForm("/lsp-potential/issueSdJwtVC", parametersOf(
        "jwk", holderKeyPubJwk
      ))
      assertEquals(200, issueResponse.status.value)
      val sdJwtVc = SDJwtVC.parse(issueResponse.bodyAsText())
      assertEquals(LspPotentialInterop.POTENTIAL_ISSUER_KEY_ID, sdJwtVc.issuer)

      // 3. make presentation request (verifier)
      val createReqResponse = client.post("/openid4vc/verify") {
        header("authorizeBaseUrl", "haip://")
        header("responseMode", "direct_post")
        contentType(ContentType.Application.Json)
        setBody(
          buildJsonObject {
            put("request_credentials", JsonArray(listOf(RequestedCredential(format = VCFormat.sd_jwt_vc, vct = "identity_credential_vc+sd-jwt").let { Json.encodeToJsonElement(it) })))
            put("vp_policies", JsonArray(listOf(JsonPrimitive("signature_sd-jwt-vc"))))
            put("vc_policies", JsonArray(listOf(JsonPrimitive("not-before"), JsonPrimitive("expired"),
              JsonObject(mapOf(
                "policy" to JsonPrimitive("allowed-issuer"),
                "args" to JsonPrimitive(LspPotentialInterop.POTENTIAL_ISSUER_KEY_ID))))))
          })
      }
      assertEquals(200, createReqResponse.status.value)
      val presReqUrl = createReqResponse.bodyAsText()
      assertTrue(presReqUrl.startsWith("haip://"))
      val presReq = AuthorizationRequest.fromHttpParametersAuto(parseQueryString(Url(presReqUrl).encodedQuery).toMap())
      assertNotNull(presReq.presentationDefinition)
      assertNotNull(presReq.responseUri)
      assertEquals(VCFormat.sd_jwt_vc, presReq.presentationDefinition!!.inputDescriptors.firstOrNull()?.format?.keys?.first())
      assertEquals("identity_credential_vc+sd-jwt", presReq.presentationDefinition!!.inputDescriptors.flatMap { it.constraints!!.fields!! }.first { it.path.contains("$.vct") }.filter?.get("pattern")?.jsonPrimitive?.content)

      val ecHolderKey = ECKey.parse(holderKey.exportJWK())
      val cryptoProvider = SimpleMultiKeyJWTCryptoProvider(mapOf(
        holderKey.getKeyId() to SimpleJWTCryptoProvider(JWSAlgorithm.ES256, ECDSASigner(ecHolderKey), ECDSAVerifier(ecHolderKey)),
        LspPotentialInterop.POTENTIAL_ISSUER_KEY_ID to LspPotentialVerificationInterop.POTENTIAL_JWT_CRYPTO_PROVIDER
      ))
      // 4. present (wallet)
      val vp_token = sdJwtVc.present(true, presReq.clientId, presReq.nonce!!, cryptoProvider, holderKey.getKeyId()).toString()

      println(vp_token)

      assertTrue(SDJwtVC.isSdJwtVCPresentation(vp_token))
      val parseAndVerifyResult = SDJwtVC.parseAndVerify(vp_token, cryptoProvider, false, audience = presReq.clientId, nonce = presReq.nonce)
      assertTrue(parseAndVerifyResult.verified)
      assertTrue(parseAndVerifyResult.sdJwtVC.toString().equals(vp_token))

      val tokenResp = OpenID4VP.generatePresentationResponse(PresentationResult(
        listOf(JsonPrimitive(vp_token)),
        PresentationSubmission("presentation_1", presReq.presentationDefinition!!.id, listOf(
          DescriptorMapping(presReq.presentationDefinition!!.id, VCFormat.sd_jwt_vc, path = "$")
        ))
      ))
      println(tokenResp)

      val httpResp = client.submitForm(presReq.responseUri!!, parametersOf(tokenResp.toHttpParameters()))
      assertEquals(200, httpResp.status.value)
    }
  }
}
