package id.walt.oid4vc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.Ed25519Signer
import com.nimbusds.jose.crypto.Ed25519Verifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.OctetKeyPair
import id.walt.credentials.PresentationBuilder
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.LocalKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidCreateOptions
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.did.dids.registrar.local.jwk.DidJwkRegistrar
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
import id.walt.sdjwt.*
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
import java.io.File
import kotlin.js.ExperimentalJsExport

const val WALLET_PORT = 8001
const val WALLET_BASE_URL = "http://localhost:${WALLET_PORT}"

@OptIn(ExperimentalJsExport::class)
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
        SDJwt.sign(SDPayload.createSDPayload(payload, SDMap.Companion.fromJSON("{}")), jwtCryptoProvider, keyId).jwt

    @OptIn(ExperimentalJsExport::class)
    override fun verifyTokenSignature(target: TokenTarget, token: String) =
        SDJwt.verifyAndParse(token, jwtCryptoProvider).signatureVerified

    override fun httpGet(url: Url, headers: Headers?): SimpleHttpResponse {
        return runBlocking {
            ktorClient.get(url) {
                headers {
                    headers?.let { appendAll(it) }
                }
            }.let { httpResponse -> SimpleHttpResponse(httpResponse.status, httpResponse.headers, httpResponse.bodyAsText()) }
        }
    }

    override fun httpPostObject(url: Url, jsonObject: JsonObject, headers: Headers?): SimpleHttpResponse {
        return runBlocking {
            ktorClient.post(url) {
                headers {
                    headers?.let { appendAll(it) }
                }
                contentType(ContentType.Application.Json)
                setBody(jsonObject)
            }.let { httpResponse -> SimpleHttpResponse(httpResponse.status, httpResponse.headers, httpResponse.bodyAsText()) }
        }
    }

    override fun httpSubmitForm(url: Url, formParameters: Parameters, headers: Headers?): SimpleHttpResponse {
        return runBlocking {
            ktorClient.submitForm {
                url(url)
                headers {
                    headers?.let { appendAll(it) }
                }
                parameters {
                    appendAll(formParameters)
                }
            }.let { httpResponse -> SimpleHttpResponse(httpResponse.status, httpResponse.headers, httpResponse.bodyAsText()) }
        }
    }

    override fun generatePresentationForVPToken(session: SIOPSession, tokenRequest: TokenRequest): PresentationResult {
        // find credential(s) matching the presentation definition
        // for this test wallet implementation, present all credentials in the wallet
        val presentationDefinition = session.presentationDefinition ?: throw PresentationError(
            TokenErrorCode.invalid_request,
            tokenRequest,
            session.presentationDefinition
        )
        val filterString = presentationDefinition.inputDescriptors.flatMap { it.constraints?.fields ?: listOf() }
            .firstOrNull { field -> field.path.any { it.contains("type") } }?.filter?.jsonObject.toString()
        val presentationJwtStr = runBlocking { PresentationBuilder().apply {
            did = TEST_DID
            nonce = session.nonce
            addCredentials(credentialStore.filter { vc -> vc.toJsonObject()["type"]?.jsonArray?.map { it.jsonPrimitive.content }?.contains(filterString) ?: false }.map { it.toJsonObject() })
        }.buildAndSign(TEST_KEY) }

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

    val TEST_KEY = runBlocking { LocalKey.importJWK(File("./data/key/priv.jwk").readText().trimIndent()).getOrThrow() }
    val TEST_DID: String = runBlocking {
        DidJwkRegistrar().registerByKey(TEST_KEY, DidJwkCreateOptions())
        //DidService.registerByKey("jwk", TEST_KEY)
    }.did

    override fun resolveDID(did: String): String {
        val didObj = runBlocking { DidService.resolve(did) }.getOrThrow()
        return (didObj["authentication"] ?: didObj["assertionMethod"] ?: didObj["verificationMethod"])?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content ?: did
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

    val jwtCryptoProvider = runBlocking {
        val key = ECKey.parse(TEST_KEY.exportJWKObject().toString())
        SimpleJWTCryptoProvider(JWSAlgorithm.ES256K, ECDSASigner(key), ECDSAVerifier(key))
    }

    val credentialStore = listOf<W3CVC>(

    )
}
