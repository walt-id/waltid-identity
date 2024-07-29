package id.walt.sdjwt

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
data class JwtVerificationResult(
    val verified: Boolean,
    val message: String? = null
)

@OptIn(ExperimentalJsExport::class)
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
