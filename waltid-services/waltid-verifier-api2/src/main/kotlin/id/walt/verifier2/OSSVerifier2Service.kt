
package id.walt.verifier2

import id.walt.commons.config.ConfigManager
import id.walt.commons.fetchBinaryFile
import id.walt.commons.web.plugins.httpJson
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.ktornotifications.KtorNotifications.notifySessionUpdate
import id.walt.ktornotifications.SseNotifier
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.commons.config.list.TransactionDataProfile
import id.walt.commons.config.list.TransactionDataProfilesConfig
import id.walt.verifier2.data.SessionEvent
import id.walt.verifier2.data.Verification2Session
import id.walt.verifier2.data.VerificationSessionSetup
import id.walt.verifier2.data.Verifier2SessionUpdate
import id.walt.verifier2.handlers.authrequest.Verifier2AuthorizationRequestHandler.respondAuthorizationRequest
import id.walt.verifier2.handlers.authrequest.Verifier2RequestUriPostHandler.respondRequestUriPost
import id.walt.verifier2.handlers.vpresponse.Verifier2VPDirectPostHandler.respondHandleDirectPostResponse
import id.walt.verifier2.openapi.VerificationSessionCreateOpenApi
import id.walt.vical.*
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.route
import io.klogging.logger
import io.ktor.http.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.server.util.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer


private val log = logger("Verifier2Service")
private const val VERIFICATION_SESSION = "verification-session"
private const val VICAL = "vical"
private const val ENVELOPE_QUERY_PARAM = "envelope"

/**
 * Groups of verifier routes that can be registered independently.
 *
 * A deployment that only needs to receive presentations from wallets must not expose the
 * management surface: [SESSION_MANAGEMENT] lets any caller create a session with an arbitrary
 * DCQL query and its own verification policies, and [VICAL] makes the server fetch a
 * caller-supplied URL. Registering only the groups a deployment actually uses removes those
 * routes from the routing tree entirely, so they answer 404 instead of relying on a guard.
 */
enum class Verifier2RouteSurface {
    /** `POST create`, `GET {session}/info`, `SSE {session}/events` - verifier-operator surface. */
    SESSION_MANAGEMENT,

    /** `GET/POST {session}/request` and `POST {session}/response` - the wallet-facing surface. */
    CLIENT,

    /** `GET transaction-data-profiles` - discloses the configured transaction data profiles. */
    TRANSACTION_DATA_PROFILES,

    /** `POST vical/fetch` (fetches an arbitrary URL) and `POST vical/validate`. */
    VICAL,
    ;

    companion object {
        /** Everything, as a full verifier service exposes it. */
        val all: Set<Verifier2RouteSurface> = entries.toSet()

        /** Only what a wallet needs to answer a presentation request. */
        val clientOnly: Set<Verifier2RouteSurface> = setOf(CLIENT)
    }
}

object Verifier2Service {

    /**
     * Safe default for OSS startup and tests. Deployments inject a repository into [registerRoute]
     * rather than replacing global process state at runtime.
     *
     * Its limits come from [OSSVerifier2ServiceConfig], because the right ceiling depends on the deployment:
     * how large the presented credentials are and how much heap the pod was given. They were hard-coded when the
     * bounds were introduced, which left the one number an operator needs to raise after an out-of-memory
     * incident reachable only by rebuilding. Lazy so that the configuration is read when the service is wired
     * rather than when this class initialises, and absent configuration falls back to the documented defaults so
     * that tests and embedded use need no config file.
     */
    val defaultSessionRepository: VerificationSessionRepository by lazy {
        // Absent configuration is normal for tests and embedded use, and means the documented defaults.
        inMemorySessionRepositoryFor(
            try {
                ConfigManager.getConfig<OSSVerifier2ServiceConfig>()
            } catch (_: IllegalArgumentException) {
                null
            }
        )
    }

    /**
     * Update data for this session and send session update notifications
     */
    private fun updateSessionCallback(repository: VerificationSessionRepository): suspend (
        session: Verification2Session,
        event: SessionEvent,
        block: Verification2Session.() -> Unit
    ) -> Unit = { session, event, block ->
        log.trace { "Updating session due to '$event': ${session.id}" }
        val notificationConfig = session.notifications
        val updated = repository.update(session.id, block).session

        try {
            // publicView() keeps the ephemeral decryption keys and internal policy detail out of
            // the notification payload.
            val publicSession = updated.publicView()
            Verifier2SessionUpdate(publicSession.id, event, publicSession)
                .toKtorSessionUpdate()
                .notifySessionUpdate(publicSession.id, notificationConfig)
        } catch (exception: Exception) {
            log.warn(exception) { "Could not deliver verification session notification for ${updated.id}" }
        }
    }

    /**
     * Mark this session as failed
     */
    private val failSessionCallback: suspend (
        session: Verification2Session,
        event: SessionEvent,
        updateSession: suspend (Verification2Session, SessionEvent, block: Verification2Session.() -> Unit) -> Unit
    ) -> Unit = { session, event, updateSession ->
        updateSession(session, event) {
            this.status = Verification2Session.VerificationSessionStatus.FAILED
        }
    }

    /**
     * Registers the verifier routes for the [surfaces] this deployment needs.
     *
     * Defaults to [Verifier2RouteSurface.all] so a full verifier service is unchanged. Deployments
     * that only receive presentations should pass [Verifier2RouteSurface.clientOnly] - see the
     * enum for why the management surface must not be public.
     */
    fun Route.registerRoute(
        repository: VerificationSessionRepository = defaultSessionRepository,
        surfaces: Set<Verifier2RouteSurface> = Verifier2RouteSurface.all,
    ) {
        require(surfaces.isNotEmpty()) { "At least one verifier route surface must be registered" }
        val updateSessionCallback = updateSessionCallback(repository)
        route(VERIFICATION_SESSION) {
            if (Verifier2RouteSurface.SESSION_MANAGEMENT in surfaces) route("", {
                tags("Verification Session Management")
            }) {
                post<VerificationSessionSetup>("create", VerificationSessionCreateOpenApi.createDocs) { sessionSetup ->
                    val newSession = OSSVerifier2Manager.createVerificationSession(sessionSetup)
                    repository.create(newSession)
                    val creationResponse = newSession.toSessionCreationResponse()
                    call.respond(creationResponse)
                }

                route("{$VERIFICATION_SESSION}") {

                    get("info", {
                        summary = "View data of existing verification session"
                        request { pathParameter<String>(VERIFICATION_SESSION) }
                        response { HttpStatusCode.OK to { body<Verification2Session>() } }
                    }) {
                        val verifierSession =
                            repository.get(call.parameters.getOrFail(VERIFICATION_SESSION))?.session
                                ?: throw VerificationSessionNotFoundException(call.parameters.getOrFail(VERIFICATION_SESSION))
                        call.respond(verifierSession.publicView())
                    }

                    delete("", {
                        summary = "Delete a verification session"
                        description = "Removes the session and everything recorded about the verification. " +
                                "To keep the record but remove the personal data, delete its pii instead."
                        request { pathParameter<String>(VERIFICATION_SESSION) }
                        response {
                            HttpStatusCode.NoContent to { description = "Session deleted" }
                            HttpStatusCode.NotFound to { description = "No such session" }
                        }
                    }) {
                        val sessionId = call.parameters.getOrFail(VERIFICATION_SESSION)
                        if (!repository.delete(sessionId)) throw VerificationSessionNotFoundException(sessionId)
                        call.respond(HttpStatusCode.NoContent)
                    }

                    delete("pii", {
                        summary = "Purge the personal data of a verification session"
                        description = "Keeps the session and its outcome, and removes the presented credentials, " +
                                "the raw presentation, the ephemeral keys and the per-policy detail. For " +
                                "answering an erasure request without losing the record that a verification " +
                                "took place."
                        request { pathParameter<String>(VERIFICATION_SESSION) }
                        response {
                            HttpStatusCode.NoContent to { description = "Personal data purged" }
                            HttpStatusCode.NotFound to { description = "No such session" }
                        }
                    }) {
                        val sessionId = call.parameters.getOrFail(VERIFICATION_SESSION)
                        val current = repository.get(sessionId)
                            ?: throw VerificationSessionNotFoundException(sessionId)
                        // Compare-and-set rather than a blind write: a presentation may be landing on this
                        // session concurrently, and the purge must not resurrect the state it read.
                        val purged = current.session.copyForStorage().apply { deletePII() }
                        repository.compareAndSet(sessionId, current.version, purged)
                        call.respond(HttpStatusCode.NoContent)
                    }

                    route({
                        summary = "Receive update events via SSE about the verification session"
                        request { pathParameter<String>(VERIFICATION_SESSION) }
                    }) {
                        sse("events", serialize = { typeInfo, it ->
                            val serializer = httpJson.serializersModule.serializer(typeInfo.kotlinType!!)
                            httpJson.encodeToString(serializer, it)
                        }) {
                            val verifierSession =
                                repository.get(call.parameters.getOrFail(VERIFICATION_SESSION))?.session
                                    ?: throw VerificationSessionNotFoundException(call.parameters.getOrFail(VERIFICATION_SESSION))

                            // Get the flow for this specific target.
                            val sseFlow = SseNotifier.getSseFlow(verifierSession.id)

                            // This will suspend until the client disconnects.
                            send(JsonObject(emptyMap()))
                            sseFlow.collect { event -> send(event) }
                        }
                    }
                }
            }
            if (Verifier2RouteSurface.CLIENT in surfaces) route("{$VERIFICATION_SESSION}", {
                tags("Client endpoints")
            }) {
                get(
                    "request",
                    {
                        summary = "Wallets lookup the AuthorizationRequest here"
                        request { pathParameter<String>(VERIFICATION_SESSION) }
                        response { HttpStatusCode.OK to { body<AuthorizationRequest>() } }
                    }) {
                    val verificationSession =
                        repository.get(call.parameters.getOrFail(VERIFICATION_SESSION))?.session
                            ?: throw VerificationSessionNotFoundException(call.parameters.getOrFail(VERIFICATION_SESSION))

                    call.respondAuthorizationRequest(
                        verificationSession = verificationSession,
                        updateSessionCallback = updateSessionCallback
                    )
                }

                post("request", {
                        summary = "Wallets POST to the request URI (request_uri_method=post), optionally sending wallet_nonce"
                        request {
                            pathParameter<String>(VERIFICATION_SESSION)
                        }
                        response { HttpStatusCode.OK to { body<String> { description = "Signed request object JWT (application/oauth-authz-req+jwt)" } } }
                    }) {
                    val verificationSession =
                        repository.get(call.parameters.getOrFail(VERIFICATION_SESSION))?.session
                            ?: throw VerificationSessionNotFoundException(call.parameters.getOrFail(VERIFICATION_SESSION))

                    call.respondRequestUriPost(
                        verificationSession = verificationSession,
                        updateSessionCallback = updateSessionCallback,
                        resolveSigningKey = OSSVerifier2Manager::resolveRequestSigningKey,
                        resolveCrypto2SigningKey = OSSVerifier2Manager::resolveCrypto2RequestSigningKey,
                    )
                }

                route("") {
                    install(DoubleReceive)

                    post<String>(
                        "response",
                        {
                            summary = "Wallets respond to an AuthorizationRequest here"
                            request {
                                pathParameter<String>(VERIFICATION_SESSION)
                                body<String> { description = "" /* TODO */ }
                            }
                        }) { body ->
                        val sessionId = call.parameters.getOrFail(VERIFICATION_SESSION)
                        log.trace { "Received verification session response to session: $sessionId" }
                        val claim = repository.claimForProcessingWithOriginal(sessionId)
                        try {
                            call.respondHandleDirectPostResponse(
                                verificationSession = claim.claimed.session,
                                updateSessionCallback = updateSessionCallback,
                                failSessionCallback = failSessionCallback,
                                beforeRespond = { rejected ->
                                    if (rejected) repository.restoreProcessingClaim(claim)
                                },
                            )
                        } catch (exception: Exception) {
                            try {
                                withContext(NonCancellable) { repository.restoreProcessingClaim(claim) }
                            } catch (rollbackException: Exception) {
                                exception.addSuppressed(rollbackException)
                            }
                            throw exception
                        }
                    }
                }
            }
        }
        if (Verifier2RouteSurface.TRANSACTION_DATA_PROFILES in surfaces) get("transaction-data-profiles", {
            tags("Transaction Data")
            summary = "List available transaction data type profiles"
            response {
                HttpStatusCode.OK to { body<List<TransactionDataProfile>>() }
            }
        }) {
            val config = ConfigManager.getConfig<TransactionDataProfilesConfig>()
            call.respond(config.transactionDataProfiles)
        }

        if (Verifier2RouteSurface.VICAL in surfaces) route(VICAL) {
            route("", {
                tags("VICAL")
            }) {
                post("fetch", {
                    summary = "Fetches a VICAL from a remote host or from the file system and converts it to Base64"
                    request {
                        body<VicalFetchRequest> {
                            example("Remote VICAL") {
                                value = VicalFetchRequest(vicalUrl = "https://beta.nationaldts.com.au/api/vical")
                            }
                        }
                    }
                    response { HttpStatusCode.OK to { body<VicalFetchResponse>() } }
                }) {
                    val vicalFetchRequest = call.receive<VicalFetchRequest>()
                    val vicalCbor = fetchBinaryFile(vicalFetchRequest.vicalUrl)
                    call.respond(VicalFetchResponse(vicalCbor?.encodeToBase64()))
                }

                post("validate", {
                    summary = "Validates a VICAL by the provided verification key"
                    request {
                        body<VicalValidationRequest> {
                            example("Validate provided VICAL") {
                                value = Json.decodeFromString<VicalValidationRequest>(
                                    """
                                        {
                                           "verificationKey":{
                                              "type":"jwk",
                                              "jwk":{
                                                 "kty":"EC",
                                                 "crv":"P-256",
                                                 "x":"5n7yVdsDcdYRBAzb78_-6iAjpXCrIHId6qdJ7wwg1lE",
                                                 "y":"EFp0x5hbusr51g61xDoL9Y1nlVUqFZGBcSdsuBsjizM"
                                              }
                                           },
                                           "vicalBase64":"hEOhASahGCFZAy ..."
                                        }
                                    """.trimIndent()
                                )
                            }
                        }
                    }
                    response { HttpStatusCode.OK to { body<VicalValidationResponse>() } }
                }) {
                    val vicalValidationRequest = call.receive<VicalValidationRequest>()
                    log.debug { "Received VICAL validation request: $vicalValidationRequest" }
                    call.respond(VicalService.validateVical(vicalValidationRequest))
                }
            }
        }
    }
}
