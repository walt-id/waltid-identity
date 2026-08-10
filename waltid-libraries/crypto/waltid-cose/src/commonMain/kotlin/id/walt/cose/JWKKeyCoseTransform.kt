package id.walt.cose

import id.walt.crypto.keys.jwk.JWKKey

@Deprecated("Use EncodedKey.Jwk.publicOnly().toCoseKey() with a crypto2 JWK.")
expect object JWKKeyCoseTransform {

    /**
     * Exports public key as a COSE Key object
     */
    @Deprecated("Use EncodedKey.Jwk.publicOnly().toCoseKey() with a crypto2 JWK.")
    fun JWKKey.getCosePublicKey(): CoseKey

}
