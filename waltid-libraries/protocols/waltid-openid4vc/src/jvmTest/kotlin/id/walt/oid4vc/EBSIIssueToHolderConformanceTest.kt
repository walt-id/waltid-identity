@file:OptIn(ExperimentalTime::class)

package id.walt.oid4vc

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.AuthorizationCodeResponse
import id.walt.oid4vc.responses.TokenResponse
import io.ktor.client.*
import io.ktor.client.engine.cio.*
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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.kotlincrypto.hash.sha2.SHA256
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

const val ISSUER_MOCK_PORT = 7018
const val ISSUER_MOCK_URL = "http://localhost:$ISSUER_MOCK_PORT"
const val ISSUER_MOCK_DID =
    "did:key:z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrJNL5rEcHRKkRBDnxzu2352jxSjTEFmM9hjTL2wMtzcTDjjDAQmPpQkaihjoAo8AygRr9M6yZsXHzWXnJRMNPzR3cCYbmvE9Q1sSQ1qzXHBo4iEc7Yb3MGu31ZAHKSd9Qx"

suspend fun getIssuerKey(): JWKKey {
    return JWKKey.importJWK("{\"kty\":\"EC\",\"x\":\"bo4FsmViF9au5-iCZbvEy-WZGaRes_eZdpIucmg4XH8\",\"y\":\"htYUXUmIc-IxyR6QMFPwXHXAgj__Fqw9kuSVtSyulhI\",\"crv\":\"P-256\",\"d\":\"UPzeJStN6Wg7zXULIlGVhYh4gG5RN-5knejePt6deqY\"}")
        .getOrThrow()
}


class EBSIIssueToHolderConformanceTest {

    fun startEBSIIssuerMockServer() {
        embeddedServer(Netty, port = ISSUER_MOCK_PORT) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                get("/client-mock") {
                    println("CLIENT MOCK called")
                    println(call.parameters.toString())
                }
                get("/jwks") {
                    call.respondText("{\"keys\":[{\"kty\":\"EC\",\"x\":\"bo4FsmViF9au5-iCZbvEy-WZGaRes_eZdpIucmg4XH8\",\"y\":\"htYUXUmIc-IxyR6QMFPwXHXAgj__Fqw9kuSVtSyulhI\",\"crv\":\"P-256\",\"kid\":\"z2dmzD81cgPx8Vki7JbuuMmFYrWPgYoytykUZ3eyqht1j9KbrJNL5rEcHRKkRBDnxzu2352jxSjTEFmM9hjTL2wMtzcTDjjDAQmPpQkaihjoAo8AygRr9M6yZsXHzWXnJRMNPzR3cCYbmvE9Q1sSQ1qzXHBo4iEc7Yb3MGu31ZAHKSd9Qx\"}]}")
                }
            }
        }.start()
    }

    val ktorClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }

        engine {
            https {
                https {
                    //disable https certificate verification
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(
                            chain: Array<out X509Certificate?>?,
                            authType: String?
                        ) {
                        }

                        override fun checkServerTrusted(
                            chain: Array<out X509Certificate?>?,
                            authType: String?
                        ) {
                        }

                        override fun getAcceptedIssuers(): Array<out X509Certificate?>? = null
                    }
                }
            }
        }
    }


    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun getCTIssueQualificationCredential() = runTest {

        startEBSIIssuerMockServer()
        // val taoIssuerServer = "http://localhost:3000/conformance/v3/issuer-mock"
        // val taoAuthServer = "http://localhost:3000/conformance/v3/auth-mock"
        val taoIssuerServer = "https://api-conformance.ebsi.eu/conformance/v3/issuer-mock"
        val taoAuthServer = "https://api-conformance.ebsi.eu/conformance/v3/auth-mock"

        //
        // Create Authorization Request
        //
        val clientId = ISSUER_MOCK_DID
        val codeVerifier = randomUUIDString() + randomUUIDString()
        val codeChallenge =
            codeVerifier.let { Base64.UrlSafe.encode(SHA256().digest(it.toByteArray(Charsets.UTF_8))).trimEnd('=') }

        val authReq = AuthorizationRequest(
            responseType = setOf(ResponseType.Code), clientId,
            scope = setOf("openid"),
            redirectUri = ISSUER_MOCK_URL,
            authorizationDetails = listOf(
                AuthorizationDetails(
                    format = CredentialFormat.jwt_vc,
                    type = "openid_credential",
                    types = listOf("VerifiableCredential", "VerifiableAttestation", "CTIssueQualificationCredential"),
                    locations = listOf(taoIssuerServer)
                )
            ),
            clientMetadata = OpenIDClientMetadata(
                customParameters = mapOf(
                    "jwks_uri" to JsonPrimitive("$ISSUER_MOCK_URL/jwks"),
                    "authorization_endpoint" to JsonPrimitive("openid:")
                )
            ),
            codeChallenge = codeChallenge,
            codeChallengeMethod = "S256"
        )

        val signedRequestObject = getIssuerKey().signJws(
            buildJsonObject {
                put(JWTClaims.Payload.issuer, ISSUER_MOCK_DID)
                put(JWTClaims.Payload.audience, taoAuthServer)
                put("client_id", clientId)
                put("response_type", "code")
                put("scope", "openid")
                put("client_metadata", authReq.clientMetadata!!.toJSON())
                put("redirect_uri", authReq.redirectUri)
                put("code_challenge", authReq.codeChallenge)
                put("code_challenge_method", authReq.codeChallengeMethod)
                put(
                    "authorization_details", JsonArray(
                        listOf(
                            buildJsonObject {
                                put("format", CredentialFormat.jwt_vc.value)
                                put(
                                    "types",
                                    JsonArray(
                                        listOf(
                                            "VerifiableCredential".toJsonElement(),
                                            "VerifiableAttestation".toJsonElement(),
                                            "CTIssueQualificationCredential".toJsonElement()
                                        )
                                    )
                                )
                                put("type", "openid_credential")
                                put("locations", JsonArray(listOf(taoIssuerServer.toJsonElement())))
                            }
                        )
                    )
                )
            }.toString().toByteArray(),
            mapOf(
                "kid" to (ISSUER_MOCK_DID + "#" + ISSUER_MOCK_DID.replaceRange(0..7, "")).toJsonElement(),
                "typ" to "JWT".toJsonElement(),
                "alg" to "ES256".toJsonElement()
            )
        )

        println(signedRequestObject)

        var httpResp = ktorClient.get("https://api-conformance.ebsi.eu/conformance/v3/auth-mock/authorize") {
            url {
                parameters.appendAll(parametersOf(authReq.toHttpParameters()))
                println(buildString())
            }
        }

        assertNotNull(httpResp.headers["location"])
        val idTokenReqUrl = httpResp.headers["location"]!!

        val idTokenReqMap = Url(idTokenReqUrl).parameters.toMap()
//        val idTokenReq = AuthorizationRequest.fromHttpParametersAuto(Url(idTokenReqUrl).parameters.toMap())  //error when parsing direct_post value

        assertEquals(302, httpResp.status.value)
        assertContains(idTokenReqMap["scope"]!!.first(), "openid")
        assertContains(idTokenReqMap["response_type"]!!.first(), ResponseType.IdToken.value)
        assertNotNull(idTokenReqMap["redirect_uri"]?.first())
        assertNotNull(idTokenReqMap["request_uri"]?.first())
        assertNotNull(idTokenReqMap["state"]?.first())
        assertNotNull(idTokenReqMap["nonce"]?.first())

        // Todo: IdTokenRequest Validation
        // Get Id Token request jwt from request_uri
        // Validate Id Token request signature using issuer-mock/jwks

        //
        // Sign and send Id Token
        //
        val idToken = getIssuerKey().signJws(
            buildJsonObject {
                put("iss", ISSUER_MOCK_DID)
                put("sub", ISSUER_MOCK_DID)
                put("aud", taoAuthServer)
                put("exp", Instant.DISTANT_FUTURE.epochSeconds)
                put("iat", Clock.System.now().epochSeconds)
                put("state", idTokenReqMap["state"]!!.first())
                put("nonce", idTokenReqMap["nonce"]!!.first())
            }.toString().toByteArray(),
            mapOf(
                "kid" to (ISSUER_MOCK_DID + "#" + ISSUER_MOCK_DID.replaceRange(0..7, "")).toJsonElement(),
                "typ" to "JWT".toJsonElement()
            )
        )

        println("ID Token is: $idToken")

        httpResp = ktorClient.submitForm(
            url = idTokenReqMap["redirect_uri"]!!.first(),
            formParameters = parameters {
                append(ResponseType.IdToken.value, idToken)
                append("state", idTokenReqMap["state"]!!.first())
            }
        )

        assertEquals(302, httpResp.status.value)
        assertNotNull(httpResp.headers["location"])

        val codeResp = AuthorizationCodeResponse.fromHttpQueryString(Url(httpResp.headers["Location"]!!).encodedQuery)
        println("Code is: $codeResp")

        //
        // Get Access Token
        //
        val tokenReq = TokenRequest.AuthorizationCode(
            clientId = ISSUER_MOCK_DID,
            redirectUri = ISSUER_MOCK_URL,
            code = codeResp.code!!,
            codeVerifier = codeVerifier
        )

        val tokenRespRaw = ktorClient.submitForm("$taoAuthServer/token", parametersOf(tokenReq.toHttpParameters()))
        assertEquals(HttpStatusCode.OK, tokenRespRaw.status)
        val tokenResp = TokenResponse.fromJSONString(tokenRespRaw.bodyAsText())
        assertNotNull(tokenResp.accessToken)
        println(tokenResp.accessToken)

        val jwtProof = ProofOfPossession.JWTProofBuilder(
            ISSUER_MOCK_DID,
            ISSUER_MOCK_DID, tokenResp.cNonce,
            ISSUER_MOCK_DID + "#" + ISSUER_MOCK_DID.replaceRange(0..7, ""),
            audience = taoIssuerServer
        ).build(getIssuerKey())
        println(jwtProof.jwt)

        //
        // Get CTIssueQualificationCredential
        //
        val credReq = CredentialRequest(
            CredentialFormat.jwt_vc,
            proof = jwtProof,
            credentialDefinition = CredentialDefinition(
                type = listOf(
                    "VerifiableCredential",
                    "VerifiableAttestation",
                    "CTIssueQualificationCredential"
                )
            )
        )

        val credRespRaw = ktorClient.post("$taoIssuerServer/credential") {
            bearerAuth(tokenResp.accessToken)
            contentType(ContentType.Application.Json)
            setBody(credReq.toJSONString())
        }
        println(credRespRaw.bodyAsText())
//        assertEquals(HttpStatusCode.OK, credRespRaw.status)
//        assertEquals(HttpStatusCode.Unauthorized, credRespRaw.status)
    }
}
