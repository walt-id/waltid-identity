package id.walt.commons.exceptions

import io.ktor.http.HttpStatusCode

open class CryptoException( val status: HttpStatusCode,message: String) : Exception(message)


class KeyTypeNotSupportedException(type: String) : CryptoException(HttpStatusCode.BadRequest, "Key type $type not Registered")
class KeyBackendNotSupportedException(backend: String) : CryptoException(HttpStatusCode.BadRequest, "Key backend $backend not registered")