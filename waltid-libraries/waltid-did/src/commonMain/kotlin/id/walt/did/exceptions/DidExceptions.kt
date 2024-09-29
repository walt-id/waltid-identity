package id.walt.did.exceptions

open  class DidIllegalArgumentException(message: String) : IllegalArgumentException(message)
open class DidIllegalStateException(message: String) : IllegalStateException(message)


class InvalidServiceIdException(message: String) : DidIllegalArgumentException(message)
class InvalidServiceTypeException(message: String) : DidIllegalArgumentException(message)
class EmptyServiceEndpointException(message: String) : DidIllegalArgumentException(message)
class ReservedKeyOverrideException(message: String) : DidIllegalStateException(message)
class InvalidServiceControllerException(message: String) : DidIllegalStateException(message)
class PrivateKeyNotAllowedException(message: String) : DidIllegalArgumentException(message)
class InvalidVerificationConfigurationException(message: String) : DidIllegalArgumentException(message)
class DidFinalizationException(message: String) : DidIllegalStateException(message)
class JobInitializationException(message: String) : DidIllegalStateException(message)
class KeyTypeMismatchException(message: String) : DidIllegalArgumentException(message)

class UnexpectedDidStateException(message: String) : DidIllegalStateException(message)
class UnexpectedActionException(message: String) : DidIllegalStateException(message)
