import COSE.AlgorithmID
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.util.Base64URL
import id.walt.crypto.keys.KeyGenerationRequest
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
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
import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SimpleJWTCryptoProvider
import id.walt.verifier.lspPotential.LspPotentialVerificationInterop
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LspPotentialVerification(private val client: HttpClient) {

  fun testPotentialInteropTrack3() {
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
            put("request_credentials", JsonArray(listOf(JsonPrimitive(MDocTypes.ISO_MDL))))
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
        LspPotentialVerificationInterop.POTENTIAL_ISSUER_CRYPTO_PROVIDER_INFO,
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
          issuerKeyID = LspPotentialVerificationInterop.POTENTIAL_ISSUER_KEY_ID, deviceKeyID = holderKey.getKeyId(),
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

  fun testPotentialInteropTrack4() {
//    val testVC = "eyJhbGciOiAiRVMyNTYiLCAidHlwIjogInZjK3NkLWp3dCIsICJraWQiOiAiZG9jLXNp" +
//        "Z25lci0wNS0yNS0yMDIyIn0.eyJfc2QiOiBbIjA5dktySk1PbHlUV00wc2pwdV9wZE9C" +
//        "VkJRMk0xeTNLaHBINTE1blhrcFkiLCAiMnJzakdiYUMwa3k4bVQwcEpyUGlvV1RxMF9k" +
//        "YXcxc1g3NnBvVWxnQ3diSSIsICJFa084ZGhXMGRIRUpidlVIbEVfVkNldUM5dVJFTE9p" +
//        "ZUxaaGg3WGJVVHRBIiwgIklsRHpJS2VpWmREd3BxcEs2WmZieXBoRnZ6NUZnbldhLXNO" +
//        "NndxUVhDaXciLCAiSnpZakg0c3ZsaUgwUjNQeUVNZmVadTZKdDY5dTVxZWhabzdGN0VQ" +
//        "WWxTRSIsICJQb3JGYnBLdVZ1Nnh5bUphZ3ZrRnNGWEFiUm9jMkpHbEFVQTJCQTRvN2NJ" +
//        "IiwgIlRHZjRvTGJnd2Q1SlFhSHlLVlFaVTlVZEdFMHc1cnREc3JaemZVYW9tTG8iLCAi" +
//        "amRyVEU4WWNiWTRFaWZ1Z2loaUFlX0JQZWt4SlFaSUNlaVVRd1k5UXF4SSIsICJqc3U5" +
//        "eVZ1bHdRUWxoRmxNXzNKbHpNYVNGemdsaFFHMERwZmF5UXdMVUs0Il0sICJpc3MiOiAi" +
//        "aHR0cHM6Ly9leGFtcGxlLmNvbS9pc3N1ZXIiLCAiaWF0IjogMTY4MzAwMDAwMCwgImV4" +
//        "cCI6IDE4ODMwMDAwMDAsICJ2Y3QiOiAiaHR0cHM6Ly9jcmVkZW50aWFscy5leGFtcGxl" +
//        "LmNvbS9pZGVudGl0eV9jcmVkZW50aWFsIiwgIl9zZF9hbGciOiAic2hhLTI1NiIsICJj" +
//        "bmYiOiB7Imp3ayI6IHsia3R5IjogIkVDIiwgImNydiI6ICJQLTI1NiIsICJ4IjogIlRD" +
//        "QUVSMTladnUzT0hGNGo0VzR2ZlNWb0hJUDFJTGlsRGxzN3ZDZUdlbWMiLCAieSI6ICJa" +
//        "eGppV1diWk1RR0hWV0tWUTRoYlNJaXJzVmZ1ZWNDRTZ0NGpUOUYySFpRIn19fQ.D43eE" +
//        "W1ae2yAzhzriJuBz-_cgX1wwNJIgNMjsdO28QE0fU8KC8ugjTPaylIp48HMVS0xV2wDQ" +
//        "9bl1zFzlbDULg~WyIyR0xDNDJzS1F2ZUNmR2ZyeU5STjl3IiwgImdpdmVuX25hbWUiLC" +
//        "AiSm9obiJd~WyJlbHVWNU9nM2dTTklJOEVZbnN4QV9BIiwgImZhbWlseV9uYW1lIiwgI" +
//        "kRvZSJd~WyI2SWo3dE0tYTVpVlBHYm9TNXRtdlZBIiwgImVtYWlsIiwgImpvaG5kb2VA" +
//        "ZXhhbXBsZS5jb20iXQ~WyJlSThaV205UW5LUHBOUGVOZW5IZGhRIiwgInBob25lX251b" +
//        "WJlciIsICIrMS0yMDItNTU1LTAxMDEiXQ~WyJRZ19PNjR6cUF4ZTQxMmExMDhpcm9BIi" +
//        "wgImFkZHJlc3MiLCB7InN0cmVldF9hZGRyZXNzIjogIjEyMyBNYWluIFN0IiwgImxvY2" +
//        "FsaXR5IjogIkFueXRvd24iLCAicmVnaW9uIjogIkFueXN0YXRlIiwgImNvdW50cnkiOi" +
//        "AiVVMifV0~WyJBSngtMDk1VlBycFR0TjRRTU9xUk9BIiwgImJpcnRoZGF0ZSIsICIxOT" +
//        "QwLTAxLTAxIl0~WyJQYzMzSk0yTGNoY1VfbEhnZ3ZfdWZRIiwgImlzX292ZXJfMTgiLC" +
//        "B0cnVlXQ~WyJHMDJOU3JRZmpGWFE3SW8wOXN5YWpBIiwgImlzX292ZXJfMjEiLCB0cnV" +
//        "lXQ~WyJsa2x4RjVqTVlsR1RQVW92TU5JdkNBIiwgImlzX292ZXJfNjUiLCB0cnVlXQ~"

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
      assertEquals(LspPotentialVerificationInterop.POTENTIAL_ISSUER_KEY_ID, sdJwtVc.issuer)

      // 3. make presentation request (verifier)
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
      assertTrue(presReqUrl.startsWith("haip://"))
      val presReq = AuthorizationRequest.fromHttpParametersAuto(parseQueryString(Url(presReqUrl).encodedQuery).toMap())
      assertNotNull(presReq.presentationDefinition)
      assertNotNull(presReq.responseUri)
      assertEquals(VCFormat.sd_jwt_vc, presReq.presentationDefinition!!.inputDescriptors.firstOrNull()?.format?.keys?.first())
      assertEquals("urn:eu.europa.ec.eudi:pid:1", presReq.presentationDefinition!!.inputDescriptors.flatMap { it.constraints!!.fields!! }.first { it.path.contains("$.vct") }.filter?.get("const")?.jsonPrimitive?.content)

      // 4. present (wallet)
      val vp_token = sdJwtVc.present(true, presReq.clientId, presReq.nonce!!, SimpleJWTCryptoProvider(
        JWSAlgorithm.ES256, ECDSASigner(ECKey.parse(holderKey.exportJWK())), null
      )).toString()

      println(vp_token)

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
