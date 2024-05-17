package id.walt.verifier.oidc

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.JwtVerificationResult
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject

private val SERVER_SIGNING_KEY by lazy { runBlocking { JWKKey.generate(KeyType.secp256r1) } }
object RequestSigningCryptoProvider: JWTCryptoProvider {
   val signingKey: JWKKey = SERVER_SIGNING_KEY

    override fun sign(payload: JsonObject, keyID: String?, typ: String): String {
        TODO("Not yet implemented")
    }

    override fun verify(jwt: String): JwtVerificationResult {
        TODO("Not yet implemented")
    }

    suspend fun signWithLocalKeyAndHeader(payload: JsonObject, headers: Map<String, String>): String {
        return signingKey.signJws(payload.toString().toByteArray(), headers)
    }
}
