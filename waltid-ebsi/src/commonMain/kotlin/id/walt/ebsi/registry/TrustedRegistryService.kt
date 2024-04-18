package id.walt.ebsi.registry

import id.walt.ebsi.eth.SignedTransaction
import id.walt.ebsi.eth.UnsignedTransaction
import id.walt.ebsi.rpc.JsonRpcRequest
import id.walt.ebsi.rpc.SignedTransactionResponse
import id.walt.ebsi.rpc.UnsignedTransactionResponse
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.responses.TokenResponse
import id.walt.oid4vc.util.http
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

object TrustedRegistryService {
  suspend fun getPresentationDefinition(scope: TrustedRegistryScope, authApiVersion: Int = 4): PresentationDefinition {
    val presDefResp = http.get("https://api-conformance.ebsi.eu/authorisation/v$authApiVersion/presentation-definitions") {
      parameter("scope", "openid ${scope.name}")
    }
    return PresentationDefinition.fromJSONString(presDefResp.bodyAsText())
  }

  suspend fun getAccessToken(scope: TrustedRegistryScope, vp: TokenResponse, authApiVersion: Int = 4): String {
    val accessTokenResponse = http.submitForm("https://api-conformance.ebsi.eu/authorisation/v$authApiVersion/token",
      formParameters = parametersOf(vp.toHttpParameters())
    ) {}.bodyAsText().let { TokenResponse.fromJSONString(it) }
    return accessTokenResponse.accessToken ?: throw Exception("No access_token received")
  }

  suspend fun executeRPCRequest(rpcRequest: JsonRpcRequest, accessToken: String, registryApiVersion: Int = 5): String {
    val response = http.post("https://api-conformance.ebsi.eu/did-registry/v$registryApiVersion/jsonrpc") {
      bearerAuth(accessToken)
      contentType(ContentType.Application.Json)
      setBody(Json.encodeToJsonElement(rpcRequest).also {
        println(it)
      })
    }
    return response.bodyAsText()
  }
}