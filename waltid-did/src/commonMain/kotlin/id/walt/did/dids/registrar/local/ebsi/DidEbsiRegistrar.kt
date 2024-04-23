package id.walt.did.dids.registrar.local.ebsi

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidEbsiBaseDocument
import id.walt.did.dids.document.DidEbsiDocument
import id.walt.did.dids.registrar.DidRegistrationException
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import id.walt.ebsi.EbsiEnvironment
import id.walt.ebsi.accreditation.AccreditationClient
import id.walt.ebsi.did.DidEbsiService
import id.walt.ebsi.eth.Utils
import id.walt.ebsi.registry.TrustedRegistryScope
import id.walt.ebsi.registry.TrustedRegistryService
import id.walt.ebsi.rpc.EbsiRpcRequests
import id.walt.ebsi.rpc.UnsignedTransactionResponse
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.util.PresentationBuilder
import id.walt.oid4vc.util.randomUUID
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import kotlinx.serialization.json.*
import kotlinx.uuid.SecureRandom
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalJsExport::class)
@JsExport
class DidEbsiRegistrar : LocalRegistrarMethod("ebsi") {

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun register(options: DidCreateOptions) = registerByKey(
    JWKKey.generate(KeyType.secp256k1), options
  )

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  override suspend fun registerByKey(mainKey: Key, options: DidCreateOptions, vcSigningKey: Key): DidResult {
    val didRegistryVersion = options.get<Int>("did_registry_api_version") ?: throw DidRegistrationException("Option \"did_registry_api_version\" not found.")
    val authApiVersion = didRegistryVersion - 1
    if(mainKey.keyType != KeyType.secp256k1 || vcSigningKey.keyType != KeyType.secp256r1) throw DidRegistrationException("Wrong key type: did:ebsi requires main key of type secp256k1 and a VC signing key of type secp256r1")
    val notBefore = options.get<Instant>("not_before") ?: Clock.System.now()
    val notAfter = options.get<Instant>("not_after") ?: notBefore.plus(365*24, DateTimeUnit.HOUR)
    val ebsiEnvironment = options.get<String>("ebsi_environment")?.let { EbsiEnvironment.valueOf(it) } ?: throw DidRegistrationException("EbsiEnvironment not set.")

    val did = DidEbsiService.generateRandomDid()
    val accreditationClient = AccreditationClient(options.get<String>("accreditation_client_uri") ?: throw DidRegistrationException("Option \"accreditation_client_uri\" not found."),
      did, vcSigningKey, trustedIssuer = options.get<String>("tao_issuer_uri") ?: throw DidRegistrationException("Option \"tao_issuer_uri\" not found."),
      options.get<String>("client_jwks_uri")!!, options.get<String>("client_redirect_uris")!!, options.get<String>("client_id")!!)
    val presDef = TrustedRegistryService.getPresentationDefinition(TrustedRegistryScope.didr_invite, ebsiEnvironment, authApiVersion)
    val authorisationToOnboardResp = accreditationClient.getAuthorisationToOnboard()

    val tokenResponse = OpenID4VP.generatePresentationResponse(
      PresentationResult(listOf(JsonPrimitive(
        PresentationBuilder().also {
          it.did = did
          it.addCredential(authorisationToOnboardResp.credential ?: throw DidRegistrationException("No VerifiableAuthorisationToOnboard credential received from TAO issuer"))
          it.nonce = randomUUID()
          it.audience = TrustedRegistryService.getAuthorisationUri(ebsiEnvironment, authApiVersion)
          it.jwtExpiration = Clock.System.now().plus(1.toDuration(DurationUnit.MINUTES))
        }.buildPresentationJsonString().let {
          mainKey.signJws(
            it.encodeToByteArray(),
            headers = mapOf("kid" to "$did#${mainKey.getKeyId()}", "typ" to "JWT")
          )
        }
      )), PresentationSubmission(
        presDef.id, presDef.id, presDef.inputDescriptors.mapIndexed { index, inputDescriptor ->
          DescriptorMapping(inputDescriptor.id, presDef.format!!.keys.first(), DescriptorMapping.vpPath(1,0),
            null) //DescriptorMapping("0", inputDescriptor.format!!.keys.first(), "${DescriptorMapping.vpPath(1,0)}.verifiableCredential[$index]"))
        }
      ).also { println(it.toJSONString()) }), grantType = GrantType.vp_token, scope = "openid ${TrustedRegistryScope.didr_invite.name}"
    )
    println(tokenResponse.vpToken.toString())

    val accessToken = TrustedRegistryService.getAccessToken(TrustedRegistryScope.didr_write, tokenResponse, ebsiEnvironment, authApiVersion)
    val id = SecureRandom.nextInt()
    val rpcResult = TrustedRegistryService.signAndExecuteRPCRequest(
      EbsiRpcRequests.generateInsertDidDocumentRequest(id, did, Utils.toEthereumAddress(mainKey), mainKey,
        DidEbsiBaseDocument().let { Json.encodeToJsonElement(it) }, notBefore, notAfter
      ), mainKey, accessToken, ebsiEnvironment, didRegistryVersion)
    println("InsertDidDocument result: $rpcResult")

    addVerificationMethod(mainKey, did, vcSigningKey, setOf("authentication", "assertionMethod"), ebsiEnvironment, didRegistryVersion, authApiVersion)

    val vmId = "$did#${mainKey.getKeyId()}"
    return DidResult(did, DidEbsiDocument(
      DidEbsiDocument.DEFAULT_CONTEXT, did, setOf(did),
      listOf(DidEbsiDocument.VerificationMethod(vmId, "JsonWebKey2020", did, mainKey.getPublicKey().exportJWKObject())),
      assertionMethod = listOf(), authentication = listOf(vmId), capabilityInvocation = listOf(vmId), capabilityDelegation = listOf(), keyAgreement = listOf()
    ).toMap().let { DidDocument(it) })
  }

  @JvmBlocking
  @JvmAsync
  @JsPromise
  @JsExport.Ignore
  protected suspend fun addVerificationMethod(capabilityInvocationKey: Key, did: String, verificationMethodKey: Key, verificationRelationShips: Set<String>, ebsiEnvironment: EbsiEnvironment, didRegistryVersion: Int, authApiVersion: Int) {
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
      ).also { println(it.toJSONString()) }), grantType = GrantType.vp_token, scope = "openid ${TrustedRegistryScope.didr_invite.name}"
    )
    println(tokenResponse.vpToken.toString())

    val accessToken = TrustedRegistryService.getAccessToken(TrustedRegistryScope.didr_write, tokenResponse, ebsiEnvironment, authApiVersion)

    // #### Add verification method and verification relationships to DID document ####
    val id = SecureRandom.nextInt()
    // --- add verification method ---
    val addVMResult = TrustedRegistryService.signAndExecuteRPCRequest(
      EbsiRpcRequests.generateAddVerificationMethodRequest(id, did, Utils.toEthereumAddress(capabilityInvocationKey), verificationMethodKey),
      capabilityInvocationKey, accessToken, ebsiEnvironment, didRegistryVersion)
    println("Add VM result: $addVMResult")

    // --- add verification relationships ---
    for (relationShip in verificationRelationShips) {
      val addRelResult = TrustedRegistryService.signAndExecuteRPCRequest(
        EbsiRpcRequests.generateAddVerificationRelationshipRequest(id, did, Utils.toEthereumAddress(capabilityInvocationKey), relationShip, "$did#${verificationMethodKey.getKeyId()}"),
        capabilityInvocationKey, accessToken, ebsiEnvironment, didRegistryVersion)
      println("Add $relationShip relationship result: $addRelResult")
    }
  }

}
