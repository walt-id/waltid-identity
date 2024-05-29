package id.walt.oid4vc

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.crypto.bc.BouncyCastleProviderSingleton
import com.nimbusds.jose.jwk.ECKey
import id.walt.credentials.PresentationBuilder
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
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
import id.walt.sdjwt.SDJwt
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.SimpleJWTCryptoProvider
import io.ktor.client.*
import io.ktor.client.call.*
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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.json.*
import kotlin.js.ExperimentalJsExport

const val WALLET_PORT = 8001
const val WALLET_BASE_URL = "http://localhost:${WALLET_PORT}"

@OptIn(ExperimentalJsExport::class)
class TestCredentialWallet(
    config: CredentialWalletConfig
) : OpenIDCredentialWallet<SIOPSession>(WALLET_BASE_URL, config) {

    private val sessionCache = mutableMapOf<String, SIOPSession>()
    private val ktorClient = HttpClient() {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }
    }

    override fun createSIOPSession(
        id: String,
        authorizationRequest: AuthorizationRequest?,
        expirationTimestamp: Instant
    ) = SIOPSession(id, authorizationRequest, expirationTimestamp)

    override fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject?, keyId: String?, privKey: Key?) =
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
            .firstOrNull { field -> field.path.any { it.contains("type") } }?.filter?.jsonObject?.get("pattern")?.jsonPrimitive?.contentOrNull
            ?: presentationDefinition.inputDescriptors.flatMap { it.schema?.map { it.uri } ?: listOf() }.firstOrNull()
        val presentationBuilder = PresentationBuilder()
        val presentationJwtStr = runBlocking {
            presentationBuilder.apply {
                session.presentationDefinition?.id?.let { presentationId = it }
                did = TEST_DID
                nonce = session.nonce
                audience = session.authorizationRequest?.clientId
                addCredentials(credentialStore[filterString]?.let { listOf(JsonPrimitive(it)) } ?: listOf())
            }.buildAndSign(TEST_KEY)
        }

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
                id = session.presentationDefinition!!.id,
                definitionId = session.presentationDefinition!!.id,
                descriptorMap = jwtCredentials.mapIndexed { index, vcJwsStr ->

                    val vcJws = vcJwsStr.decodeJws()
                    val type =
                        vcJws.payload["vc"]?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
                            ?: "VerifiableCredential"

                    DescriptorMapping(
                        id = session.presentationDefinition?.inputDescriptors?.get(index)?.id,
                        format = VCFormat.jwt_vp,  // jwt_vp_json
                        path = "$",
                        pathNested = DescriptorMapping(
                            id = session.presentationDefinition?.inputDescriptors?.get(index)?.id,
                            format = VCFormat.jwt_vc,
                            path = "$.verifiableCredential[0]",
                        )
                    )
                }
            )
        )
    }

    val TEST_WALLET_DID_WEB = "did:web:entra.walt.id:holder"
    val TEST_WALLET_DID_ION =
        "did:ion:EiDh0EL8wg8oF-7rRiRzEZVfsJvh4sQX4Jock2Kp4j_zxg:eyJkZWx0YSI6eyJwYXRjaGVzIjpbeyJhY3Rpb24iOiJyZXBsYWNlIiwiZG9jdW1lbnQiOnsicHVibGljS2V5cyI6W3siaWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsInB1YmxpY0tleUp3ayI6eyJjcnYiOiJzZWNwMjU2azEiLCJraWQiOiI0OGQ4YTM0MjYzY2Y0OTJhYTdmZjYxYjYxODNlOGJjZiIsImt0eSI6IkVDIiwidXNlIjoic2lnIiwieCI6IlRLYVE2c0NvY1REc211ajl0VFI5OTZ0RlhwRWNTMkVKTi0xZ09hZGFCdmsiLCJ5IjoiMFRySVlIY2ZDOTNWcEV1dmotSFhUbnlLdDBzbmF5T013R1NKQTFYaURYOCJ9LCJwdXJwb3NlcyI6WyJhdXRoZW50aWNhdGlvbiJdLCJ0eXBlIjoiRWNkc2FTZWNwMjU2azFWZXJpZmljYXRpb25LZXkyMDE5In1dfX1dLCJ1cGRhdGVDb21taXRtZW50IjoiRWlCQnlkZ2R5WHZkVERob3ZsWWItQkV2R3ExQnR2TWJSLURmbDctSHdZMUhUZyJ9LCJzdWZmaXhEYXRhIjp7ImRlbHRhSGFzaCI6IkVpRGJxa05ldzdUcDU2cEJET3p6REc5bThPZndxamlXRjI3bTg2d1k3TS11M1EiLCJyZWNvdmVyeUNvbW1pdG1lbnQiOiJFaUFGOXkzcE1lQ2RQSmZRYjk1ZVV5TVlfaUdCRkMwdkQzeDNKVTB6V0VjWUtBIn19"

    val TEST_WALLET_DID_WEB_KEY = "{\"kty\":\"EC\",\"d\":\"uD-uxub011cplvr5Bd6MrIPSEUBsgLk-C1y3tnmfetQ\",\"use\":\"sig\",\"crv\":\"secp256k1\",\"kid\":\"48d8a34263cf492aa7ff61b6183e8bcf\",\"x\":\"TKaQ6sCocTDsmuj9tTR996tFXpEcS2EJN-1gOadaBvk\",\"y\":\"0TrIYHcfC93VpEuvj-HXTnyKt0snayOMwGSJA1XiDX8\"}"
    /*val TEST_KEY = runBlocking { JWKKey.generate(KeyType.Ed25519) }
    val TEST_DID: String = runBlocking {
        DidJwkRegistrar().registerByKey(TEST_KEY, DidJwkCreateOptions())
        //DidService.registerByKey("jwk", TEST_KEY)
    }.did*/

    // enable for Entra tests
    val TEST_KEY = runBlocking { JWKKey.importJWK(TEST_WALLET_DID_WEB_KEY).getOrThrow() }
    val TEST_DID: String = TEST_WALLET_DID_WEB

    val jwtCryptoProvider = runBlocking {
        // val key = OctetKeyPair.parse(TEST_KEY.jwk)
        // SimpleJWTCryptoProvider(JWSAlgorithm.EdDSA, Ed25519Signer(key), Ed25519Verifier(key.toPublicJWK()))
        // Enable for Entra tests
        val key = ECKey.parse(TEST_WALLET_DID_WEB_KEY)
        SimpleJWTCryptoProvider(JWSAlgorithm.ES256K, ECDSASigner(key).apply {
            jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
        }, ECDSAVerifier(key.toPublicJWK()).apply {
            jcaContext.provider = BouncyCastleProviderSingleton.getInstance()
        })
    }

    override fun resolveDID(did: String): String {
        val didObj = runBlocking { DidService.resolve(did) }.getOrThrow()
        return (didObj["authentication"] ?: didObj["assertionMethod"] ?: didObj["verificationMethod"])?.jsonArray?.firstOrNull()?.let {
            if(it is JsonObject) it.jsonObject["id"]?.jsonPrimitive?.content
            else it.jsonPrimitive.contentOrNull
        }?: did
    }

    override fun getDidFor(session: SIOPSession): String {
        return TEST_DID
    }

    override fun isPresentationDefinitionSupported(presentationDefinition: PresentationDefinition): Boolean {
        return true
    }

    override val metadata: OpenIDProviderMetadata
        get() = createDefaultProviderMetadata()

    override fun getSession(id: String) = sessionCache[id]
    override fun getSessionByIdTokenRequestState(idTokenRequestState: String): SIOPSession? {
        TODO("Not yet implemented")
    }

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
                        if (!authReq.responseType.contains(ResponseType.VpToken)) {
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

    val credentialStore = mapOf(
        // did:web:entra.walt.id:holder
        "MyID" to "eyJhbGciOiJFUzI1NksiLCJraWQiOiJkaWQ6d2ViOmVudHJhLndhbHQuaWQjNzVlZWFhMmY3M2RmNDA4MzkyZDUzNmNjOGFjMjM1NGJ2Y1NpZ25pbmdLZXktMTQwZDEiLCJ0eXAiOiJKV1QifQ.eyJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiTXlJRCJdLCJjcmVkZW50aWFsU3ViamVjdCI6eyJmaXJzdE5hbWUiOiJTZXYiLCJsYXN0TmFtZSI6IlN0YSJ9LCJjcmVkZW50aWFsU3RhdHVzIjp7ImlkIjoidXJuOnV1aWQ6YjY2OTRjZjUtNzNiZi00N2VmLTgzNGItZGFkZjY1OWNiZTFiP2JpdC1pbmRleD0yOCIsInR5cGUiOiJSZXZvY2F0aW9uTGlzdDIwMjFTdGF0dXMiLCJzdGF0dXNMaXN0SW5kZXgiOjI4LCJzdGF0dXNMaXN0Q3JlZGVudGlhbCI6ImRpZDp3ZWI6ZW50cmEud2FsdC5pZD9zZXJ2aWNlPUlkZW50aXR5SHViJnF1ZXJpZXM9VzNzaWJXVjBhRzlrSWpvaVEyOXNiR1ZqZEdsdmJuTlJkV1Z5ZVNJc0luTmphR1Z0WVNJNkltaDBkSEJ6T2k4dmR6TnBaQzV2Y21jdmRtTXRjM1JoZEhWekxXeHBjM1F0TWpBeU1TOTJNU0lzSW05aWFtVmpkRWxrSWpvaVlqWTJPVFJqWmpVdE56TmlaaTAwTjJWbUxUZ3pOR0l0WkdGa1pqWTFPV05pWlRGaUluMWQifX0sImp0aSI6InVybjpwaWM6ZjAxMmZjMzdkZmUyNDAwMzg5MmFlZGNjY2NiMTVlODIiLCJpc3MiOiJkaWQ6d2ViOmVudHJhLndhbHQuaWQiLCJzdWIiOiJkaWQ6d2ViOmVudHJhLndhbHQuaWQ6aG9sZGVyIiwiaWF0IjoxNzA0Nzk5NDI5LCJleHAiOjE3MDczOTE0Mjl9.hL6jdyKlWTVEikO6SUCJBT9n5aGrl98XPZXKZsC_qXZE2O-W81nLNFpKjAfaPnDH0V0Kun_iDTgmRjqbWylZEw"
        // did:ion
        //"MyID" to "eyJhbGciOiJFUzI1NksiLCJraWQiOiJkaWQ6d2ViOmVudHJhLndhbHQuaWQjNzVlZWFhMmY3M2RmNDA4MzkyZDUzNmNjOGFjMjM1NGJ2Y1NpZ25pbmdLZXktMTQwZDEiLCJ0eXAiOiJKV1QifQ.eyJ2YyI6eyJAY29udGV4dCI6WyJodHRwczovL3d3dy53My5vcmcvMjAxOC9jcmVkZW50aWFscy92MSJdLCJ0eXBlIjpbIlZlcmlmaWFibGVDcmVkZW50aWFsIiwiTXlJRCJdLCJjcmVkZW50aWFsU3ViamVjdCI6eyJmaXJzdE5hbWUiOiJTZXYiLCJsYXN0TmFtZSI6IlN0YSJ9LCJjcmVkZW50aWFsU3RhdHVzIjp7ImlkIjoidXJuOnV1aWQ6YjY2OTRjZjUtNzNiZi00N2VmLTgzNGItZGFkZjY1OWNiZTFiP2JpdC1pbmRleD0zMCIsInR5cGUiOiJSZXZvY2F0aW9uTGlzdDIwMjFTdGF0dXMiLCJzdGF0dXNMaXN0SW5kZXgiOjMwLCJzdGF0dXNMaXN0Q3JlZGVudGlhbCI6ImRpZDp3ZWI6ZW50cmEud2FsdC5pZD9zZXJ2aWNlPUlkZW50aXR5SHViJnF1ZXJpZXM9VzNzaWJXVjBhRzlrSWpvaVEyOXNiR1ZqZEdsdmJuTlJkV1Z5ZVNJc0luTmphR1Z0WVNJNkltaDBkSEJ6T2k4dmR6TnBaQzV2Y21jdmRtTXRjM1JoZEhWekxXeHBjM1F0TWpBeU1TOTJNU0lzSW05aWFtVmpkRWxrSWpvaVlqWTJPVFJqWmpVdE56TmlaaTAwTjJWbUxUZ3pOR0l0WkdGa1pqWTFPV05pWlRGaUluMWQifX0sImp0aSI6InVybjpwaWM6YWRhMTllY2M1ZTc4NDQ2MmI0M2Q3ZDFjYzUyZDdhNmQiLCJpc3MiOiJkaWQ6d2ViOmVudHJhLndhbHQuaWQiLCJzdWIiOiJkaWQ6aW9uOkVpRGgwRUw4d2c4b0YtN3JSaVJ6RVpWZnNKdmg0c1FYNEpvY2syS3A0al96eGc6ZXlKa1pXeDBZU0k2ZXlKd1lYUmphR1Z6SWpwYmV5SmhZM1JwYjI0aU9pSnlaWEJzWVdObElpd2laRzlqZFcxbGJuUWlPbnNpY0hWaWJHbGpTMlY1Y3lJNlczc2lhV1FpT2lJME9HUTRZVE0wTWpZelkyWTBPVEpoWVRkbVpqWXhZall4T0RObE9HSmpaaUlzSW5CMVlteHBZMHRsZVVwM2F5STZleUpqY25ZaU9pSnpaV053TWpVMmF6RWlMQ0pyYVdRaU9pSTBPR1E0WVRNME1qWXpZMlkwT1RKaFlUZG1aall4WWpZeE9ETmxPR0pqWmlJc0ltdDBlU0k2SWtWRElpd2lkWE5sSWpvaWMybG5JaXdpZUNJNklsUkxZVkUyYzBOdlkxUkVjMjExYWpsMFZGSTVPVFowUmxod1JXTlRNa1ZLVGkweFowOWhaR0ZDZG1zaUxDSjVJam9pTUZSeVNWbElZMlpET1ROV2NFVjFkbW90U0ZoVWJubExkREJ6Ym1GNVQwMTNSMU5LUVRGWWFVUllPQ0o5TENKd2RYSndiM05sY3lJNld5SmhkWFJvWlc1MGFXTmhkR2x2YmlKZExDSjBlWEJsSWpvaVJXTmtjMkZUWldOd01qVTJhekZXWlhKcFptbGpZWFJwYjI1TFpYa3lNREU1SW4xZGZYMWRMQ0oxY0dSaGRHVkRiMjF0YVhSdFpXNTBJam9pUldsQ1FubGtaMlI1V0haa1ZFUm9iM1pzV1dJdFFrVjJSM0V4UW5SMlRXSlNMVVJtYkRjdFNIZFpNVWhVWnlKOUxDSnpkV1ptYVhoRVlYUmhJanA3SW1SbGJIUmhTR0Z6YUNJNklrVnBSR0p4YTA1bGR6ZFVjRFUyY0VKRVQzcDZSRWM1YlRoUFpuZHhhbWxYUmpJM2JUZzJkMWszVFMxMU0xRWlMQ0p5WldOdmRtVnllVU52YlcxcGRHMWxiblFpT2lKRmFVRkdPWGt6Y0UxbFEyUlFTbVpSWWprMVpWVjVUVmxmYVVkQ1JrTXdka1F6ZUROS1ZUQjZWMFZqV1V0QkluMTkiLCJpYXQiOjE3MDQ4MDY3NzAsImV4cCI6MTcwNzM5ODc3MH0.JrsymjPljtdk3438bz3RL6y68PlQd5VeSA98c_nyZmkHkFXlKN648PxuxP67nDmPv-g2RC6JaZS7bzJqXqK61A"
    )
}
