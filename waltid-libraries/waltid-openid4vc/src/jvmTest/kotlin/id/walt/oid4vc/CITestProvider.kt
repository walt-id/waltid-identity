package id.walt.oid4vc

import com.nimbusds.jose.JWSHeader
import com.nimbusds.jwt.JWTParser
import id.walt.credentials.CredentialBuilder
import id.walt.credentials.CredentialBuilderType
import id.walt.credentials.issuance.Issuer.baseIssue
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.minutes

const val CI_PROVIDER_PORT = 9001
const val CI_PROVIDER_BASE_URL = "http://localhost:$CI_PROVIDER_PORT"

class CITestProvider : OpenIDCredentialIssuer(
    baseUrl = CI_PROVIDER_BASE_URL,
    config = CredentialIssuerConfig(
        credentialConfigurationsSupported = listOf(
            CredentialSupported(
                "VerifiableId", CredentialFormat.jwt_vc_json,
                cryptographicBindingMethodsSupported = setOf("did"), cryptographicSuitesSupported = setOf("ES256K"),
                types = listOf("VerifiableCredential", "VerifiableId"),
                customParameters = mapOf("foo" to JsonPrimitive("bar"))
            ),
            CredentialSupported(
                "VerifiableDiploma", CredentialFormat.jwt_vc_json,
                cryptographicBindingMethodsSupported = setOf("did"), cryptographicSuitesSupported = setOf("ES256K"),
                types = listOf("VerifiableCredential", "VerifiableAttestation", "VerifiableDiploma")
            )
        ).associateBy { it.id }
    )
) {

    // session management
    private val authSessions: MutableMap<String, IssuanceSession> = mutableMapOf()

    override fun getSession(id: String): IssuanceSession? = authSessions[id]
    override fun putSession(id: String, session: IssuanceSession) = authSessions.put(id, session)
    override fun getSessionByIdTokenRequestState(idTokenRequestState: String): IssuanceSession? {
        TODO("Not yet implemented")
    }

    override fun removeSession(id: String) = authSessions.remove(id)

    // crypto operations and credential issuance
    private val CI_TOKEN_KEY = runBlocking { JWKKey.generate(KeyType.RSA) }
    private val CI_DID_KEY = runBlocking { JWKKey.generate(KeyType.Ed25519) }
    val CI_ISSUER_DID = runBlocking { DidService.registerByKey("key", CI_DID_KEY).did }
    val deferredCredentialRequests = mutableMapOf<String, CredentialRequest>()
    var deferIssuance = false

    override fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject?, keyId: String?, privKey: Key?) =
        runBlocking { CI_TOKEN_KEY.signJws(payload.toString().toByteArray()) }

    fun getKeyFor(token: String): Key {
        return runBlocking { DidService.resolveToKey((JWTParser.parse(token).header as JWSHeader).keyID.substringBefore("#")) }.getOrThrow()
    }
    override fun verifyTokenSignature(target: TokenTarget, token: String) =
        runBlocking { (if(target == TokenTarget.PROOF_OF_POSSESSION) getKeyFor(token) else CI_TOKEN_KEY).verifyJws(token).isSuccess }

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
        return runBlocking { CredentialBuilder(CredentialBuilderType.W3CV2CredentialBuilder).apply {
            type = credentialRequest.types ?: listOf("VerifiableCredential")
            issuerDid = CI_ISSUER_DID
            subjectDid = holderKid
        }.buildW3C().baseIssue(CI_DID_KEY, CI_ISSUER_DID, holderKid, mapOf(), mapOf(), mapOf(), mapOf()) }.let {
            CredentialResult(CredentialFormat.jwt_vc_json, JsonPrimitive(it))
        }
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
                        val session = initializeAuthorization(authReq, 5.minutes, null)
                        call.respond(getPushedAuthorizationSuccessResponse(session).toJSON())
                    } catch (exc: AuthorizationError) {
                        call.respond(HttpStatusCode.BadRequest, exc.toPushedAuthorizationErrorResponse().toJSON())
                    }
                }
                get("/authorize") {
                    val authReq = AuthorizationRequest.fromHttpParameters(call.parameters.toMap())
                    try {
                        val authResp = if (authReq.responseType.contains(ResponseType.Code)) {
                            processCodeFlowAuthorization(authReq)
                        } else if (authReq.responseType.contains(ResponseType.Token)) {
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
                                if (authReq.responseType.contains(ResponseType.Code)) ResponseMode.query else ResponseMode.fragment
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
                    if (credentialOffer != null) {
                        call.respond(HttpStatusCode.Created, credentialOffer.toJSON())
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Issuance session with given ID not found")
                    }
                }
            }
        }.start()
    }
}

