package id.walt.credentials.presentations

class PresentationValidationException(
    val error: PresentationValidationErrors,
    val errorMessage: String,
    val additionalErrorInformation: String?,
    override val cause: Throwable? = null
) : IllegalArgumentException("$error: $errorMessage${additionalErrorInformation?.let { " $it" } ?: ""}", cause)

interface PresentationValidationErrors {
    val errorMessage: String
}

enum class W3CPresentationValidationError(override val errorMessage: String) : PresentationValidationErrors {
    ISSUER_NOT_FOUND("Was unable to get issuer DID for W3C VP"),
    SIGNATURE_VERIFICATION_FAILED("Signature verification failed."),
    AUDIENCE_MISMATCH("W3C VP JWT 'aud' claim mismatch."),
    NONCE_MISMATCH("W3C VP JWT 'nonce' claim mismatch."),
    MISSING_VP("W3C VP JWT 'vp' claim is missing.")
}

enum class DcSdJwtPresentationValidationError(override val errorMessage: String) : PresentationValidationErrors {
    MISSING_CNF("SD-JWT core is missing 'cnf' claim for holder verification."),
    MISSING_CNF_METHOD("SD-JWT core contains 'cnf' claim for holder verification, but no compatible method (or it is empty)."),
    INVALID_CNF_KID("SD-JWT core contains 'cnf.kid' claim for holder verification, but the kid is not valid. Check if it is a string with a sensible kid."),
    CNF_KID_CANNOT_RESOLVE_DID("SD-JWT core contains 'cnf.kid' claim for holder verification, but the kid is not resolvable."),
    CNF_JWK_CANNOT_PARSE_JWK("SD-JWT core contains 'cnf.jwk' claim for holder verification, but the JWK is not parseable."),

    SIGNATURE_VERIFICATION_FAILED("Key Binding JWT signature verification failed."),
    AUDIENCE_MISMATCH("KB-JWT 'aud' claim mismatch."),
    NONCE_MISMATCH("KB-JWT 'nonce' claim mismatch."),
    MISSING_SD_HASH("KB-JWT 'sd_hash' claim is missing."),
    SD_HASH_MISMATCH("KB-JWT 'sd_hash' mismatch. The KB-JWT is not bound to the presented disclosures."),
    MISMATCH_PRESENTED_CLAIMS("Claims in validated credential do not match the original DCQL claims query.")
}

enum class DcqlValidationError(override val errorMessage: String) : PresentationValidationErrors {
    MISSING_CLAIM("Claim validation failed: Requested claim path not found in the presented credential."),
    CLAIM_MISMATCH("Claim validation failed: Value for claim does not match any of the required values.")
}
