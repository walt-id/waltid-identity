package id.walt.did.dids.registrar.local.ebsi

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidEbsiBaseDocument
import id.walt.did.dids.document.DidEbsiDocument
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import id.walt.did.utils.randomUUID
import id.walt.ebsi.did.DidEbsiService
import id.walt.ebsi.eth.TransactionService
import id.walt.ebsi.registry.TrustedRegistryScope
import id.walt.ebsi.registry.TrustedRegistryService
import id.walt.ebsi.rpc.EbsiRpcRequests
import id.walt.ebsi.rpc.UnsignedTransactionResponse
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.GrantType
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.util.PresentationBuilder
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import kotlinx.serialization.json.*
import kotlinx.uuid.SecureRandom
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import kotlinx.uuid.randomUUID
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
  override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult {
    val didRegistryVersion = options.get<Int>("version") ?: throw IllegalArgumentException("Option \"version\" not found.")
    val authVersion = didRegistryVersion - 1
    val authorisationToOnboard = options.get<String>("authorisationToOnboard") ?: throw IllegalArgumentException("Option \"authorisationToOnboardVP\" not found.")
    //val vcSigningKey = options.get<Key>("vcSigningKey") ?: throw IllegalArgumentException("Option \"vcSigningKey\" not found.")
    val nonce = options.get<String>("nonce") ?: throw IllegalArgumentException("Option \"nonce\" not found.")
    val presDef = TrustedRegistryService.getPresentationDefinition(TrustedRegistryScope.didr_invite, authVersion)
    val notBefore = options.get<Instant>("notBefore") ?: Clock.System.now()
    val notAfter = options.get<Instant>("notAfter") ?: notBefore.plus(365*24, DateTimeUnit.HOUR)
    val did = DidEbsiService.generateRandomDid()
    val tokenResponse = OpenID4VP.generatePresentationResponse(
      PresentationResult(listOf(JsonPrimitive(
        PresentationBuilder().also {
          it.did = did
          it.addCredential(Json.parseToJsonElement(authorisationToOnboard))
          it.nonce = nonce
          it.audience = "https://api-conformance.ebsi.eu/authorisation/v4"
          it.jwtExpiration = Clock.System.now().plus(1.toDuration(DurationUnit.MINUTES))
        }.buildPresentationJsonString().let {
          key.signJws(
            it.encodeToByteArray(),
            headers = mapOf("kid" to "$did#${key.getKeyId()}", "typ" to "JWT")
          )
        }
      )), PresentationSubmission(
        presDef.id, presDef.id, presDef.inputDescriptors.mapIndexed { index, inputDescriptor ->
          DescriptorMapping(inputDescriptor.id, presDef.format!!.keys.first(), DescriptorMapping.vpPath(1,0),
            null) //DescriptorMapping("0", inputDescriptor.format!!.keys.first(), "${DescriptorMapping.vpPath(1,0)}.verifiableCredential[$index]"))
        }
      ).also { println(it.toJSONString()) }), grantType = GrantType.vp_token, scope = "openid didr_invite"
    )
    println(tokenResponse.vpToken.toString())
    val vp = OpenID4VP.generatePresentationResponse(
      PresentationResult(listOf(tokenResponse.vpToken!!), PresentationSubmission(
        presDef.id, presDef.id, presDef.inputDescriptors.mapIndexed { index, inputDescriptor ->
          DescriptorMapping(inputDescriptor.id, presDef.format!!.keys.first(), DescriptorMapping.vpPath(1,0),
            null)
        }
      ).also { println(it.toJSONString()) }), grantType = GrantType.vp_token, scope = "openid ${TrustedRegistryScope.didr_invite.name}"
    )
    val accessToken = TrustedRegistryService.getAccessToken(TrustedRegistryScope.didr_write, vp, authVersion)
    val id = SecureRandom.nextInt()
    val unsignedTransaction = TrustedRegistryService.executeRPCRequest(
      EbsiRpcRequests.generateInsertDidDocumentRequest(id, did, key,
        DidEbsiBaseDocument().let { Json.encodeToJsonElement(it) }, notBefore, notAfter
      ), accessToken, didRegistryVersion).let { Json.decodeFromString<UnsignedTransactionResponse>(it).result }
    val signedTransaction = unsignedTransaction.sign(key)
    val result = TrustedRegistryService.executeRPCRequest(
      EbsiRpcRequests.generateSendSignedTransactionRequest(id, unsignedTransaction, signedTransaction), accessToken, didRegistryVersion)
    val vmId = "$did#${key.getKeyId()}"
    return DidResult(did, DidEbsiDocument(
      DidEbsiDocument.DEFAULT_CONTEXT, did,
      listOf(DidEbsiDocument.VerificationMethod(vmId, "JsonWebKey2020", did, key.getPublicKey().exportJWKObject())),
      assertionMethod = listOf(), authentication = listOf(vmId), capabilityInvocation = listOf(vmId), capabilityDelegation = listOf(), keyAgreement = listOf()
    ).toMap().let { DidDocument(it) })
  }

}