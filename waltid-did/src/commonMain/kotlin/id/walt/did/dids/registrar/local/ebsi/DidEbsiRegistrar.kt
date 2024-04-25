package id.walt.did.dids.registrar.local.ebsi

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.document.DidEbsiDocument
import id.walt.did.dids.registrar.DidRegistrationException
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import id.walt.ebsi.EbsiEnvironment
import id.walt.ebsi.did.DidEbsiBaseDocument
import id.walt.ebsi.did.DidEbsiService
import id.walt.ebsi.did.DidRegistrationOptions
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import love.forte.plugin.suspendtrans.annotation.JsPromise
import love.forte.plugin.suspendtrans.annotation.JvmAsync
import love.forte.plugin.suspendtrans.annotation.JvmBlocking
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

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

    val did = DidEbsiService.generateAndRegisterDid(mainKey, vcSigningKey, DidRegistrationOptions(
        options.get<String>("accreditation_client_uri") ?: throw DidRegistrationException("Option \"accreditation_client_uri\" not found."),
      options.get<String>("tao_issuer_uri") ?: throw DidRegistrationException("Option \"tao_issuer_uri\" not found."),
      options.get<String>("client_jwks_uri")!!, options.get<String>("client_redirect_uris")!!, options.get<String>("client_id")!!,
      ebsiEnvironment, didRegistryVersion, notBefore, notAfter
        ))

    val vmId = "$did#${mainKey.getKeyId()}"
    return DidResult(did, DidEbsiDocument(
      DidEbsiBaseDocument.DEFAULT_CONTEXT, did, setOf(did),
      listOf(DidEbsiDocument.VerificationMethod(vmId, "JsonWebKey2020", did, mainKey.getPublicKey().exportJWKObject())),
      assertionMethod = listOf(), authentication = listOf(vmId), capabilityInvocation = listOf(vmId), capabilityDelegation = listOf(), keyAgreement = listOf()
    ).toMap().let { DidDocument(it) })
  }
}
