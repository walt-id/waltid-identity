package id.walt.oid4vc

import id.walt.credentials.w3c.W3CIssuer
import id.walt.crypto.KeyAlgorithm
import id.walt.model.DidMethod
import id.walt.model.DidUrl
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.CredentialSupported
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.*
import id.walt.oid4vc.interfaces.CredentialResult
import id.walt.oid4vc.providers.CredentialIssuerConfig
import id.walt.oid4vc.providers.IssuanceSession
import id.walt.oid4vc.providers.OpenIDCredentialIssuer
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.BatchCredentialRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.CredentialErrorCode
import id.walt.oid4vc.util.randomUUID
import id.walt.services.did.DidService
import id.walt.services.jwt.JwtService
import id.walt.services.key.KeyService
import id.walt.signatory.ProofConfig
import id.walt.signatory.Signatory
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.minutes

const val CI_PROVIDER_PORT = 8000
const val CI_PROVIDER_BASE_URL = "http://localhost:$CI_PROVIDER_PORT"

class CITestProvider() : OpenIDCredentialIssuer(
    baseUrl = CI_PROVIDER_BASE_URL,
    config = CredentialIssuerConfig(
        credentialsSupported = listOf(
            CredentialSupported(
                CredentialFormat.jwt_vc_json, "VerifiableId",
                cryptographicBindingMethodsSupported = setOf("did"), cryptographicSuitesSupported = setOf("ES256K"),
                types = listOf("VerifiableCredential", "VerifiableId"),
                customParameters = mapOf("foo" to JsonPrimitive("bar"))
            ),
            CredentialSupported(
                CredentialFormat.jwt_vc_json, "VerifiableDiploma",
                cryptographicBindingMethodsSupported = setOf("did"), cryptographicSuitesSupported = setOf("ES256K"),
                types = listOf("VerifiableCredential", "VerifiableAttestation", "VerifiableDiploma")
            )
        )
    )
) {

    // session management
    private val authSessions: MutableMap<String, IssuanceSession> = mutableMapOf()

    override fun getSession(id: String): IssuanceSession? = authSessions[id]
    override fun putSession(id: String, session: IssuanceSession) = authSessions.put(id, session)
    override fun removeSession(id: String) = authSessions.remove(id)

    // crypto operations and credential issuance
    private val CI_TOKEN_KEY = KeyService.getService().generate(KeyAlgorithm.RSA)
    private val CI_DID_KEY = KeyService.getService().generate(KeyAlgorithm.EdDSA_Ed25519)
    val CI_ISSUER_DID = DidService.create(DidMethod.key, CI_DID_KEY.id)
    val deferredCredentialRequests = mutableMapOf<String, CredentialRequest>()
    var deferIssuance = false

    override fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject?, keyId: String?) =
        JwtService.getService().sign(keyId ?: CI_TOKEN_KEY.id, payload.toString())

    override fun verifyTokenSignature(target: TokenTarget, token: String) =
        JwtService.getService().verify(token).verified

    override fun generateCredential(credentialRequest: CredentialRequest): CredentialResult {
        if (deferIssuance) return CredentialResult(credentialRequest.format, null, randomUUID()).also {
            deferredCredentialRequests[it.credentialId!!] = credentialRequest
        }
        return doGenerateCredential(credentialRequest).also {
            // for testing purposes: defer next credential if multiple credentials are issued
            deferIssuance = !deferIssuance
        }
    }

    override fun getDeferredCredential(credentialID: String): CredentialResult {
        if (deferredCredentialRequests.containsKey(credentialID)) {
            return doGenerateCredential(deferredCredentialRequests[credentialID]!!)
        }
        throw DeferredCredentialError(CredentialErrorCode.invalid_request, message = "Invalid credential ID given")
    }

    private fun doGenerateCredential(credentialRequest: CredentialRequest): CredentialResult {
        if (credentialRequest.format == CredentialFormat.mso_mdoc) throw CredentialError(
            credentialRequest,
            CredentialErrorCode.unsupported_credential_format
        )
        val types = credentialRequest.types ?: credentialRequest.credentialDefinition?.types ?: throw CredentialError(
            credentialRequest,
            CredentialErrorCode.unsupported_credential_type
        )
        val proofHeader = credentialRequest.proof?.jwt?.let { parseTokenHeader(it) } ?: throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof,
            message = "Proof must be JWT proof"
        )
        val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content ?: throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof,
            message = "Proof JWT header must contain kid claim"
        )
        return Signatory.getService().issue(
            types.last(),
            ProofConfig(CI_ISSUER_DID, subjectDid = resolveDIDFor(holderKid)),
            issuer = W3CIssuer(baseUrl),
            storeCredential = false
        ).let {
            when (credentialRequest.format) {
                CredentialFormat.ldp_vc -> Json.decodeFromString<JsonObject>(it)
                else -> JsonPrimitive(it)
            }
        }.let { CredentialResult(credentialRequest.format, it) }
    }

    private fun resolveDIDFor(keyId: String): String {
        return DidUrl.from(keyId).did
    }

    fun start() {
        embeddedServer(Netty, port = CI_PROVIDER_PORT) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/.well-known/openid-configuration") {
                    call.respond(metadata.toJSON())
                }
                get("/.well-known/openid-credential-issuer") {
                    call.respond(metadata.toJSON())
                }
                post("/par") {
                    val authReq = AuthorizationRequest.fromHttpParameters(call.receiveParameters().toMap())
                    try {
                        val session = initializeAuthorization(authReq, 5.minutes)
                        call.respond(getPushedAuthorizationSuccessResponse(session).toJSON())
                    } catch (exc: AuthorizationError) {
                        call.respond(HttpStatusCode.BadRequest, exc.toPushedAuthorizationErrorResponse().toJSON())
                    }
                }
                get("/authorize") {
                    val authReq = AuthorizationRequest.fromHttpParameters(call.parameters.toMap())
                    try {
                        val authResp = if (authReq.responseType == ResponseType.code.name) {
                            processCodeFlowAuthorization(authReq)
                        } else if (authReq.responseType.contains(ResponseType.token.name)) {
                            processImplicitFlowAuthorization(authReq)
                        } else {
                            throw AuthorizationError(
                                authReq,
                                AuthorizationErrorCode.unsupported_response_type,
                                "Response type not supported"
                            )
                        }
                        val redirectUri = if (authReq.isReferenceToPAR) {
                            getPushedAuthorizationSession(authReq).authorizationRequest?.redirectUri
                        } else {
                            authReq.redirectUri
                        } ?: throw AuthorizationError(
                            authReq,
                            AuthorizationErrorCode.invalid_request,
                            "No redirect_uri found for this authorization request"
                        )
                        call.response.apply {
                            status(HttpStatusCode.Found)
                            val defaultResponseMode =
                                if (authReq.responseType == ResponseType.code.name) ResponseMode.query else ResponseMode.fragment
                            header(
                                HttpHeaders.Location,
                                authResp.toRedirectUri(redirectUri, authReq.responseMode ?: defaultResponseMode)
                            )
                        }
                    } catch (authExc: AuthorizationError) {
                        call.response.apply {
                            status(HttpStatusCode.Found)
                            header(HttpHeaders.Location, URLBuilder(authExc.authorizationRequest.redirectUri!!).apply {
                                parameters.appendAll(
                                    parametersOf(
                                        authExc.toAuthorizationErrorResponse().toHttpParameters()
                                    )
                                )
                            }.buildString())
                        }
                    }
                }
                post("/token") {
                    val params = call.receiveParameters().toMap()
                    val tokenReq = TokenRequest.fromHttpParameters(params)
                    try {
                        val tokenResp = processTokenRequest(tokenReq)
                        call.respond(tokenResp.toJSON())
                    } catch (exc: TokenError) {
                        call.respond(HttpStatusCode.BadRequest, exc.toAuthorizationErrorResponse().toJSON())
                    }
                }
                post("/credential") {
                    val accessToken = call.request.header(HttpHeaders.Authorization)?.substringAfter(" ")
                    if (accessToken.isNullOrEmpty() || !verifyTokenSignature(TokenTarget.ACCESS, accessToken)) {
                        call.respond(HttpStatusCode.Unauthorized)
                    } else {
                        val credReq = CredentialRequest.fromJSON(call.receive<JsonObject>())
                        try {
                            call.respond(generateCredentialResponse(credReq, accessToken).toJSON())
                        } catch (exc: CredentialError) {
                            call.respond(HttpStatusCode.BadRequest, exc.toCredentialErrorResponse().toJSON())
                        }
                    }
                }
                post("/credential_deferred") {
                    val accessToken = call.request.header(HttpHeaders.Authorization)?.substringAfter(" ")
                    if (accessToken.isNullOrEmpty() || !verifyTokenSignature(
                            TokenTarget.DEFERRED_CREDENTIAL,
                            accessToken
                        )
                    ) {
                        call.respond(HttpStatusCode.Unauthorized)
                    } else {
                        try {
                            call.respond(generateDeferredCredentialResponse(accessToken).toJSON())
                        } catch (exc: DeferredCredentialError) {
                            call.respond(HttpStatusCode.BadRequest, exc.toCredentialErrorResponse().toJSON())
                        }
                    }
                }
                post("/batch_credential") {
                    val accessToken = call.request.header(HttpHeaders.Authorization)?.substringAfter(" ")
                    if (accessToken.isNullOrEmpty() || !verifyTokenSignature(TokenTarget.ACCESS, accessToken)) {
                        call.respond(HttpStatusCode.Unauthorized)
                    } else {
                        val req = BatchCredentialRequest.fromJSON(call.receive())
                        try {
                            call.respond(generateBatchCredentialResponse(req, accessToken).toJSON())
                        } catch (exc: BatchCredentialError) {
                            call.respond(HttpStatusCode.BadRequest, exc.toBatchCredentialErrorResponse().toJSON())
                        }
                    }
                }
                get("/credential_offer/{session_id}") {
                    val sessionId = call.parameters["session_id"]!!
                    val credentialOffer = getSession(sessionId)?.credentialOffer
                    if(credentialOffer != null) {
                        call.respond(HttpStatusCode.Created, credentialOffer.toJSON())
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Issuance session with given ID not found")
                    }
                }
            }
        }.start()
    }
}

