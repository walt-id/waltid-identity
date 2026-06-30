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
     * @param typ JWT `typ` header value to include in the protected header
     * @param headers Additional protected header values to include in the signed JWT
     */
    @JsExport.Ignore
    suspend fun sign(
        payload: JsonObject,
        keyID: String? = null,
        typ: String = "JWT",
        headers: Map<String, Any> = mapOf()
    ): String

    /**
     * Interface method for verifying a JWT signature
     * @param jwt A signed JWT token to be verified
     */
    @JsExport.Ignore
    suspend fun verify(jwt: String): JwtVerificationResult
}
