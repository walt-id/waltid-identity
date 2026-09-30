package id.walt.ktorauthnz.methods

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.converter.AttestedCredentialDataConverter
import com.webauthn4j.converter.util.ObjectConverter
import com.webauthn4j.credential.CredentialRecordImpl
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import com.webauthn4j.data.attestation.statement.NoneAttestationStatement
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.DefaultChallenge
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.verifier.exception.VerificationException
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.KtorAuthnzManager
import id.walt.ktorauthnz.accounts.identifiers.methods.PasskeyIdentifier
import id.walt.ktorauthnz.amendmends.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.AuthenticationFailureException
import id.walt.ktorauthnz.exceptions.InvalidChallengeException
import id.walt.ktorauthnz.methods.config.PasskeySettings
import id.walt.ktorauthnz.methods.storeddata.PasskeyStoredData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.minutes

/**
 * Login with a passkey (WebAuthn, discoverable credentials - no username needed). Passkeys are registered with
 * `passkeyEnrollment()`; the relying party comes from [KtorAuthnzManager.passkeys].
 *
 * - `POST passkey/options`: the `PublicKeyCredentialRequestOptions` (JSON) for `navigator.credentials.get()`
 * - `POST passkey`: the credential it returned (WebAuthn JSON, e.g. `credential.toJSON()`); logs in its account
 */
object Passkey : AuthenticationMethod("passkey") {

    override val relatedAuthMethodStoredData = PasskeyStoredData::class

    internal val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
    private val random = SecureRandom()
    private val objectConverter = ObjectConverter()
    private val credentialDataConverter = AttestedCredentialDataConverter(objectConverter)
    private val webAuthn = WebAuthnManager.createNonStrictWebAuthnManager(objectConverter)
    private val challengeLifetime = 5.minutes

    internal val settings: PasskeySettings
        get() = KtorAuthnzManager.passkeys ?: error("Passkeys need their relying party: set `passkeys` when installing KtorAuthnz")

    /** Signature algorithms offered for new passkeys: ES256, EdDSA, RS256. */
    private val algorithms = listOf(COSEAlgorithmIdentifier.ES256, COSEAlgorithmIdentifier.EdDSA, COSEAlgorithmIdentifier.RS256)

    /** A new single-use challenge for [purpose]; base64url. */
    internal suspend fun newChallenge(purpose: String): String {
        val challenge = base64Url.encode(ByteArray(32).also { random.nextBytes(it) })
        KtorAuthnzManager.expiringStore.put("passkey-challenge:$challenge", purpose, challengeLifetime)
        return challenge
    }

    /** Takes the challenge a response signed: it must have been issued for [purpose], and works once. */
    private suspend fun takeChallenge(challenge: ByteArray, purpose: String) {
        val key = "passkey-challenge:${base64Url.encode(challenge)}"
        val store = KtorAuthnzManager.expiringStore
        val issuedFor = store.get(key)
        if (issuedFor != purpose || !store.putIfAbsent("$key:used", "used", challengeLifetime)) throw InvalidChallengeException()
        store.remove(key)
    }

    private fun serverProperty(challenge: ByteArray) =
        ServerProperty(settings.origins.map(::Origin).toSet(), settings.rpId, DefaultChallenge(challenge))

    internal fun registrationOptions(challenge: String, accountId: String, userName: String, excludeCredentials: List<String>) = buildJsonObject {
        putJsonObject("rp") { put("id", settings.rpId); put("name", settings.rpName) }
        putJsonObject("user") {
            put("id", base64Url.encode(accountId.toByteArray()))
            put("name", userName)
            put("displayName", userName)
        }
        put("challenge", challenge)
        putJsonArray("pubKeyCredParams") { algorithms.forEach { alg -> addJsonObject { put("type", "public-key"); put("alg", alg.value) } } }
        put("timeout", challengeLifetime.inWholeMilliseconds)
        putJsonArray("excludeCredentials") { excludeCredentials.forEach { addJsonObject { put("type", "public-key"); put("id", it) } } }
        putJsonObject("authenticatorSelection") {
            put("residentKey", "required")
            put("requireResidentKey", true)
            put("userVerification", if (settings.requireUserVerification) "required" else "preferred")
        }
        put("attestation", "none")
    }

    /** Verifies a registration response for [accountId]; returns the new passkey's identifier and stored data. */
    internal suspend fun verifyRegistration(responseJson: String, accountId: String, name: String?): Pair<PasskeyIdentifier, PasskeyStoredData> {
        val parsed = runCatching { webAuthn.parseRegistrationResponseJSON(responseJson) }
            .getOrElse { throw IllegalArgumentException("Invalid passkey registration response: ${it.message}", it) }
        val challenge = parsed.collectedClientData?.challenge?.value ?: throw InvalidChallengeException()
        takeChallenge(challenge, "register:$accountId")

        val verified = try {
            webAuthn.verify(
                parsed,
                RegistrationParameters(
                    serverProperty(challenge),
                    algorithms.map { PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, it) },
                    settings.requireUserVerification,
                    true,
                )
            )
        } catch (e: VerificationException) {
            throw AuthenticationFailureException("Passkey registration failed: ${e.message}")
        }
        val authenticatorData = verified.attestationObject!!.authenticatorData
        val credentialData = authenticatorData.attestedCredentialData!!
        return PasskeyIdentifier(base64Url.encode(credentialData.credentialId)) to PasskeyStoredData(
            attestedCredentialData = base64Url.encode(credentialDataConverter.convert(credentialData)),
            signCount = authenticatorData.signCount,
            userVerified = authenticatorData.isFlagUV,
            backupEligible = authenticatorData.isFlagBE,
            backedUp = authenticatorData.isFlagBS,
            name = name,
        )
    }

    /** Verifies an authentication response; returns the passkey's identifier (its stored counter is updated). */
    suspend fun verifyAuthentication(responseJson: String): PasskeyIdentifier {
        val parsed = runCatching { webAuthn.parseAuthenticationResponseJSON(responseJson) }
            .getOrElse { throw IllegalArgumentException("Invalid passkey response: ${it.message}", it) }
        val challenge = parsed.collectedClientData?.challenge?.value ?: throw InvalidChallengeException()
        takeChallenge(challenge, "login")

        val identifier = PasskeyIdentifier(base64Url.encode(parsed.credentialId))
        val accountId = identifier.resolveIfExists() ?: throw AuthenticationFailureException("Unknown passkey")
        parsed.userHandle?.let { handle ->
            if (!handle.contentEquals(accountId.toByteArray())) throw AuthenticationFailureException("Passkey belongs to another account")
        }
        val stored = lookupAccountIdentifierStoredData<PasskeyStoredData>(identifier)
        val record = CredentialRecordImpl(
            NoneAttestationStatement(), stored.userVerified, stored.backupEligible, stored.backedUp, stored.signCount,
            credentialDataConverter.convert(base64Url.decode(stored.attestedCredentialData)), null, null, null, null,
        )
        val verified = try {
            webAuthn.verify(
                parsed,
                AuthenticationParameters(serverProperty(challenge), record, listOf(parsed.credentialId), settings.requireUserVerification, true)
            )
        } catch (e: VerificationException) {
            throw AuthenticationFailureException("Passkey verification failed: ${e.message}")
        }
        val authenticatorData = verified.authenticatorData!!
        KtorAuthnzManager.accountStore.updateAccountIdentifierStoredData(
            identifier, id, stored.copy(signCount = authenticatorData.signCount, backedUp = authenticatorData.isFlagBS)
        )
        return identifier
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        route("passkey", { tags("Authentication") }) {
            post("options", {
                summary = "Passkey login options"
                description = "PublicKeyCredentialRequestOptions (WebAuthn JSON) for navigator.credentials.get()"
                response { HttpStatusCode.OK to { body<JsonObject>() } }
            }) {
                call.respond(buildJsonObject {
                    put("challenge", newChallenge("login"))
                    put("rpId", settings.rpId)
                    put("timeout", challengeLifetime.inWholeMilliseconds)
                    put("userVerification", if (settings.requireUserVerification) "required" else "preferred")
                })
            }
        }
        post("passkey", {
            tags("Authentication")
            summary = "Log in with a passkey"
            request { body<JsonObject> { description = "The PublicKeyCredential from navigator.credentials.get(), as WebAuthn JSON" } }
            response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
        }) {
            val session = call.getAuthSession(authContext)
            val identifier = verifyAuthentication(call.receiveText())
            call.handleAuthSuccess(session, authContext(call), identifier.resolveToAccountId())
        }
    }

}
