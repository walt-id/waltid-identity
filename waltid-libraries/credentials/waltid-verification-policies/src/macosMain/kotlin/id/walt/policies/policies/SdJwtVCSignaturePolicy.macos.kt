package id.walt.policies.policies

import id.walt.crypto.keys.Key
import id.walt.sdjwt.JWTCryptoProvider

actual object JWTCryptoProviderManager {
    actual fun getDefaultJWTCryptoProvider(keys: Map<String, Key>): JWTCryptoProvider {
        TODO("Not yet implemented")
    }
}