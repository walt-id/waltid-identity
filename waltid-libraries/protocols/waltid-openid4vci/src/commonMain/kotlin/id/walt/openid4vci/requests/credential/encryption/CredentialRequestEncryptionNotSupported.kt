package id.walt.openid4vci.requests.credential.encryption

class CredentialRequestEncryptionNotSupported(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
