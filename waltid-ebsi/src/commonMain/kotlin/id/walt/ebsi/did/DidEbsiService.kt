package id.walt.ebsi.did

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.MultiBaseUtils
import id.walt.ebsi.Delay
import id.walt.ebsi.EbsiEnvironment
import id.walt.ebsi.accreditation.AccreditationClient
import id.walt.ebsi.accreditation.AccreditationException
import id.walt.ebsi.eth.Utils
import id.walt.ebsi.registry.TrustedRegistryScope
import id.walt.ebsi.registry.TrustedRegistryService
import id.walt.ebsi.registry.TrustedRegistryType
import id.walt.ebsi.rpc.EbsiRpcRequests
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.util.JwtUtils
import id.walt.oid4vc.util.PresentationBuilder
import id.walt.oid4vc.util.randomUUID
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.JsExport
import kotlin.random.Random
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object DidEbsiService {

  /**
   * https://hub.ebsi.eu/vc-framework/did/legal-entities#generation-of-a-method-specific-identifier
   */
  fun generateRandomDid(): String {
    return MultiBaseUtils.encodeMultiBase58Btc(
      Random.nextBytes(17).also { it[0] = 1 }
    ).let {
      "did:ebsi:$it".also {
        println("DID generated: $it")
      }
    }
  }

  suspend fun generateAndRegisterDid(accreditationClient: AccreditationClient, capabilityInvocationKey: Key, vcSigningKey: Key, didRegistrationOptions: DidRegistrationOptions): String {
    if(capabilityInvocationKey.keyType != KeyType.secp256k1 || vcSigningKey.keyType != KeyType.secp256r1) throw DidEbsiRegistrationException("Wrong key type: did:ebsi requires capability invocation key of type secp256k1 and a VC signing key of type secp256r1")

    val did = accreditationClient.did
//    val accreditationClient = AccreditationClient(didRegistrationOptions.clientUri, did, vcSigningKey,
//      trustedIssuer = didRegistrationOptions.taoIssuerUri, didRegistrationOptions.clientJwksUri, didRegistrationOptions.clientRedirectUri, didRegistrationOptions.clientId)
    val authorisationToOnboardResp = accreditationClient.getAuthorisationToOnboard()

    val presDef = TrustedRegistryService.getPresentationDefinition(TrustedRegistryScope.didr_invite, didRegistrationOptions.ebsiEnvironment, didRegistrationOptions.authApiVersion)
    val tokenResponse = OpenID4VP.generatePresentationResponse(
      PresentationResult(listOf(JsonPrimitive(
        PresentationBuilder().also {
          it.did = did
          it.addCredential(authorisationToOnboardResp.credential ?: throw DidEbsiRegistrationException("No VerifiableAuthorisationToOnboard credential received from TAO issuer"))
          it.nonce = randomUUID()
          it.audience = TrustedRegistryService.getAuthorisationUri(didRegistrationOptions.ebsiEnvironment, didRegistrationOptions.authApiVersion)
          it.jwtExpiration = Clock.System.now().plus(1.toDuration(DurationUnit.MINUTES))
        }.buildPresentationJsonString().let {
          capabilityInvocationKey.signJws(
            it.encodeToByteArray(),
            headers = mapOf("kid" to "$did#${capabilityInvocationKey.getKeyId()}", "typ" to "JWT")
          )
        }
      )), PresentationSubmission(
        presDef.id, presDef.id, presDef.inputDescriptors.mapIndexed { index, inputDescriptor ->
          DescriptorMapping(inputDescriptor.id, presDef.format!!.keys.first(), DescriptorMapping.vpPath(1,0),
            null)
        }
      ).also { println(it.toJSONString()) }), grantType = GrantType.vp_token, scope = "openid ${TrustedRegistryScope.didr_invite.name}"
    )
    println(tokenResponse.vpToken.toString())

    val accessToken = TrustedRegistryService.getAccessToken(TrustedRegistryScope.didr_write, tokenResponse, didRegistrationOptions.ebsiEnvironment, didRegistrationOptions.authApiVersion)
    val id = Random.nextInt()
    val rpcResult = TrustedRegistryService.signAndExecuteRPCRequest(
      EbsiRpcRequests.generateInsertDidDocumentRequest(id, did, Utils.toEthereumAddress(capabilityInvocationKey), capabilityInvocationKey,
        DidEbsiBaseDocument().let { Json.encodeToJsonElement(it) }, didRegistrationOptions.notBefore, didRegistrationOptions.notAfter
      ), capabilityInvocationKey, accessToken, null, TrustedRegistryType.didr, didRegistrationOptions.ebsiEnvironment, didRegistrationOptions.didRegistryVersion)
    println("InsertDidDocument result: $rpcResult")

    addVerificationMethod(did, capabilityInvocationKey, vcSigningKey, setOf("authentication", "assertionMethod"), didRegistrationOptions.ebsiEnvironment, didRegistrationOptions.didRegistryVersion, didRegistrationOptions.authApiVersion)

    return did
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  suspend fun addVerificationMethod(did: String, capabilityInvocationKey: Key, verificationMethodKey: Key, verificationRelationShips: Set<String>, ebsiEnvironment: EbsiEnvironment, didRegistryVersion: Int, authApiVersion: Int) {
    // https://hub.ebsi.eu/get-started/build/how-tos/didr
    // #### Get didr_write Access Token ####
    val presDef = TrustedRegistryService.getPresentationDefinition(TrustedRegistryScope.didr_write, ebsiEnvironment, authApiVersion)
    val tokenResponse = OpenID4VP.generatePresentationResponse(
      PresentationResult(listOf(JsonPrimitive(
        PresentationBuilder().also {
          it.did = did
          it.nonce = randomUUID()
          it.audience = TrustedRegistryService.getAuthorisationUri(ebsiEnvironment, authApiVersion)
          it.jwtExpiration = Clock.System.now().plus(1.toDuration(DurationUnit.MINUTES))
        }.buildPresentationJsonString().let {
          capabilityInvocationKey.signJws(
            it.encodeToByteArray(),
            headers = mapOf("kid" to "$did#${capabilityInvocationKey.getKeyId()}", "typ" to "JWT")
          )
        }
      )), PresentationSubmission(
        presDef.id, presDef.id, presDef.inputDescriptors.mapIndexed { index, inputDescriptor ->
          DescriptorMapping(inputDescriptor.id, presDef.format!!.keys.first(), DescriptorMapping.vpPath(1,0),
            null)
        }
      ).also { println(it.toJSONString()) }), grantType = GrantType.vp_token, scope = "openid ${TrustedRegistryScope.didr_write.name}"
    )
    println(tokenResponse.vpToken.toString())

    waitForDidRegistration(did, ebsiEnvironment, didRegistryVersion)
    val accessToken = TrustedRegistryService.getAccessToken(TrustedRegistryScope.didr_write, tokenResponse, ebsiEnvironment, authApiVersion)

    // #### Add verification method and verification relationships to DID document ####
    // --- add verification method ---
    var txResult = TrustedRegistryService.signAndExecuteRPCRequest(
      EbsiRpcRequests.generateAddVerificationMethodRequest(Random.nextInt(), did, Utils.toEthereumAddress(capabilityInvocationKey), verificationMethodKey),
      capabilityInvocationKey, accessToken, null, TrustedRegistryType.didr, ebsiEnvironment, didRegistryVersion)
    println("Add-VM result: ${txResult.result}")

    // --- add verification relationships ---
    for (relationShip in verificationRelationShips) {
      txResult = TrustedRegistryService.signAndExecuteRPCRequest(
        EbsiRpcRequests.generateAddVerificationRelationshipRequest(Random.nextInt(), did, Utils.toEthereumAddress(capabilityInvocationKey), relationShip, verificationMethodKey.getKeyId()),
        capabilityInvocationKey, accessToken, txResult, TrustedRegistryType.didr, ebsiEnvironment, didRegistryVersion)
      println("Add-$relationShip relationship result: ${txResult.result}")
    }
  }

  private suspend fun waitForDidRegistration(did: String, ebsiEnvironment: EbsiEnvironment, didRegistryVersion: Int) {
    while(!kotlin.runCatching { resolveDid(did) }.isSuccess.also { println("Did registration succeeded: ${it}") }) {
      println("Waiting for DID registration...")
      Delay.delay(1000)
    }
  }

  suspend fun resolveDid(did: String, ebsiEnvironment: EbsiEnvironment = EbsiEnvironment.conformance, didRegistryApiVersion: Int = 5): JsonObject {
    val url = "https://api-${ebsiEnvironment.name}.ebsi.eu/did-registry/v${didRegistryApiVersion}/identifiers/${did}"

    val httpClient = HttpClient() {
      install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
      }
    }

    val httpResp = httpClient.get(url)
    if(httpResp.status != HttpStatusCode.OK) { throw DidEbsiResolutionException("Received HTTP status ${httpResp.status}") }
    return httpResp.body<JsonObject>()
  }

  @OptIn(ExperimentalStdlibApi::class)
  suspend fun getTrustedIssuerAccreditation(accreditationClient: AccreditationClient, capabilityInvocationKey: Key, vcSigningKey: Key, didRegistrationOptions: DidRegistrationOptions) {
    val accreditationToAttestResp = accreditationClient.getAccreditationToAttest()
    val presDef = TrustedRegistryService.getPresentationDefinition(TrustedRegistryScope.tir_invite, didRegistrationOptions.ebsiEnvironment, didRegistrationOptions.authApiVersion)
    val tokenResponse = OpenID4VP.generatePresentationResponse(
      PresentationResult(listOf(JsonPrimitive(
        PresentationBuilder().also {
          it.did = accreditationClient.did
          it.addCredential(accreditationToAttestResp.credential ?: throw AccreditationException("No VerifiableAccreditationToAttest credential received from TAO issuer"))
          it.nonce = randomUUID()
          it.audience = TrustedRegistryService.getAuthorisationUri(didRegistrationOptions.ebsiEnvironment, didRegistrationOptions.authApiVersion)
          it.jwtExpiration = Clock.System.now().plus(1.toDuration(DurationUnit.MINUTES))
        }.buildPresentationJsonString().let {
          vcSigningKey.signJws(
            it.encodeToByteArray(),
            headers = mapOf("kid" to "${accreditationClient.did}#${vcSigningKey.getKeyId()}", "typ" to "JWT")
          )
        }
      )), PresentationSubmission(
        presDef.id, presDef.id, presDef.inputDescriptors.mapIndexed { index, inputDescriptor ->
          DescriptorMapping(inputDescriptor.id, presDef.format!!.keys.first(), DescriptorMapping.vpPath(1,0),
            null)
        }
      ).also { println(it.toJSONString()) }), grantType = GrantType.vp_token, scope = "openid ${TrustedRegistryScope.tir_invite.name}"
    )
    println("VP token: ${tokenResponse.vpToken.toString()}")

    val accessToken = TrustedRegistryService.getAccessToken(TrustedRegistryScope.tir_invite, tokenResponse, didRegistrationOptions.ebsiEnvironment, didRegistrationOptions.authApiVersion)
    println("Access token: $accessToken")

    val txResult = TrustedRegistryService.signAndExecuteRPCRequest(
      EbsiRpcRequests.generateTirSetAttributeDataRequest(Random.nextInt(),
        Utils.toEthereumAddress(capabilityInvocationKey), accreditationClient.did,
        getReservedAttributeId(accreditationToAttestResp.credential!!) ?: throw AccreditationException("No reservedAttributeId found in VerifiableAccreditationToAttest"),
        accreditationToAttestResp.credential!!.jsonPrimitive.content.toByteArray().toHexString().let { "0x$it" }
      ), capabilityInvocationKey, accessToken, null, TrustedRegistryType.tir, didRegistrationOptions.ebsiEnvironment, didRegistrationOptions.didRegistryVersion
    )
    println("TIR registration result: ${txResult.result}")
  }

  private fun getReservedAttributeId(credential: JsonElement): String? {
    return JwtUtils.parseJWTPayload(credential.jsonPrimitive.content).get("vc")?.jsonObject?.get("credentialSubject")?.jsonObject?.get("reservedAttributeId")?.jsonPrimitive?.content
  }
}
