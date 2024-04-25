package id.walt.ebsi.registry

import id.walt.crypto.keys.Key
import id.walt.ebsi.EbsiEnvironment
import id.walt.ebsi.rpc.EbsiRpcRequests
import id.walt.ebsi.rpc.JsonRpcRequest
import id.walt.ebsi.rpc.UnsignedTransactionResponse
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.http
import id.walt.oid4vc.util.randomUUID
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.random.Random

object TrustedRegistryService {
  fun getAuthorisationUri(ebsiEnvironment: EbsiEnvironment, authApiVersion: Int = 3) = "https://api-$ebsiEnvironment.ebsi.eu/authorisation/v$authApiVersion"
  private fun toRegistrySubPath(registry: TrustedRegistryType) = when(registry) {
    TrustedRegistryType.didr -> "did-registry"
    else -> "trusted-issuers-registry"
  }
  fun getTrustedRegistryUri(registry: TrustedRegistryType, ebsiEnvironment: EbsiEnvironment, registryApiVersion: Int = 5) = "https://api-$ebsiEnvironment.ebsi.eu/${toRegistrySubPath(registry)}/v$registryApiVersion"

  suspend fun getPresentationDefinition(scope: TrustedRegistryScope, ebsiEnvironment: EbsiEnvironment = EbsiEnvironment.conformance, authApiVersion: Int = 3): PresentationDefinition {
    val presDefResp = http.get("${getAuthorisationUri(ebsiEnvironment, authApiVersion)}/presentation-definitions") {
      parameter("scope", "openid ${scope.name}")
    }
    return PresentationDefinition.fromJSONString(presDefResp.bodyAsText())
  }

  suspend fun getAccessToken(scope: TrustedRegistryScope, vp: TokenResponse, ebsiEnvironment: EbsiEnvironment = EbsiEnvironment.conformance, authApiVersion: Int = 3): String {
    val accessTokenResponse = http.submitForm("${getAuthorisationUri(ebsiEnvironment, authApiVersion)}/token",
      formParameters = parametersOf(vp.toHttpParameters())
    ) {}.bodyAsText().let { TokenResponse.fromJSONString(it) }
    return accessTokenResponse.accessToken ?: throw Exception("No access_token received: ${accessTokenResponse.error}, ${accessTokenResponse.errorDescription}")
  }

  suspend fun executeRPCRequest(rpcRequest: JsonRpcRequest, accessToken: String, registry: TrustedRegistryType, ebsiEnvironment: EbsiEnvironment, registryApiVersion: Int = 4): String {
    val response = http.post("${getTrustedRegistryUri(registry, ebsiEnvironment, registryApiVersion)}/jsonrpc") {
      bearerAuth(accessToken)
      contentType(ContentType.Application.Json)
      setBody(Json.encodeToJsonElement(rpcRequest).also {
        println(it)
      })
    }
    return response.bodyAsText()
  }

  suspend fun signAndExecuteRPCRequest(rpcRequest: JsonRpcRequest, capabilityInvocationKey: Key, accessToken: String, previousTransactionResult: TransactionResult?, registry: TrustedRegistryType, ebsiEnvironment: EbsiEnvironment, registryApiVersion: Int = 4): TransactionResult {
    val unsignedTransaction = executeRPCRequest(
      rpcRequest, accessToken, registry, ebsiEnvironment, registryApiVersion).let { Json.decodeFromString<UnsignedTransactionResponse>(it).result }
    if(previousTransactionResult != null && unsignedTransaction.nonce == previousTransactionResult.nonce) {
      id.walt.ebsi.Delay.delay(2000)
      return signAndExecuteRPCRequest(rpcRequest, capabilityInvocationKey, accessToken, previousTransactionResult, registry, ebsiEnvironment, registryApiVersion)
    }
    val signedTransaction = unsignedTransaction.sign(capabilityInvocationKey)
    return TransactionResult(
    executeRPCRequest(
      EbsiRpcRequests.generateSendSignedTransactionRequest(Random.nextInt(), unsignedTransaction, signedTransaction), accessToken, registry, ebsiEnvironment, registryApiVersion),
      unsignedTransaction.nonce)
  }
}
