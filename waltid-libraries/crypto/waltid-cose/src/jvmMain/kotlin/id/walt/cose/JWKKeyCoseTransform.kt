package id.walt.cose

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey

object JWKKeyCoseTransform {


    /**
     * Exports the public key as a COSE Key object for HPKE usage.
     */
    fun JWKKey.getCosePublicKey(): CoseKey {
        return when (keyType) {
            KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> {
                val ecKey = _internalJwk.toECKey()
                CoseKey(
                    kty = Cose.KeyTypes.EC2,
                    crv = Cose.EllipticCurves.ellipticCurveForKeyType(keyType),
                    x = ecKey.x.decode(),
                    y = ecKey.y.decode()
                )
            }
            KeyType.Ed25519 -> {
                val octKey = _internalJwk.toOctetKeyPair()
                CoseKey(
                    kty = Cose.KeyTypes.OKP,
                    crv = Cose.EllipticCurves.Ed25519,
                    x = octKey.x.decode()
                )
            }
            else -> throw IllegalArgumentException("Key type $keyType not supported for COSE conversion")
        }
    }

}
