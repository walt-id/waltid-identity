package id.walt.sdjwt

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@ExperimentalJsExport
@JsExport
data class JwtVerificationResult(
    val verified: Boolean,
    val message: String? = null
)

@ExperimentalJsExport
@JsExport
data class VerificationResult<T : SDJwt>(
    val sdJwt: T,
    val signatureVerified: Boolean,
    val disclosuresVerified: Boolean,
    val message: String? = null
) {
    val verified
        get() = signatureVerified && disclosuresVerified
}
