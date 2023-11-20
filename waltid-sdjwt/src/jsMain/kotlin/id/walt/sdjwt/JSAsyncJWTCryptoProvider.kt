package id.walt.sdjwt

import kotlin.js.Promise

@ExperimentalJsExport
@JsExport
interface JSAsyncJWTCryptoProvider : AsyncJWTCryptoProvider {
    fun signAsync(payload: dynamic, keyID: String?): Promise<String>
    fun verifyAsync(jwt: String): Promise<JwtVerificationResult>
}
