package id.walt.ktorauthnz.exceptions

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
