package id.walt.crypto.exceptions


open class CryptoArgumentException(message: String) : IllegalArgumentException(message)
open class CryptoStateException(message: String) : IllegalStateException(message)

class KeyTypeNotSupportedException(type: String) :
    CryptoArgumentException("The key type '$type' is not supported. Please provide a valid key type.")

class KeyBackendNotSupportedException(backend: String) :
    CryptoArgumentException("The key backend '$backend' is not supported. Ensure you are using a compatible backend.")

class KeyTypeMissingException() :
    CryptoArgumentException("The key type is missing from the serialized key. Please include the key type.")

class KeyNotFoundException(
    id: String,
    message: String = "The key with ID '$id' could not be found. Please verify the ID and try again."
) : CryptoArgumentException(message)

class SigningException(message: String) :
    CryptoStateException("An error occurred during the signing process: $message")

class VerificationException(message: String) :
    CryptoStateException("An error occurred during verification: $message")

class MissingSignatureException(message: String) :
    CryptoStateException("The signature is missing or invalid: $message")

