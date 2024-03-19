package id.walt.oid4vc.requests

import id.walt.oid4vc.util.httpGet
import id.walt.oid4vc.util.sha256
import id.walt.sdjwt.SDJwt
import io.ktor.utils.io.core.*
import kotlinx.datetime.Clock
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

data class EntraIssuanceRequest(
  val authorizationRequest: AuthorizationRequest,
  val manifest: JsonObject,
  val contract: String
) {
  val issuerReturnAddress
    get() = manifest["input"]!!.jsonObject["credentialIssuer"]!!.jsonPrimitive.content

  @OptIn(ExperimentalEncodingApi::class)
  private fun getHashedPin(pin: String): String? {
    return (authorizationRequest.customParameters["pin"]?.firstOrNull()?.let {
      Json.parseToJsonElement(it)
    }?.jsonObject?.get("salt")?.jsonPrimitive?.content)?.let {
      Base64.encode(sha256((it + pin).toByteArray()))
    }
  }

  fun getResponseObject(keyThumbprint: String, did: String, pubKeyJwk: String, pin: String? = null): JsonObject {
    return buildJsonObject {
      put("sub", keyThumbprint) // key thumbprint
      put("aud", issuerReturnAddress)
      put("did", did) // holder DID
      pin?.let { put("pin", getHashedPin(it)) }
      put("sub_jwk", Json.parseToJsonElement(pubKeyJwk))
      Clock.System.now().epochSeconds.let {
        put("iat", it)
        put("exp", it + 3600)
      }
      put("jti", UUID.generateUUID().toString())
      put("attestations", buildJsonObject {
        // * Get id_token_hint, if any, to add to response, or else generate id_token/input claims according to alternative attestation mode
        // Attestation modes: https://learn.microsoft.com/en-us/entra/verified-id/rules-and-display-definitions-model
        put("idTokens", buildJsonObject {
          put("https://self-issued.me", authorizationRequest.idTokenHint)
        })
      })
      put("iss", "https://self-issued.me")
      put("contract", contract)
    }
  }
  companion object {
    const val ISSUANCE_REQUEST_URI_PREFIX = "openid-vc://"
    suspend fun fromAuthorizationRequest(authorizationRequest: AuthorizationRequest): EntraIssuanceRequest {
      val manifestUrl = authorizationRequest.claims!!["vp_token"]!!.jsonObject["presentation_definition"]!!.jsonObject["input_descriptors"]!!.jsonArray.first().jsonObject["issuance"]!!.jsonArray.first().jsonObject["manifest"]!!.jsonPrimitive.content
      val manifest = Json.parseToJsonElement(httpGet(manifestUrl)).jsonObject["token"]!!.jsonPrimitive.content.let {
        SDJwt.parse(it).fullPayload
      }
      return EntraIssuanceRequest(authorizationRequest, manifest, manifestUrl)
    }

    fun isEntraIssuanceRequestUri(requestUri: String): Boolean {
      return requestUri.startsWith(ISSUANCE_REQUEST_URI_PREFIX)
    }
  }
}
