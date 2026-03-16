package id.walt.cose

import id.walt.crypto.keys.jwk.JWKKey

expect object JWKKeyCoseTransform {

    /**
     * Exports public key as a COSE Key object
     */
    fun JWKKey.getCosePublicKey(): CoseKey

}
