package id.walt.issuer2.config

import id.walt.commons.config.WaltConfig
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.openid4vci.clientauth.ClientAuthenticationConfig
import id.walt.openid4vci.clientauth.attestation.verifier.ClientAttestationVerifierConfig
import kotlinx.coroutines.runBlocking

data class Issuer2ServiceConfig(
    val baseUrl: String,
    val ciTokenKey: String = runBlocking { KeySerialization.serializeKey(JWKKey.generate(KeyType.secp256r1)) },
    val credentialEncryptionKey: String? = null,
    val enforcePushedAuthorizationRequests: Boolean = false,
    val clientAuthenticationConfig: ClientAuthenticationConfig? = null,
) : WaltConfig() {
    init {
        credentialEncryptionKey?.let(CredentialEncryptionKeyConfig::validate)
    }

    fun openId4VciBaseUrl(): String = baseUrl.trimEnd('/') + "/openid4vci"

    fun clientAttestationConfig(): ClientAttestationVerifierConfig? =
        clientAuthenticationConfig?.clientAttestationMethod()?.config

    fun supportsPreAuthAnonymous(): Boolean =
        clientAuthenticationConfig?.supportsPreAuthAnonymous() == true
}
