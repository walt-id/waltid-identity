package id.walt.issuer.web

import io.ktor.http.*

open class WebException(val status: HttpStatusCode, message: String): Exception(message)

class UnauthorizedException(message: String): WebException(HttpStatusCode.Unauthorized, message)
