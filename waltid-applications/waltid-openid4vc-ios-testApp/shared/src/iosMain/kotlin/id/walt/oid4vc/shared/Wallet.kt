package id.walt.oid4vc.shared

import id.walt.credentials.PresentationBuilder
import id.walt.crypto.IosKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.JwsUtils.jwsAlg
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.errors.PresentationError
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.interfaces.SimpleHttpResponse
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDClientConfig
import id.walt.oid4vc.providers.OpenIDCredentialWallet
import id.walt.oid4vc.providers.SIOPSession
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.responses.TokenErrorCode
import id.walt.sdjwt.JWTCryptoProvider
import id.walt.sdjwt.JwtVerificationResult
import id.walt.sdjwt.SDJwt
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.Parameters
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.encodeBase64
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.js.ExperimentalJsExport

//const val WALLET_BASE_URL = "https://waltid-issuer.internal.dev.udisp8.di-uisp-accenture.com"
const val WALLET_BASE_URL = "https://issuer.portal.walt.id"

internal sealed class DidMethod(val signingKeyInKeychainIdentifier: String) {
    abstract fun resolveDid(): String

    internal class DidWeb(val didWeb: String, signingKeyInKeychainIdentifier: String): DidMethod(signingKeyInKeychainIdentifier) {
        override fun resolveDid(): String {
            return didWeb
        }
    }

    internal class DidJwk(signingKeyInKeychainIdentifier: String): DidMethod(signingKeyInKeychainIdentifier) {
        override fun resolveDid(): String {
            val encodedJwk = runBlocking {
                IosKey.load(signingKeyInKeychainIdentifier, KeyType.secp256r1)?.exportJWK()?.encodeBase64() ?: throw IllegalStateException("Loading key problem")
            }

            return "did:jwk:$encodedJwk"
        }
    }
}

internal class TestCredentialWallet(
    private val didMethod: DidMethod,
    config: CredentialWalletConfig
) : OpenIDCredentialWallet<SIOPSession>(WALLET_BASE_URL, config) {

    private val jwtCryptoProvider: JWTCryptoProvider by lazy {
        object : JWTCryptoProvider {
            override fun sign(payload: JsonObject, keyID: String?, typ: String): String {
                val kid = requireNotNull(keyID) { "Requires kid" }

                val key = requireNotNull(IosKey.load(kid, KeyType.secp256r1)) { "Could not find key with kid in ios" }

                val header = buildJsonObject {
                    put("typ", typ)
                    put("alg", key.keyType.jwsAlg())
                }

                return runBlocking { key.signJws(payload.toString().toByteArray(), header.mapValues { it.value.jsonPrimitive.content })}
            }

            override fun verify(jwt: String): JwtVerificationResult {
                TODO("verify skipped for now")
            }
        }
    }

    private val sessionCache = mutableMapOf<String, SIOPSession>()
    private val ktorClient = HttpClient() {
        install(ContentNegotiation) {
            json()
        }
        followRedirects = false
    }

    init {
        println("Test wallet created")
    }

    override fun createSIOPSession(
        id: String,
        authorizationRequest: AuthorizationRequest?,
        expirationTimestamp: Instant
    ) = SIOPSession(id, authorizationRequest, expirationTimestamp)

    fun executePreAuthorizedCodeFlow(
        credentialOffer: CredentialOffer,
        client: OpenIDClientConfig,
        userPIN: String?
    ) : List<CredentialResponse> {
        val did = didMethod.resolveDid()
        return executePreAuthorizedCodeFlow(credentialOffer, did, client, userPIN)
    }

    override fun signToken(
        target: TokenTarget,
        payload: JsonObject,
        header: JsonObject?,
        keyId: String?,
        privKey: Key?
    ): String {
        print(target)
        println("// keyId: $keyId")
        println("// privKey: ${privKey}")

        return when (target) {
            TokenTarget.PROOF_OF_POSSESSION -> {
                runBlocking {
                    requireNotNull(IosKey.load(didMethod.signingKeyInKeychainIdentifier, KeyType.secp256r1)).run {

                        val headerWithAlg = header?.let {
                            JsonObject(it.toMutableMap().apply {
                                put("alg", JsonPrimitive(keyType.jwsAlg()))
                            })

                        } ?: buildJsonObject { }

                        runBlocking {
                            signJws(
                                plaintext = payload.toString().toByteArray(),
                                headers = headerWithAlg.jsonObject.mapValues { it.value.jsonPrimitive.content }
                            )
                        }
                    }
                }
            }

            else -> TODO("signToken $target not implemented yet")
        }
    }

    fun acceptOpenId4VPAuthorize(offerUri: String, kid: String) = credentialWallet.executeVpTokenAuthorization(Url(offerUri), didMethod.resolveDid(), testCIClientConfig)


//    override fun signToken(
//        target: TokenTarget,
//        payload: JsonObject,
//        header: JsonObject?,
//        keyId: String?
//    ): String {
////        val sdPayload = SDPayload.createSDPayload(payload, SDMap.Companion.fromJSON("{}"))
//// na zaklade tokentypu pouzit header/payload a spravne podpisat
////        println("sdPayload: $sdPayload")
//
////        jwtCryptoProvider.sign()
////
////        return SDJwt.sign(sdPayload, jwtCryptoProvider, keyId).jwt
//    }

    @OptIn(ExperimentalJsExport::class)
    override fun verifyTokenSignature(target: TokenTarget, token: String) =
        SDJwt.verifyAndParse(token, jwtCryptoProvider).signatureVerified

    override fun httpGet(url: Url, headers: Headers?): SimpleHttpResponse {
        return runBlocking {
            ktorClient.get(url) {
                headers {
                    headers?.let { appendAll(it) }
                }
            }.let { httpResponse ->
                SimpleHttpResponse(
                    httpResponse.status,
                    httpResponse.headers,
                    httpResponse.bodyAsText()
                )
            }
        }
    }

    override fun httpPostObject(
        url: Url,
        jsonObject: JsonObject,
        headers: Headers?
    ): SimpleHttpResponse {
        return runBlocking {
            ktorClient.post(url) {
                headers {
                    headers?.let { appendAll(it) }
                }
                contentType(ContentType.Application.Json)
                setBody(jsonObject)
            }.let { httpResponse ->
                SimpleHttpResponse(
                    httpResponse.status,
                    httpResponse.headers,
                    httpResponse.bodyAsText()
                )
            }
        }
    }

    override fun httpSubmitForm(
        url: Url,
        formParameters: Parameters,
        headers: Headers?
    ): SimpleHttpResponse {
        return runBlocking {
            ktorClient.submitForm(formParameters) {
                url(url)
                headers {
                    headers?.let { appendAll(it) }
                }
            }.let { httpResponse ->
                SimpleHttpResponse(
                    httpResponse.status,
                    httpResponse.headers,
                    httpResponse.bodyAsText()
                )
            }
        }
    }

    override fun generatePresentationForVPToken(
        session: SIOPSession,
        tokenRequest: TokenRequest
    ): PresentationResult {
        // find credential(s) matching the presentation definition
        // for this test wallet implementation, present all credentials in the wallet
        val presentationDefinition = session.presentationDefinition ?: throw PresentationError(
            TokenErrorCode.invalid_request,
            tokenRequest,
            session.presentationDefinition
        )
        val filterString =
            presentationDefinition.inputDescriptors.flatMap { it.constraints?.fields ?: listOf() }
                .firstOrNull { field -> field.path.any { it.contains("type") } }?.filter?.jsonObject?.get(
                    "pattern"
                )?.jsonPrimitive?.contentOrNull
                ?: presentationDefinition.inputDescriptors.flatMap {
                    it.schema?.map { it.uri } ?: listOf()
                }.firstOrNull()
        val presentationBuilder = PresentationBuilder()


        val signKey =
            requireNotNull(IosKey.load(didMethod.signingKeyInKeychainIdentifier, KeyType.secp256r1)){"Load key failed"}

        println("walletCredentials: $walletCredentials")

        val presentationJwtStr = runBlocking {
            presentationBuilder.apply {
                session.presentationDefinition?.id?.let { presentationId = it }
                did = didMethod.resolveDid()
                nonce = session.nonce
                audience = session.authorizationRequest?.clientId
//            addCredentials(credentialStore[filterString]?.let { listOf(JsonPrimitive(it)) } ?: listOf())
                addCredentials(walletCredentials.map { JsonPrimitive(it) })
            }.buildAndSign(signKey)
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


    override fun resolveDID(did: String): String {
        val didObj = runBlocking { DidService.resolve(did) }.getOrThrow()

        return (didObj["authentication"] ?: didObj["assertionMethod"]
        ?: didObj["verificationMethod"])?.jsonArray?.firstOrNull()?.let {
            if (it is JsonObject) it.jsonObject.get("id")?.jsonPrimitive?.content
            else it.jsonPrimitive.contentOrNull
        } ?: did
    }

    override fun getDidFor(session: SIOPSession): String {
        return didMethod.resolveDid()
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

    var walletCredentials: List<String> = listOf()
}