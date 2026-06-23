package id.walt.sdjwt

/** Thrown when an SD-JWT fails verification. */
class SDJwtVerificationException(message: String, cause: Throwable? = null) : Exception(message, cause)
