package id.walt.did.dids.registrar.local.ebsi

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.registrar.DidResult
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.local.LocalRegistrarMethod
import id.walt.ebsi.did.DidEbsiService
import id.walt.ebsi.registry.TrustedRegistryScope
import id.walt.ebsi.registry.TrustedRegistryService
import kotlinx.serialization.json.JsonElement
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
  override suspend fun registerByKey(key: Key, options: DidCreateOptions): DidResult {
    val didRegistryVersion = options.get<Int>("version") ?: throw IllegalArgumentException("Option \"version\" not found.")
    val vAuthToOnboard = options.get<JsonElement>("verifiableAuthorisationToOnboard") ?: throw IllegalArgumentException("Option \"verifiableAuthorisationToOnboard\" not found.")
    val authVersion = didRegistryVersion - 1
    val did = DidEbsiService.generateRandomDid()
    TrustedRegistryService.fillTrustedRegistry(TrustedRegistryScope.didr_write, vAuthToOnboard)
    TODO()
  }

}