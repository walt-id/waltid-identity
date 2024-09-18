package id.walt.commons.exceptions

import io.ktor.http.HttpStatusCode

open class CryptoException( val status: HttpStatusCode,message: String) : Throwable(message)


class KeyTypeNotSupportedException(type: String) : CryptoException(HttpStatusCode.BadRequest, "Key type $type not supported")
class KeyBackendNotSupportedException(backend: String) : CryptoException(HttpStatusCode.BadRequest, "Key backend $backend not registered")