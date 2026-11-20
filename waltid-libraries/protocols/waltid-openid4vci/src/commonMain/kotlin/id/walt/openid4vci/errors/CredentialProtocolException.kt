package id.walt.openid4vci.errors

/** A credential protocol failure, including during initial proof validation or deferred delivery. */
open class CredentialProtocolException(val error: CredentialError) : IllegalStateException(error.description)
