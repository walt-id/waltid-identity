package id.walt.ktorauthnz.methods

import com.atlassian.onetime.core.TOTP as TotpCode
import com.atlassian.onetime.model.TOTPSecret
import com.atlassian.onetime.service.DefaultTOTPService
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.amendments.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.events.AuthnzEvent
import id.walt.ktorauthnz.events.AuthnzEvents
import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.exceptions.OTPAuthException
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.config.TotpSetupConfiguration
import id.walt.ktorauthnz.methods.sessiondata.IdentifiedSessionData
import id.walt.ktorauthnz.methods.sessiondata.TotpSetupSessionData
import id.walt.ktorauthnz.methods.storeddata.TOTPStoredData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepCustomData
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.security.SecureRandom
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * Sets up TOTP during login, for accounts whose flow asks for TOTP but that have none yet. Offer it next to `totp`:
 *
 * ```json
 * {"method": "email", "continue": [{"method": "totp", "success": true}, {"method": "totp-setup", "success": true}]}
 * ```
 *
 * - `POST {sessionId}/totp-setup/start`: a new secret and its `otpauth://` URI (for a QR code), in `next_step`
 * - `POST {sessionId}/totp-setup {code}`: a code from the authenticator app stores the secret and completes the step
 *
 * An account that has TOTP already is refused: it logs in with `totp`, and a password alone cannot replace its second
 * factor. Until an account has set it up, its first login is as strong as the steps before this one. Recovery codes can
 * be created after login (`totpEnrollment`'s `recovery-codes`).
 */
object TotpSetup : AuthenticationMethod("totp-setup") {

    override val relatedAuthMethodConfiguration = TotpSetupConfiguration::class

    @Serializable
    data class TotpSetupCode(val code: String)

    private val pendingLifetime = 10.minutes

    private fun AuthSession.setupConfiguration(): TotpSetupConfiguration =
        flows?.firstOrNull { it.method == this@TotpSetup.id }?.config?.let { Json.decodeFromJsonElement<TotpSetupConfiguration>(it) }
            ?: TotpSetupConfiguration()

    private suspend fun accountWithoutTotp(session: AuthSession): String {
        val accountId = session.accountId
            ?: throw AuthSessionStateException("TOTP setup needs a previous step that identifies the account")
        if (KtorAuthnzManager.accountStore.lookupStoredDataForAccount(accountId, TOTP) != null) {
            throw AuthSessionStateException("TOTP is already set up for this account; log in with totp")
        }
        return accountId
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        route("totp-setup", { tags("Authentication") }) {
            post("start", {
                summary = "Start setting up TOTP"
                response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
            }) {
                val session = call.getAuthSession(authContext)
                val accountId = accountWithoutTotp(session)
                val secret = TOTPSecret(ByteArray(20).also { SecureRandom().nextBytes(it) }).base32Encoded
                session.setSessionData(this@TotpSetup, TotpSetupSessionData(secret, Clock.System.now() + pendingLifetime))

                fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")
                val issuer = session.setupConfiguration().issuer
                val label = session.getSessionData<IdentifiedSessionData>(Identify)?.value ?: accountId
                val uri = "otpauth://totp/${enc(issuer)}:${enc(label)}?secret=$secret&issuer=${enc(issuer)}&algorithm=SHA1&digits=6&period=30"

                call.handleAuthNextStep(
                    session = session,
                    nextStepInfo = AuthSessionNextStepCustomData(buildJsonObject { put("secret", secret); put("otpauth_uri", uri) }),
                    nextStepDescription = "Add the secret to an authenticator app (e.g. scan otpauth_uri as a QR code), " +
                            "then POST ${session.id}/totp-setup {\"code\": ...} with a code it shows.",
                )
            }

            post({
                summary = "Confirm the TOTP setup with a code"
                request { body<TotpSetupCode>() }
                response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
            }) {
                val session = call.getAuthSession(authContext)
                val accountId = accountWithoutTotp(session)
                val pending = session.getSessionData<TotpSetupSessionData>(this@TotpSetup)
                    ?.takeIf { Clock.System.now() < it.expiresAt }
                    ?: throw AuthSessionStateException("No TOTP setup in progress; start it with totp-setup/start")
                val code = call.receive<TotpSetupCode>().code.trim()

                authCheck(DefaultTOTPService().verify(TotpCode(code), TOTPSecret.fromBase32EncodedString(pending.secret)).isSuccess(), OTPAuthException())
                authCheck(KtorAuthnzManager.expiringStore.putIfAbsent("totp-used:$accountId:$code", "used", 3.minutes), OTPAuthException())

                KtorAuthnzManager.accountStore.updateAccountStoredData(accountId, TOTP.id, TOTPStoredData(pending.secret))
                session.sessionData?.remove(id)
                AuthnzEvents.emit(AuthnzEvent.MethodEnrolled(accountId, TOTP.id))
                call.handleAuthSuccess(session, authContext(call), null)
            }
        }
    }
}
