package id.walt.crypto.exceptions


open class CryptoArgumentException(message: String, cause: Throwable? = null) : IllegalArgumentException(message)
open class CryptoStateException(message: String, cause: Throwable? = null) : IllegalStateException(message)

class KeyTypeNotSupportedException(type: String, cause: Throwable? = null) :
    CryptoArgumentException("The key type '$type' is not supported. Please provide a valid key type.")

class KeyBackendNotSupportedException(backend: String, cause: Throwable? = null) :
    CryptoArgumentException("The key backend '$backend' is not supported. Ensure you are using a compatible backend.")

class KeyTypeMissingException(cause: Throwable? = null) :
    CryptoArgumentException("The key type is missing from the serialized key. Please include the key type.")

class KeyNotFoundException(
    id: String = "",
    message: String = "The key with ID '$id' could not be found. Please verify the ID and try again.",
    cause: Throwable? = null,
) : CryptoArgumentException(message)

class SigningException(message: String, cause: Throwable? = null) :
    CryptoStateException("An error occurred during the signing process: $message")

class VerificationException(message: String, cause: Throwable? = null) :
    CryptoArgumentException("An error occurred during verification: $message")

class MissingSignatureException(message: String, cause: Throwable? = null) :
    CryptoArgumentException("The signature is missing or invalid: $message")

class KeyCreationFailed(message: String, cause: Throwable? = null) :
    CryptoStateException(message, cause)

class UnauthorizedKeyAccess(message: String, cause: Throwable? = null) :
    CryptoStateException(message, cause)

class KeyVaultUnavailable(message: String, cause: Throwable? = null) :
    CryptoStateException(message, cause)

