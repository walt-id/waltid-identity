package id.walt.sdjwt

import kotlin.js.Promise

@OptIn(ExperimentalJsExport::class)
@JsExport
interface JSAsyncJWTCryptoProvider : AsyncJWTCryptoProvider {
    fun signAsync(payload: dynamic, keyID: String?): Promise<String>
    fun verifyAsync(jwt: String): Promise<JwtVerificationResult>
}
