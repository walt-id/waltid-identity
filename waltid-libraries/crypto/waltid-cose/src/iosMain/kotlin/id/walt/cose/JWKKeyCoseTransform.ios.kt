package id.walt.cose

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
actual object JWKKeyCoseTransform {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    actual fun JWKKey.getCosePublicKey(): CoseKey {
        val jwkObj = runBlocking { exportJWKObject() }
        return when (keyType) {
            KeyType.secp256r1, KeyType.secp384r1, KeyType.secp521r1 -> {
                val x = base64Url.decode(jwkObj["x"]!!.jsonPrimitive.content)
                val y = base64Url.decode(jwkObj["y"]!!.jsonPrimitive.content)
                CoseKey(
                    kty = Cose.KeyTypes.EC2,
                    crv = Cose.EllipticCurves.ellipticCurveForKeyType(keyType),
                    x = x,
                    y = y
                )
            }

            KeyType.Ed25519 -> {
                val x = base64Url.decode(jwkObj["x"]!!.jsonPrimitive.content)
                CoseKey(
                    kty = Cose.KeyTypes.OKP,
                    crv = Cose.EllipticCurves.Ed25519,
                    x = x
                )
            }

            else -> throw IllegalArgumentException("Key type $keyType not supported for COSE conversion")
        }
    }
}
