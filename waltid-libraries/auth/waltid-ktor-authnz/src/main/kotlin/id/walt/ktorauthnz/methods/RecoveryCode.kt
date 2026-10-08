package id.walt.ktorauthnz.methods

import kotlin.time.Duration.Companion.days
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.amendments.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.exceptions.OTPAuthException
import id.walt.ktorauthnz.exceptions.authCheck
import id.walt.ktorauthnz.methods.storeddata.RecoveryCodesStoredData
import id.walt.ktorauthnz.sessions.AuthSession
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.kotlincrypto.hash.sha2.SHA256
import java.security.SecureRandom

/**
 * One-time recovery codes, the fallback for a second factor (e.g. a lost TOTP device): offer it next to `totp` in a
 * flow, `"continue": [{"method": "totp", ...}, {"method": "recovery-code", ...}]`. Each code works once.
 */
object RecoveryCode : AuthenticationMethod("recovery-code") {

    override val relatedAuthMethodStoredData = RecoveryCodesStoredData::class

    private val random = SecureRandom()
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789" // no look-alikes (l/1, o/0, i)

    /** [count] new codes, as shown to the user once (`xxxxx-xxxxx`), and their stored form. */
    fun generate(count: Int = 10): Pair<List<String>, RecoveryCodesStoredData> {
        val codes = List(count) {
            (1..10).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("").chunked(5).joinToString("-")
        }
        return codes to RecoveryCodesStoredData(codes.map(::digest))
    }

    internal fun digest(code: String): String =
        SHA256().digest(code.trim().lowercase().replace(" ", "").toByteArray()).toHexString()

    suspend fun auth(session: AuthSession, code: String) {
        val accountId = session.accountId
            ?: throw AuthSessionStateException("A recovery code needs a previous step that identifies the account")
        val stored = lookupAccountStoredData<RecoveryCodesStoredData>(accountId)
        val codeDigest = digest(code)
        authCheck(codeDigest in stored.codeDigests, OTPAuthException())
        // The account store has no compare-and-set: two requests with the same code would both read it as unused, and
        // two different codes removed at once can lose one removal. The marker is what makes a code work once.
        authCheck(
            KtorAuthnzManager.expiringStore.putIfAbsent("recovery-code-used:$accountId:$codeDigest", "used", USED_MARKER_LIFETIME),
            OTPAuthException(),
        )
        val current = lookupAccountStoredData<RecoveryCodesStoredData>(accountId)
        KtorAuthnzManager.accountStore.updateAccountStoredData(accountId, id, current.copy(codeDigests = current.codeDigests - codeDigest))
    }

    /** How long a used code stays refused even if its removal from the account was lost. Codes do not expire. */
    private val USED_MARKER_LIFETIME = (10 * 365).days

    @Serializable
    data class RecoveryCodeRequest(val code: String)

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        post(id, {
            request { body<RecoveryCodeRequest>() }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = call.getAuthSession(authContext)
            val code = requireNotNull(call.receive<RecoveryCodeRequest>().code.takeIf { it.isNotBlank() }) { "Missing recovery code" }

            auth(session, code)

            call.handleAuthSuccess(session, authContext(call), null)
        }
    }
}
