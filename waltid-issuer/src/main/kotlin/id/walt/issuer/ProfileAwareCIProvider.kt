package id.walt.issuer

import id.walt.crypto.keys.Key
import id.walt.issuer.base.config.ConfigManager
import id.walt.issuer.base.config.OIDCIssuerServiceConfig
import id.walt.oid4vc.data.CredentialSupported
import id.walt.oid4vc.interfaces.CredentialResult
import id.walt.oid4vc.providers.CredentialIssuerConfig
import id.walt.oid4vc.providers.IssuanceSession
import id.walt.oid4vc.providers.OpenIDCredentialIssuer
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.CredentialRequest
import kotlinx.serialization.json.JsonObject

class CIProviderProfileConfig(
  credentialsSupported: List<CredentialSupported>,
  val issuerDid: String,
  val issuerKey: Key,
  val tokenKey: Key,
): CredentialIssuerConfig(credentialsSupported) {}

open class ProfileAwareCIProvider(val profileConfig: CIProviderProfileConfig)
  : OpenIDCredentialIssuer(ConfigManager.getConfig<OIDCIssuerServiceConfig>().baseUrl, profileConfig) {

  // -------------------------------
  // Simple in-memory session management
  private val authSessions: MutableMap<String, IssuanceSession> = mutableMapOf()
  override fun getSession(id: String): IssuanceSession? {
    println("RETRIEVING CI AUTH SESSION: $id")
    return authSessions[id]
  }

  override fun putSession(id: String, session: IssuanceSession): IssuanceSession? {
    println("SETTING CI AUTH SESSION: $id = $session")
    return authSessions.put(id, session)
  }

  override fun removeSession(id: String): IssuanceSession? {
    println("REMOVING CI AUTH SESSION: $id")
    return authSessions.remove(id)
  }

  override fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject?, keyId: String?): String {
    TODO() //profileConfig.tokenKey.signJws(payload.toString().toByteArray())
  }

  override fun verifyTokenSignature(target: TokenTarget, token: String): Boolean {
    TODO("Not yet implemented")
  }

  override fun generateCredential(credentialRequest: CredentialRequest): CredentialResult {
    TODO("Not yet implemented")
  }

  override fun getDeferredCredential(credentialID: String): CredentialResult {
    TODO("Not yet implemented")
  }
}