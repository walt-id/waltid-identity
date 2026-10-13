package id.walt.ktorauthnz.methods

import id.walt.errors.StatusException
import id.walt.ktorauthnz.AuthContext
import id.walt.ktorauthnz.accounts.identifiers.methods.VerifiableCredentialIdentifier
import id.walt.ktorauthnz.amendments.AuthMethodFunctionAmendments
import id.walt.ktorauthnz.exceptions.AccountNotFoundException
import id.walt.ktorauthnz.exceptions.AuthSessionStateException
import id.walt.ktorauthnz.exceptions.AuthenticationFailureException
import id.walt.ktorauthnz.methods.config.VerifiableCredentialAuthConfiguration
import id.walt.ktorauthnz.methods.sessiondata.VerifiableCredentialSessionData
import id.walt.ktorauthnz.sessions.AuthSessionInformation
import id.walt.ktorauthnz.sessions.AuthSessionNextStepRedirectData
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*

/**
 * Login by presenting a verifiable credential, verified by a verifier2 service ([VerifiableCredentialAuthConfiguration]).
 *
 * - `POST vc/start`: opens a verifier2 session; the next step names its authorization request URL (for a QR code, or
 *   to open in a wallet on the same device)
 * - `GET {sessionId}/vc/status`: while the wallet has not presented, answers the unchanged session; once verified,
 *   logs in the account of the presented claim (or registers it via the `Registration` function amendment, which gets
 *   the [VerifiableCredentialIdentifier]); a failed or expired verification fails the step
 */
object VerifiableCredential : AuthenticationMethod("vc") {

    override val relatedAuthMethodConfiguration = VerifiableCredentialAuthConfiguration::class

    private val http = HttpClient {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun VerifiableCredentialAuthConfiguration.endpoint(path: String) = verifierUrl.trimEnd('/') + "/verification-session/" + path

    /** Opens the verifier2 session; returns its id and the authorization request URL for the wallet. */
    suspend fun startVerification(config: VerifiableCredentialAuthConfiguration): Pair<String, Url> {
        val response = http.post(config.endpoint("create")) {
            contentType(ContentType.Application.Json)
            setBody(config.setup)
        }
        if (!response.status.isSuccess()) throw StatusException(502, "The verifier refused the verification session: ${response.status}")
        val created = response.body<JsonObject>()
        val sessionId = created["sessionId"]?.jsonPrimitive?.contentOrNull ?: throw StatusException(502, "The verifier answered no sessionId")
        val url = (created["bootstrapAuthorizationRequestUrl"] ?: created["fullAuthorizationRequestUrl"])?.jsonPrimitive?.contentOrNull
            ?: throw StatusException(502, "The verifier answered no authorization request URL")
        return sessionId to Url(url)
    }

    /** The verifier2 session, as its `info` endpoint shows it. */
    suspend fun verification(config: VerifiableCredentialAuthConfiguration, verifierSessionId: String): JsonObject {
        val response = http.get(config.endpoint("$verifierSessionId/info"))
        if (!response.status.isSuccess()) throw StatusException(502, "The verifier has no session $verifierSessionId: ${response.status}")
        return response.body()
    }

    /** The configured claim of the first credential presented (for the configured query), or null. */
    fun identifierOf(verification: JsonObject, config: VerifiableCredentialAuthConfiguration): VerifiableCredentialIdentifier? {
        val presented = verification["presented_credentials"] as? JsonObject ?: return null
        val credentials = (config.credentialQueryId?.let { presented[it] } ?: presented.values.firstOrNull()) as? JsonArray
        val credentialData = (credentials?.firstOrNull() as? JsonObject)?.get("credentialData") as? JsonObject ?: return null
        val claim = config.identifierClaim.fold<String, JsonElement?>(credentialData) { element, key -> (element as? JsonObject)?.get(key) }
        val value = (claim as? JsonPrimitive)?.contentOrNull ?: return null
        return VerifiableCredentialIdentifier(config.identifierClaim.joinToString("/"), value)
    }

    override fun Route.registerAuthenticationRoutes(
        authContext: ApplicationCall.() -> AuthContext,
        functionAmendments: Map<AuthMethodFunctionAmendments, suspend (Any) -> Unit>?
    ) {
        route("vc", { tags("Authentication") }) {
            post("start", {
                summary = "Start a verifiable credential login"
                response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
            }) {
                val session = call.getAuthSession(authContext)
                val config = session.lookupFlowMethodConfiguration<VerifiableCredentialAuthConfiguration>(this@VerifiableCredential)

                val (verifierSessionId, requestUrl) = startVerification(config)
                session.setSessionData(this@VerifiableCredential, VerifiableCredentialSessionData(verifierSessionId))

                call.handleAuthNextStep(
                    session = session,
                    nextStepInfo = AuthSessionNextStepRedirectData(requestUrl),
                    nextStepDescription = "Present the requested credential from your wallet: open the URL on this device, " +
                            "or scan it as a QR code. Then poll `${session.id}/vc/status` until the login completes.",
                )
            }

            get("status", {
                summary = "Check a verifiable credential login"
                response { HttpStatusCode.OK to { body<AuthSessionInformation>() } }
            }) {
                val session = call.getAuthSession(authContext)
                val config = session.lookupFlowMethodConfiguration<VerifiableCredentialAuthConfiguration>(this@VerifiableCredential)
                val verifierSessionId = session.getSessionData<VerifiableCredentialSessionData>(this@VerifiableCredential)?.verifierSessionId
                    ?: throw AuthSessionStateException("No credential verification started for this session; use vc/start")

                val verification = verification(config, verifierSessionId)
                when (val status = verification["status"]?.jsonPrimitive?.contentOrNull) {
                    "SUCCESSFUL" -> {
                        val identifier = identifierOf(verification, config)
                            ?: throw AuthenticationFailureException("The presented credential has no ${config.identifierClaim.joinToString("/")} claim")
                        val accountId = accountFor(session, identifier, functionAmendments?.get(AuthMethodFunctionAmendments.Registration))
                        call.handleAuthSuccess(session, authContext(call), accountId)
                    }

                    "FAILED", "EXPIRED" -> throw AuthenticationFailureException(
                        "Credential verification ${status.lowercase()}: ${verification["statusReason"]?.jsonPrimitive?.contentOrNull ?: "no reason given"}"
                    )

                    else -> call.respond(session.toInformation())
                }
            }
        }
    }

}
