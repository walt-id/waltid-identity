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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.random.Random

object TrustedRegistryService {
  fun getAuthorisationUri(ebsiEnvironment: EbsiEnvironment, authApiVersion: Int = 3) = "https://api-$ebsiEnvironment.ebsi.eu/authorisation/v$authApiVersion"
  fun getDidRegistryUri(ebsiEnvironment: EbsiEnvironment, registryApiVersion: Int = 4) = "https://api-$ebsiEnvironment.ebsi.eu/did-registry/v$registryApiVersion"

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

  suspend fun executeRPCRequest(rpcRequest: JsonRpcRequest, accessToken: String, ebsiEnvironment: EbsiEnvironment, registryApiVersion: Int = 4): String {
    val response = http.post("${getDidRegistryUri(ebsiEnvironment, registryApiVersion)}/jsonrpc") {
      bearerAuth(accessToken)
      contentType(ContentType.Application.Json)
      setBody(Json.encodeToJsonElement(rpcRequest).also {
        println(it)
      })
    }
    return response.bodyAsText()
  }

  suspend fun signAndExecuteRPCRequest(rpcRequest: JsonRpcRequest, capabilityInvocationKey: Key, accessToken: String, ebsiEnvironment: EbsiEnvironment, registryApiVersion: Int = 4): String {
    val unsignedTransaction = executeRPCRequest(
      rpcRequest, accessToken, ebsiEnvironment, registryApiVersion).let { Json.decodeFromString<UnsignedTransactionResponse>(it).result }
    val signedTransaction = unsignedTransaction.sign(capabilityInvocationKey)
    return executeRPCRequest(
      EbsiRpcRequests.generateSendSignedTransactionRequest(rpcRequest.id, unsignedTransaction, signedTransaction), accessToken, ebsiEnvironment, registryApiVersion)
  }
}
