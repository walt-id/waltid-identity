package id.walt.policies.policies

import id.walt.crypto.keys.Key
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.WaltIdJWTCryptoProvider

actual object JWTCryptoProviderManager {
  actual fun getDefaultJWTCryptoProvider(keys: Map<String, Key>): JWTCryptoProvider {
    return WaltIdJWTCryptoProvider(keys)
  }
}
