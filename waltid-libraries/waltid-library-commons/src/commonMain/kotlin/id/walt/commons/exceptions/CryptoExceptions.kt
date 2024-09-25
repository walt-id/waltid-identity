package id.walt.commons.exceptions

import io.ktor.http.HttpStatusCode

open class CryptoException(val status: HttpStatusCode, message: String) : Exception(message)


class KeyTypeNotSupportedException(type: String) :
    CryptoException(HttpStatusCode.BadRequest, "Key type $type not supported")

class KeyBackendNotSupportedException(backend: String) :
    CryptoException(HttpStatusCode.BadRequest, "Key backend $backend not supported")

class KeyTypeMissingException() :
    CryptoException(HttpStatusCode.BadRequest, "Key type is missing in the serialized key")

class KeyNotFoundException(
    id: String,
    message: String = "Key with id $id not found"
) : CryptoException(HttpStatusCode.NotFound, message)

class SigningException(message: String) : CryptoException(HttpStatusCode.InternalServerError, message)
class VerificationException(message: String) : CryptoException(HttpStatusCode.InternalServerError, message)

class MissingSignatureException(message: String) : CryptoException(HttpStatusCode.BadRequest, message)
object TSEError {
    class LoginException(errors: List<String>) : CryptoException(
        HttpStatusCode.InternalServerError,
        "Errors occurred at TSE login: ${errors.joinToString { it }}"
    )

    class MissingAuthTokenException :
        CryptoException(HttpStatusCode.InternalServerError, "Did not receive token after login!")

    class MissingKeyNameException : CryptoException(HttpStatusCode.BadRequest, "Key name is missing from the request!")
    class MissingKeyDataException : CryptoException(HttpStatusCode.BadRequest, "Key data is missing from the request!")
    object InvalidAuthenticationMethod {
        abstract class BaseAuthenticationMethodException(reason: String) : CryptoException(
            HttpStatusCode.BadRequest,
            "No valid authentication method passed!: $reason"
        )

        class MissingAuthenticationMethodException : BaseAuthenticationMethodException(
            "Expecting any of: accessKey, roleId and secretId or username and password."
        )

        class IncompleteRoleAuthenticationMethodException : BaseAuthenticationMethodException(
            "Both roleId and secretId are required."
        )

        class IncompleteUserAuthenticationMethodException : BaseAuthenticationMethodException(
            "Both username and password are required."
        )

    }
}