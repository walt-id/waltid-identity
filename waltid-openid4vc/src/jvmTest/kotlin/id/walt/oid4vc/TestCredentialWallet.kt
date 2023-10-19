package id.walt.oid4vc

import id.walt.credentials.w3c.PresentableCredential
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.custodian.Custodian
import id.walt.model.DidMethod
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.ResponseMode
import id.walt.oid4vc.data.ResponseType
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.errors.PresentationError
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.interfaces.SimpleHttpResponse
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDCredentialWallet
import id.walt.oid4vc.providers.SIOPSession
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationDirectPostResponse
import id.walt.oid4vc.responses.AuthorizationErrorCode
import id.walt.oid4vc.responses.TokenErrorCode
import id.walt.services.did.DidService
import id.walt.services.jwt.JwtService
import io.kotest.common.runBlocking
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.java.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport

const val WALLET_PORT = 8001
const val WALLET_BASE_URL = "http://localhost:${WALLET_PORT}"

class TestCredentialWallet(
    config: CredentialWalletConfig
) : OpenIDCredentialWallet<SIOPSession>(WALLET_BASE_URL, config) {

    private val sessionCache = mutableMapOf<String, SIOPSession>()
    private val ktorClient = HttpClient(Java) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }
    }

    override fun createSIOPSession(
        id: String,
        authorizationRequest: AuthorizationRequest?,
        expirationTimestamp: Instant
    ) = SIOPSession(id, authorizationRequest, expirationTimestamp)

    override fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject?, keyId: String?) =
        JwtService.getService().sign(payload, keyId)

    @OptIn(ExperimentalJsExport::class)
    override fun verifyTokenSignature(target: TokenTarget, token: String) =
        JwtService.getService().verify(token).verified

    override fun httpGet(url: Url, headers: Headers?): SimpleHttpResponse {
        return runBlocking { ktorClient.get(url) {
            headers {
                headers?.let { appendAll(it) }
            }
        }.let { httpResponse -> SimpleHttpResponse(httpResponse.status, httpResponse.headers, httpResponse.bodyAsText()) } }
    }

    override fun httpPostObject(url: Url, jsonObject: JsonObject, headers: Headers?): SimpleHttpResponse {
        return runBlocking { ktorClient.post(url) {
            headers {
                headers?.let { appendAll(it) }
            }
            contentType(ContentType.Application.Json)
            setBody(jsonObject)
        }.let { httpResponse -> SimpleHttpResponse(httpResponse.status, httpResponse.headers, httpResponse.bodyAsText()) } }
    }

    override fun httpSubmitForm(url: Url, formParameters: Parameters, headers: Headers?): SimpleHttpResponse {
        return runBlocking { ktorClient.submitForm {
            url(url)
            headers {
                headers?.let { appendAll(it) }
            }
            parameters {
                appendAll(formParameters)
            }
        }.let { httpResponse -> SimpleHttpResponse(httpResponse.status, httpResponse.headers, httpResponse.bodyAsText()) } }
    }

    override fun generatePresentationForVPToken(session: SIOPSession, tokenRequest: TokenRequest): PresentationResult {
        // find credential(s) matching the presentation definition
        // for this test wallet implementation, present all credentials in the wallet
        val presentationDefinition = session.presentationDefinition ?: throw PresentationError(TokenErrorCode.invalid_request, tokenRequest, session.presentationDefinition)
        val filterString = presentationDefinition.inputDescriptors.flatMap { it.constraints?.fields ?: listOf() }
            .firstOrNull { field -> field.path.any { it.contains("type") } }?.filter?.jsonObject.toString()
        val presentationJwtStr = Custodian.getService()
            .createPresentation(
                Custodian.getService().listCredentials().filter { filterString.contains(it.type.last()) }.map {
                    PresentableCredential(
                        it,
                        selectiveDisclosure = null,
                        discloseAll = false
                    )
                }, TEST_DID, challenge = session.nonce
            )

        println("================")
        println("PRESENTATION IS: $presentationJwtStr")
        println("================")

        val presentationJws = presentationJwtStr.decodeJws()
        val jwtCredentials =
            ((presentationJws.payload["vp"]
                ?: throw IllegalArgumentException("VerifiablePresentation string does not contain `vp` attribute?"))
                .jsonObject["verifiableCredential"]
                ?: throw IllegalArgumentException("VerifiablePresentation does not contain verifiableCredential list?"))
                .jsonArray.map { it.jsonPrimitive.content }

        return PresentationResult(
            listOf(JsonPrimitive(presentationJwtStr)), PresentationSubmission(
                id = "submission 1",
                definitionId = session.presentationDefinition!!.id,
                descriptorMap = jwtCredentials.mapIndexed { index, vcJwsStr ->

                    val vcJws = vcJwsStr.decodeJws()
                    val type =
                        vcJws.payload["vc"]?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
                            ?: "VerifiableCredential"

                    DescriptorMapping(
                        id = type,
                        format = VCFormat.jwt_vp,  // jwt_vp_json
                        path = "$",
                        pathNested = DescriptorMapping(
                            format = VCFormat.jwt_vc,
                            path = "$.vp.verifiableCredential[0]",
                        )
                    )
                }
            )
        )
    }

    val TEST_DID: String = DidService.create(DidMethod.key)

    override fun resolveDID(did: String): String {
        val didObj = DidService.resolve(did)
        return (didObj.authentication ?: didObj.assertionMethod ?: didObj.verificationMethod)?.firstOrNull()?.id ?: did
    }

    override fun isPresentationDefinitionSupported(presentationDefinition: PresentationDefinition): Boolean {
        return true
    }

    override val metadata: OpenIDProviderMetadata
        get() = createDefaultProviderMetadata()

    override fun getSession(id: String) = sessionCache[id]
    override fun putSession(id: String, session: SIOPSession) = sessionCache.put(id, session)
    override fun removeSession(id: String) = sessionCache.remove(id)

    fun start() {
        embeddedServer(Netty, port = WALLET_PORT) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/.well-known/openid-configuration") {
                    call.respond(metadata.toJSON())
                }
                get("/authorize") {
                    val authReq = AuthorizationRequest.fromHttpParameters(call.parameters.toMap())
                    try {
                        if (authReq.responseType != ResponseType.vp_token.name) {
                            throw AuthorizationError(
                                authReq,
                                AuthorizationErrorCode.unsupported_response_type,
                                "Only response type vp_token is supported"
                            )
                        }
                        val tokenResponse = processImplicitFlowAuthorization(authReq)
                        val redirectLocation = if (authReq.responseMode == ResponseMode.direct_post) {
                            ktorClient.submitForm(
                                authReq.responseUri ?: throw AuthorizationError(
                                    authReq,
                                    AuthorizationErrorCode.invalid_request,
                                    "No response_uri parameter found for direct_post response mode"
                                ),
                                parametersOf(tokenResponse.toHttpParameters())
                            ).body<JsonObject>().let { AuthorizationDirectPostResponse.fromJSON(it) }.redirectUri
                        } else {
                            tokenResponse.toRedirectUri(
                                authReq.redirectUri ?: throw AuthorizationError(
                                    authReq,
                                    AuthorizationErrorCode.invalid_request,
                                    "No redirect uri found on authorization request"
                                ),
                                authReq.responseMode ?: ResponseMode.fragment
                            )
                        }
                        if (!redirectLocation.isNullOrEmpty()) {
                            call.response.apply {
                                status(HttpStatusCode.Found)
                                header(HttpHeaders.Location, redirectLocation)
                            }
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
            }
        }.start()
    }
}
