package id.walt.crypto.exceptions

object TSEError {
    class LoginException(errors: List<String>) : CryptoStateException(
        "Errors occurred during TSE login: ${errors.joinToString { it }}. Please try again."
    )

    class MissingAuthTokenException :
        CryptoStateException("Authentication token was not received after login. Please ensure the login process completed successfully.")

    class MissingKeyNameException :
        CryptoArgumentException("The key name is missing from the request. Please provide a valid key name.")

    class MissingKeyDataException :
        CryptoArgumentException("The key data is missing from the request. Please include the necessary key data.")

    object InvalidAuthenticationMethod {
        abstract class BaseAuthenticationMethodException(reason: String) : CryptoArgumentException(
            "The provided authentication method is invalid: $reason"
        )

        class MissingAuthenticationMethodException : BaseAuthenticationMethodException(
            "Please provide one of the following authentication methods: accessKey, roleId and secretId, or username and password."
        )

        class IncompleteRoleAuthenticationMethodException : BaseAuthenticationMethodException(
            "Both roleId and secretId are required for role-based authentication. Please provide both values."
        )

        class IncompleteUserAuthenticationMethodException : BaseAuthenticationMethodException(
            "Both username and password are required for user-based authentication. Please provide both credentials."
        )
    }
}
