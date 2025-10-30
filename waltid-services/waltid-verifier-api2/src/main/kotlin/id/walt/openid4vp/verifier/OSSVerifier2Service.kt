@file:OptIn(ExperimentalUuidApi::class)

package id.walt.openid4vp.verifier

import id.walt.commons.fetchBinaryFile
import id.walt.cose.Cose
import id.walt.cose.CoseCertificate
import id.walt.cose.toCoseSigner
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.ktornotifications.KtorNotifications.notifySessionUpdate
import id.walt.ktornotifications.SseNotifier
import id.walt.openid4vp.verifier.Verification2Session.VerificationSessionStatus
import id.walt.openid4vp.verifier.VerificationSessionCreator.VerificationSessionSetup
import id.walt.openid4vp.verifier.openapi.VerificationSessionCreateOpenApi
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi

private val log = logger("Verifier2Service")
private const val VERIFICATION_SESSION = "verification-session"
private const val VICAL = "vical"


object Verifier2Service {

    val sessions = HashMap<String, Verification2Session>()

    /**
     * Update data for this session and send session update notifications
     */
    val updateSessionCallback: suspend (
        session: Verification2Session,
        event: SessionEvent,
        block: Verification2Session.() -> Unit
    ) -> Unit = { session, event, block ->
        log.trace { "Updating session due to '$event': ${session.id}" }
        val newSession = session.apply {
            block.invoke(this)
        }
        sessions[newSession.id] = newSession

        Verifier2SessionUpdate(session.id, event, session)
            .toKtorSessionUpdate()
            .notifySessionUpdate(session.id, session.notifications)
    }

    /**
     * Mark this session as failed
     */
    val failSessionCallback: suspend (
        session: Verification2Session,
        event: SessionEvent,
        updateSession: suspend (Verification2Session, SessionEvent, block: Verification2Session.() -> Unit) -> Unit
    ) -> Unit = { session, event, updateSession ->
        updateSession(session, event) {
            this.status = VerificationSessionStatus.FAILED
        }
    }

    @OptIn(ExperimentalTime::class)
    fun Route.registerRoute() {
        route(VERIFICATION_SESSION) {
            route("", {
                tags("Verification Session Management")
            }) {
                post<VerificationSessionSetup>("create", VerificationSessionCreateOpenApi.createDocs) { sessionSetup ->
                    val newSession = OSSVerifier2Manager.createVerificationSession(sessionSetup)
                    sessions[newSession.id] = newSession
                    val creationResponse = newSession.toSessionCreationResponse()
                    call.respond(creationResponse)
                }

                route("{$VERIFICATION_SESSION}") {

                    get("info", {
                        summary = "View data of existing verification session"
                        request { pathParameter<String>(VERIFICATION_SESSION) }
                        response { HttpStatusCode.OK to { body<Verification2Session>() } }
                    }
                    ) {
                        val verifierSession =
                            sessions[call.parameters.getOrFail(VERIFICATION_SESSION)]
                                ?: throw IllegalArgumentException("Unknown session id")
                        call.respond(verifierSession)
                    }

                    route({
                        summary = "Receive update events via SSE about the verification session"
                        request { pathParameter<String>(VERIFICATION_SESSION) }
                    }) {
                        sse("$VERIFICATION_SESSION/events", serialize = { typeInfo, it ->
                            val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
                            Json.encodeToString(serializer, it)
                        }) {
                            val verifierSession =
                                sessions[call.parameters.getOrFail("sessionId")]
                                    ?: throw IllegalArgumentException("Unknown session id")

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
                        sessions[call.parameters.getOrFail(VERIFICATION_SESSION)]
                            ?: throw IllegalArgumentException("Unknown session id")

                    if (verificationSession.signedAuthorizationRequestJwt != null) {
                        // JAR (Signed)
                        call.respond(verificationSession.signedAuthorizationRequestJwt!!)
                    } else {
                        // Unsigned
                        call.respond(verificationSession.authorizationRequest)
                    }
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
                        val verificationSession = sessions[sessionId]

                        val urlParameters = call.receiveParameters()
                        val vpTokenString = urlParameters.getOrFail("vp_token")
                        val receivedState = urlParameters["state"]

                        log.trace { "Verification session data: state = $receivedState, vp_token = $vpTokenString" }

                        call.respond(
                            Verifier2DirectPostHandler.handleDirectPost(
                                verificationSession = verificationSession,
                                vpTokenString = vpTokenString,
                                receivedState = receivedState,
                                updateSessionCallback = updateSessionCallback,
                                failSessionCallback = failSessionCallback
                            )
                        )
                    }
                }
            }
        }
        route(VICAL) {
            route("", {
                tags("VICAL")
            }) {
                post("create", {
                    summary = "Creates a VICAL and converts it to Base64"
                    request {
                        body<VicalCreateRequest> {
                            example("VICAL Create Request") {
                                value = Json.decodeFromString<VicalCreateRequest>(
                                    """
                                    {
                                      "signingKey": {
                                        "type": "jwk",
                                        "jwk": {
                                          "kty": "EC",
                                          "d": "OWctGAH4JvvrolLjCmbLJqdK2jBRKpMQwNGD1Q6JnwM",
                                          "crv": "P-256",
                                          "kid": "0lwJbQHDxnh1Mzt3Hj9dL6mhv1YqvSeQQz0th68pY6w",
                                          "x": "DxnRmkJloU8UkKTZB2XQWN1PB4d2yb-9xg_uGLGNLwA",
                                          "y": "jUUlulJJ8jhLC1-2d8KeFCOGlsifDK5TZsc1NraewTk"
                                        }
                                      },
                                      "vicalProvider": "walt.id VICAL Service",
                                      "certificateInfoList": [
                                        {
                                          "certificate": "fda16464bc6ec46e1dfb2ab24a861bbbc70a9809e84a95e273ae0733a1ebc96d3ecd605653a2b42ee2c96b858d1e3589f31a81ba512ed28ba23a27d2dc5689c915957464cdfcc69e9a880ab264ed250b2a9b405af300161b8604f99364972c2501ece5a33eeb69e96ade8fb2829d20062e6dc8e322df1d4074008fcc2714af5de5f6f3f641fe766dc566c2626e5a3f42eff4307e0159d138aae45578c2417e25a5453a9ea220659ac4a7233a7a3bab5f506c3ed3d5779702c42cedc5535fd481c7e988bad8f2105861cdd9e13b7ccda61f1ed29fe59938427f6e677e946a821f3d5d16bdf7e7e932fb924881bebabc4c6b24007ee2230c752103495e27d7b2e4",
                                          "serialNumber": "01020304",
                                          "ski": "cd4e2253d812ea6eb3cb166820def12c91a70cd6",
                                          "docType": [
                                            "org.iso.18013.5.1.mDL"
                                          ],
                                          "certificateProfile": [
                                            "org.iso.18013.5.1.mDL.IACA"
                                          ],
                                          "issuingAuthority": "walt.id CA",
                                          "issuingCountry": "AT",
                                          "stateOrProvinceName": null,
                                          "issuer": null,
                                          "subject": null,
                                          "notBefore": null,
                                          "notAfter": null
                                        }
                                      ]
                                    } 
                                    """.trimIndent()
                                )
                            }
                        }
                    }
                    response { HttpStatusCode.OK to { body<VicalFetchResponse>() } }
                }) {
                    val vicalCreateRequest = call.receive<VicalCreateRequest>()

                    val signingKey = KeyManager.resolveSerializedKey(vicalCreateRequest.signingKey)

                    val iacaCertificateInfoList =
                        vicalCreateRequest.certificateInfoList.map { json -> json.toCertificateInfo() }

                    if (iacaCertificateInfoList.isEmpty()) {
                        throw IllegalArgumentException("No IACA certificate info provided")
                    }

                    val vicalData = VicalData(
                        vicalProvider = vicalCreateRequest.vicalProvider,
                        date = Clock.System.now(),
                        vicalIssueID = 1L, // TODO: generate
                        nextUpdate = Instant.parse("2026-08-01T00:00:00Z"), // TODO: configure
                        certificateInfos = iacaCertificateInfoList
                    )

                    val vicalProviderCertificate =
                        CoseCertificate(signingKey.getPublicKeyRepresentation()) // TODO: should be propper certificates

                    val signedVical = Vical.createAndSign(
                        vicalData = vicalData,
                        signer = signingKey.toCoseSigner(),
                        algorithmId = Cose.Algorithm.ES256, // ES256 for secp256r1 = -7
                        signerCertificateChain = listOf(vicalProviderCertificate)
                    )

                    val vicalCbor = signedVical.toTaggedCbor()

                    call.respond(VicalFetchResponse(vicalCbor.encodeToBase64()))
                }
            }

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
