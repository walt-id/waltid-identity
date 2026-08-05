package id.walt.sdjwt

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

class Crypto2JWTCryptoProvider(
    keys: Map<String, Crypto2SdJwtKey>,
) : JWTCryptoProvider {
    private val delegate = Crypto2AsyncJWTCryptoProvider(keys)

    override fun sign(
        payload: JsonObject,
        keyID: String?,
        typ: String,
        headers: Map<String, Any>,
    ): String = runBlocking { delegate.sign(payload, keyID, typ, headers) }

    override fun verify(jwt: String, keyID: String?): JwtVerificationResult = runBlocking {
        delegate.verify(jwt, keyID)
    }
}
