package id.walt.commons.exceptions

import io.ktor.http.HttpStatusCode

open class DidExceptions(val status: HttpStatusCode, message: String) : Exception(message)

class InvalidServiceIdException(message: String) : DidExceptions(HttpStatusCode.BadRequest, message)
class InvalidServiceTypeException(message: String) : DidExceptions(HttpStatusCode.BadRequest, message)
class EmptyServiceEndpointException(message: String) : DidExceptions(HttpStatusCode.BadRequest, message)
class ReservedKeyOverrideException(message: String) : DidExceptions(HttpStatusCode.Forbidden, message)
class InvalidServiceControllerException(message: String) : DidExceptions(HttpStatusCode.BadRequest, message)