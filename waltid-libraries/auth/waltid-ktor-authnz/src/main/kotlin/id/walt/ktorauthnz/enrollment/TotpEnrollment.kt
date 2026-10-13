package id.walt.ktorauthnz.enrollment

import com.atlassian.onetime.core.TOTP as TotpCode
import com.atlassian.onetime.model.TOTPSecret
import com.atlassian.onetime.service.DefaultTOTPService
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.attempts.AttemptLimiter.attemptOnIdentifier
import id.walt.ktorauthnz.auth.getAuthenticatedAccount
import id.walt.ktorauthnz.events.AuthnzEvent
import id.walt.ktorauthnz.events.AuthnzEvents
import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.exceptions.OTPAuthException
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.RecoveryCode
import id.walt.ktorauthnz.methods.TOTP
import id.walt.ktorauthnz.methods.authenticationMethodRoutes
import id.walt.ktorauthnz.methods.storeddata.TOTPStoredData
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLEncoder
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@Serializable
data class TotpEnrollmentStart(
    /** Base32 secret, for manual entry. */
    val secret: String,
    /** `otpauth://` URI, e.g. for a QR code. */
    @SerialName("otpauth_uri") val otpauthUri: String,
)

@Serializable
data class TotpEnrollmentConfirmation(val code: String)

@Serializable
data class RecoveryCodes(
    /** Shown once; only their digests are stored. */
    @SerialName("recovery_codes") val recoveryCodes: List<String>,
)

/**
 * TOTP enrolment for the authenticated account - place inside `authenticate { }`:
 *
 * - `POST totp/enroll`: a new secret (pending for [pendingLifetime]) and its `otpauth://` URI
 * - `POST totp/enroll/confirm {code}`: a code from the authenticator app confirms it; answers fresh recovery codes
 * - `DELETE totp`: removes TOTP and the recovery codes
 * - `POST recovery-codes`: replaces the recovery codes
 *
 * [issuer] is the service name authenticator apps show; [label] names the account there.
 */
fun Route.totpEnrollment(
    issuer: String,
    label: suspend io.ktor.server.application.ApplicationCall.(accountId: String) -> String = { it },
    pendingLifetime: Duration = 10.minutes,
    recoveryCodeCount: Int = 10,
) = authenticationMethodRoutes {
    val store = { KtorAuthnzManager.expiringStore }
    val accounts = { KtorAuthnzManager.accountStore }
    fun pendingKey(accountId: String) = "totp-enrollment:$accountId"

    suspend fun newRecoveryCodes(accountId: String): RecoveryCodes {
        val (codes, stored) = RecoveryCode.generate(recoveryCodeCount)
        accounts().updateAccountStoredData(accountId, RecoveryCode.id, stored)
        return RecoveryCodes(codes)
    }

    route("totp", { tags("Authentication") }) {
        post("enroll", {
            summary = "Start TOTP enrolment"
            response { HttpStatusCode.OK to { body<TotpEnrollmentStart>() } }
        }) {
            val accountId = call.getAuthenticatedAccount()
            val secret = TOTPSecret(ByteArray(20).also { SecureRandom().nextBytes(it) }).base32Encoded
            store().put(pendingKey(accountId), secret, pendingLifetime)

            fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")
            val accountLabel = call.label(accountId)
            val uri = "otpauth://totp/${enc(issuer)}:${enc(accountLabel)}?secret=$secret&issuer=${enc(issuer)}&algorithm=SHA1&digits=6&period=30"
            call.respond(TotpEnrollmentStart(secret, uri))
        }

        post("enroll/confirm", {
            summary = "Confirm TOTP enrolment with a code"
            request { body<TotpEnrollmentConfirmation>() }
            response { HttpStatusCode.OK to { body<RecoveryCodes>() } }
        }) {
            val accountId = call.getAuthenticatedAccount()
            call.attemptOnIdentifier("totp-enrollment", accountId)
            val pending = store().get(pendingKey(accountId))
                ?: throw AuthSessionStateException("No TOTP enrolment in progress; start it with totp/enroll")
            val code = call.receive<TotpEnrollmentConfirmation>().code

            authCheck(DefaultTOTPService().verify(TotpCode(code), TOTPSecret.fromBase32EncodedString(pending)).isSuccess(), OTPAuthException())

            accounts().updateAccountStoredData(accountId, TOTP.id, TOTPStoredData(pending))
            store().remove(pendingKey(accountId))
            AuthnzEvents.emit(AuthnzEvent.MethodEnrolled(accountId, TOTP.id))
            call.respond(newRecoveryCodes(accountId))
        }

        delete({
            summary = "Remove TOTP and the recovery codes"
            response { HttpStatusCode.NoContent to { } }
        }) {
            val accountId = call.getAuthenticatedAccount()
            accounts().deleteAccountStoredData(accountId, TOTP.id)
            accounts().deleteAccountStoredData(accountId, RecoveryCode.id)
            AuthnzEvents.emit(AuthnzEvent.MethodRemoved(accountId, TOTP.id))
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post("recovery-codes", {
        tags("Authentication")
        summary = "Replace the recovery codes"
        response { HttpStatusCode.OK to { body<RecoveryCodes>() } }
    }) {
        val accountId = call.getAuthenticatedAccount()
        if (accounts().lookupStoredDataForAccount(accountId, TOTP) == null) {
            throw AuthSessionStateException("Recovery codes need an enrolled second factor")
        }
        call.respond(newRecoveryCodes(accountId))
    }
}
