package id.walt.sdjwt

import kotlinx.serialization.json.JsonObject
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Crypto provider, that provides signing and verifying of standard JWTs on the target platform
 * Can be implemented by library user, to integrate their own or custom JWT crypto library
 * Default implementations exist for some platforms.
 * @see id.walt.sdjwt.SimpleJWTCryptoProvider
 */
@Suppress("NON_EXPORTABLE_TYPE")
@OptIn(ExperimentalJsExport::class)
@JsExport
interface JWTCryptoProvider {
    /**
     * Interface method to create a signed JWT for the given JSON payload object, with an optional keyID.
     * @param payload The JSON payload of the JWT to be signed
     * @param keyID Optional keyID of the signing key to be used, if required by crypto provider
     */
    fun sign(payload: JsonObject, keyID: String? = null, typ: String = "JWT"): String

    /**
     * Interface method for verifying a JWT signature
     * @param jwt A signed JWT token to be verified
     */
    fun verify(jwt: String): JwtVerificationResult
}
