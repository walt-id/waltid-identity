
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

object Verifier2Service {

    /**
     * Safe default for OSS startup and tests. Deployments inject a repository into [registerRoute]
     * rather than replacing global process state at runtime.
     */
    val defaultSessionRepository: VerificationSessionRepository = InMemoryVerificationSessionRepository()

    /**
     * Update data for this session and send session update notifications
     */
    private fun updateSessionCallback(repository: VerificationSessionRepository): suspend (
        session: Verification2Session,
        event: SessionEvent,
        block: Verification2Session.() -> Unit
    ) -> Unit = { session, event, block ->
        log.trace { "Updating session due to '$event': ${session.id}" }
        val updated = repository.update(session.id, block).session

        try {
            Verifier2SessionUpdate(updated.id, event, updated)
                .toKtorSessionUpdate()
                .notifySessionUpdate(updated.id, updated.notifications)
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

    fun Route.registerRoute(repository: VerificationSessionRepository = defaultSessionRepository) {
        val updateSessionCallback = updateSessionCallback(repository)
        route(VERIFICATION_SESSION) {
            route("", {
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
                        call.respond(verifierSession)
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
            route("{$VERIFICATION_SESSION}", {
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
                        updateSessionCallback = updateSessionCallback
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
        get("transaction-data-profiles", {
            tags("Transaction Data")
            summary = "List available transaction data type profiles"
            response {
                HttpStatusCode.OK to { body<List<TransactionDataProfile>>() }
            }
        }) {
            val config = ConfigManager.getConfig<TransactionDataProfilesConfig>()
            call.respond(config.transactionDataProfiles)
        }

        route(VICAL) {
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
