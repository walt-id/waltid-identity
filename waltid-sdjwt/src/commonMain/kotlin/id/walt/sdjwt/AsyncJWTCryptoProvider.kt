package id.walt.sdjwt

import kotlinx.serialization.json.JsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Crypto provider, that provides signing and verifying of standard JWTs on the target platform
 * Can be implemented by library user, to integrate their own or custom JWT crypto library
 * Default implementations exist for some platforms.
 * **Note for JavaScript**: Implement _JSAsyncJWTCryptoProvider_ instead.
 * @see id.walt.sdjwt.SimpleJWTCryptoProvider
 */
@OptIn(ExperimentalJsExport::class)
@JsExport.Ignore
interface AsyncJWTCryptoProvider {
    /**
     * Interface method to create a signed JWT for the given JSON payload object, with an optional keyID.
     * @param payload The JSON payload of the JWT to be signed
     * @param keyID Optional keyID of the signing key to be used, if required by crypto provider
     */
    @JsExport.Ignore
    suspend fun sign(payload: JsonObject, keyID: String? = null): String

    /**
     * Interface method for verifying a JWT signature
     * @param jwt A signed JWT token to be verified
     */
    @JsExport.Ignore
    suspend fun verify(jwt: String): JwtVerificationResult
}
