package id.walt.commons.exceptions

import io.ktor.http.HttpStatusCode

open class CryptoException(val status: HttpStatusCode, message: String) : Exception(message)


class KeyTypeNotSupportedException(type: String) :
    CryptoException(HttpStatusCode.BadRequest, "Key type $type not Supported")

class KeyBackendNotSupportedException(backend: String) :
    CryptoException(HttpStatusCode.BadRequest, "Key backend $backend not Supported")

class MissingKeyTypeException() :
    CryptoException(HttpStatusCode.BadRequest, "Key type is missing in the serialized key")

class KeyNotFoundException(id: String) : CryptoException(HttpStatusCode.NotFound, "Key with id $id not found")

class SigningException(message: String) : CryptoException(HttpStatusCode.InternalServerError, message)
class VerificationException(message: String) : CryptoException(HttpStatusCode.InternalServerError, message)
class PublicKeyNotFoundException(message: String) : CryptoException(HttpStatusCode.NotFound, message)
class InvalidAuthenticationMethodException() : CryptoException(
    HttpStatusCode.BadRequest,
    "No valid authentication method passed! Provide either accessKey, roleId and secretId, or username and password."
)

class TSELoginException(message: String) : CryptoException(HttpStatusCode.InternalServerError, message)