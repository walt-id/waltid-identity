package id.walt.did.exceptions

open class DidExceptions(message: String) : Exception(message)

class InvalidServiceIdException(message: String) : DidExceptions(message)
class InvalidServiceTypeException(message: String) : DidExceptions(message)
class EmptyServiceEndpointException(message: String) : DidExceptions(message)
class ReservedKeyOverrideException(message: String) : DidExceptions(message)
class InvalidServiceControllerException(message: String) : DidExceptions(message)