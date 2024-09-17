package id.walt.commons.exceptions

import io.ktor.http.HttpStatusCode

open class CryptoException(val status: HttpStatusCode, message: String) : Exception(message)
