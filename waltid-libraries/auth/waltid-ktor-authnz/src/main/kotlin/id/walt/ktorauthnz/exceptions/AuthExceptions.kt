package id.walt.ktorauthnz.exceptions

import id.walt.errors.HttpStatusError
import id.walt.errors.StatusException
import io.ktor.http.HttpStatusCode

/** Authentication failures, answered with their status by the services' status pages. */
sealed class AuthException(
    message: String,
    status: HttpStatusCode
) : StatusException(status.value, message)

class InvalidCredentialsException :
    AuthException("Invalid email or password.", HttpStatusCode.Unauthorized)

class ExpiredTokenException(override val message: String) :
    AuthException(message, HttpStatusCode.Unauthorized)

class JWTVerificationException :
    AuthException("JWT verification failed.", HttpStatusCode.Unauthorized)

class RadiusAuthException :
    AuthException("RADIUS server did not accept authentication", HttpStatusCode.NotAcceptable)

class OTPAuthException :
    AuthException("Invalid one-time password (OTP).", HttpStatusCode.Unauthorized)

class Web3AuthException(override val message: String) :
    AuthException(message, HttpStatusCode.Unauthorized)

class InvalidChallengeException :
    AuthException("Cannot verify that nonce was supplied by system.", HttpStatusCode.Unauthorized)

class AccountDataNotFoundException(methodId: String) :
    AuthException("No stored data found for authentication method: $methodId", HttpStatusCode.NotFound)

/** Too many failed attempts on this session or for this account identifier; retry after the window passes. */
class TooManyAttemptsException(override val message: String) :
    AuthException(message, HttpStatusCode.TooManyRequests)

/**
 * The authentication session does not exist - never created, expired, or already logged out. An
 * [IllegalArgumentException], as unknown sessions always were, answered 404.
 */
class AuthSessionNotFoundException(sessionId: String) :
    IllegalArgumentException("Unknown or expired authentication session: $sessionId"), HttpStatusError {
    override val status = HttpStatusCode.NotFound.value
}

/** The authentication session cannot take this step, e.g. it is already complete or expects another method. */
class AuthSessionStateException(override val message: String) :
    AuthException(message, HttpStatusCode.BadRequest)

/** An identifier a new account was to get belongs to an account already. */
class AccountExistsException(identifierType: String) :
    AuthException("An account with this $identifierType exists already", HttpStatusCode.Conflict)

/** The action needs a login more recent than the caller's; log in again and retry. */
class ReauthenticationRequiredException(override val message: String) :
    AuthException(message, HttpStatusCode.Unauthorized)

/** The identity was authenticated, but no account belongs to it. */
class AccountNotFoundException(identifierType: String) :
    AuthException("No account exists for this $identifierType identity", HttpStatusCode.NotFound)

/** The token is not (or no longer) valid. An [IllegalStateException], as unknown tokens always were, answered 401. */
class InvalidTokenException(message: String) : IllegalStateException(message), HttpStatusError {
    override val status = HttpStatusCode.Unauthorized.value
}
