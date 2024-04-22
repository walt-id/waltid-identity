package id.walt.webwallet.service.oidc4vc

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeySerialization
import id.walt.crypto.utils.Base64Utils.base64UrlToBase64
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.data.dif.VCFormat
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.interfaces.SimpleHttpResponse
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDCredentialWallet
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.webwallet.service.SessionAttributes.HACK_outsideMappedSelectedCredentialsPerSession
import id.walt.webwallet.service.SessionAttributes.HACK_outsideMappedSelectedDisclosuresPerSession
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.keys.KeysService
import id.walt.webwallet.utils.WalletHttpClients.getHttpClient
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlinx.uuid.UUID
import kotlinx.uuid.generateUUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

const val WALLET_PORT = 8001
const val WALLET_BASE_URL = "http://localhost:$WALLET_PORT"

class TestCredentialWallet(
    config: CredentialWalletConfig,
    val did: String
) : OpenIDCredentialWallet<VPresentationSession>(WALLET_BASE_URL, config) {

    private val sessionCache = mutableMapOf<String, VPresentationSession>() // TODO not stateless because of oidc4vc library

    private val ktorClient = getHttpClient()
    private val credentialsService = CredentialsService()

    suspend fun resolveDidAuthentication(did: String): String {
        return DidService.resolve(did).getOrElse {
            ktorClient.post("https://core.ssikit.walt.id/v1/did/resolve") {
                headers { contentType(ContentType.Application.Json) }
                setBody("{ \"did\": \"${this@TestCredentialWallet.did}\" }")
            }.body<JsonObject>()
        }["authentication"]!!.jsonArray.first().let {
            if (it is JsonObject) {
                it.jsonObject["id"]!!.jsonPrimitive.content
            } else {
                it.jsonPrimitive.content
            }
        }
    }

    override fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject?, keyId: String?, privKey: Key?): String {
        fun debugStateMsg() = "(target: $target, payload: $payload, header: $header, keyId: $keyId)"
        println("SIGNING TOKEN: ${debugStateMsg()}")

        keyId ?: throw IllegalArgumentException("No keyId provided for signToken ${debugStateMsg()}")

//        val key = runBlocking { walletService.getKeyByDid(keyId) }
        val key = runBlocking {
            DidService.resolveToKey(keyId).getOrThrow().let { KeysService.get(it.getKeyId()) }
                ?.let { KeySerialization.deserializeKey(it.document).getOrThrow() }
        } ?: error("Failed to retrieve the key")
        println("KEY FOR SIGNING: $key")

        return runBlocking {
            val authKeyId = resolveDidAuthentication(did)

            val payloadToSign = Json.encodeToString(payload).encodeToByteArray()
            key.signJws(payloadToSign, mapOf("typ" to "JWT", "kid" to authKeyId))
                .also { signed ->
                    key.getPublicKey().verifyJws(signed).also {
                        println("RE-VERIFICATION: $it")
                    }
                }
        }

        //JwtService.getService().sign(payload, keyId)
    }

    @OptIn(ExperimentalEncodingApi::class)
    override fun verifyTokenSignature(target: TokenTarget, token: String): Boolean {
        println("VERIFYING TOKEN: ($target) $token")
        val jwtHeader = runCatching {
            Json.parseToJsonElement(Base64.UrlSafe.decode(token.split(".")[0]).decodeToString()).jsonObject
        }.getOrElse {
            throw IllegalArgumentException(
                "Could not verify token signature, as JWT header could not be coded for token: $token, cause attached.",
                it
            )
        }

        val kid = jwtHeader["kid"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Could not verify token signature, as no kid in jwtHeader")

        val key = keyMapping[kid]
            ?: throw IllegalStateException("Could not verify token signature, as Key with keyId $kid has not been mapped")

        val result = runBlocking { key.verifyJws(token) }
        return result.isSuccess

        // JwtService.getService().verify(token).verified
    }

    override fun httpGet(url: Url, headers: Headers?): SimpleHttpResponse {
        return runBlocking {
            ktorClient.get(url) {
                headers {
                    headers?.forEach { s, strings -> headersOf(s, strings) }
                }
            }.let { SimpleHttpResponse(it.status, it.headers, it.bodyAsText()) }
        }
    }

    override fun httpPostObject(url: Url, jsonObject: JsonObject, headers: Headers?): SimpleHttpResponse {
        TODO("Not yet implemented")
    }

    override fun httpSubmitForm(url: Url, formParameters: Parameters, headers: Headers?): SimpleHttpResponse {
        TODO("Not yet implemented")
    }

    override fun generatePresentationForVPToken(session: VPresentationSession, tokenRequest: TokenRequest): PresentationResult {
        println("=== GENERATING PRESENTATION FOR VP TOKEN - Session: $session")

        val selectedCredentials =
            HACK_outsideMappedSelectedCredentialsPerSession[session.authorizationRequest!!.state + session.authorizationRequest.presentationDefinition]!!
        val selectedDisclosures =
            HACK_outsideMappedSelectedDisclosuresPerSession[session.authorizationRequest.state + session.authorizationRequest.presentationDefinition]

        println("Selected credentials: $selectedCredentials")
//        val matchedCredentials = walletService.getCredentialsByIds(selectedCredentials)
        val matchedCredentials = credentialsService.get(selectedCredentials)
        println("Matched credentials: $matchedCredentials")

        println("Using disclosures: $selectedDisclosures")

        val credentialsPresented = matchedCredentials.map {
            if (selectedDisclosures?.containsKey(it.id) == true) {
                it.document + "~${selectedDisclosures[it.id]!!.joinToString("~")}"
            } else {
                it.document
            }
        }

        println("Credentials presented: $credentialsPresented")

        val presentationId = (session.presentationDefinition?.id ?: "urn:uuid:${UUID.generateUUID().toString().lowercase()}")
        val vp = Json.encodeToString(
            mapOf(
                "sub" to this.did,
                "nbf" to Clock.System.now().minus(1.minutes).epochSeconds,
                "iat" to Clock.System.now().epochSeconds,
                "jti" to presentationId,
                "iss" to this.did,
                "nonce" to (session.nonce ?: ""),
                "aud" to session.authorizationRequest.clientId,
                "vp" to mapOf(
                    "@context" to listOf("https://www.w3.org/2018/credentials/v1"),
                    "type" to listOf("VerifiablePresentation"),
                    "id" to presentationId,
                    "holder" to this.did,
                    "verifiableCredential" to credentialsPresented
                )
            ).toJsonElement()
        )

//        val key = runBlocking { walletService.getKeyByDid(this@TestCredentialWallet.did) }
        val key = runBlocking {
            DidService.resolveToKey(did).getOrThrow().let { KeysService.get(it.getKeyId()) }
                ?.let { KeySerialization.deserializeKey(it.document).getOrThrow() }
        } ?: error("Failed to retrieve the key")
        val signed = runBlocking {
            val authKeyId = resolveDidAuthentication(this@TestCredentialWallet.did)

            key.signJws(
                vp.toByteArray(), mapOf(
                    "kid" to authKeyId,
                    "typ" to "JWT"
                )
            )
        }

        println("GENERATED VP: $signed")

        return PresentationResult(
            listOf(JsonPrimitive(signed)), PresentationSubmission(
                id = presentationId,
                definitionId = presentationId,
                descriptorMap = matchedCredentials.map { it.document }.mapIndexed { index, vcJwsStr ->
                    buildDescriptorMapping(session, index, vcJwsStr)
                }
            )
        )
        /*val presentation: String = Custodian.getService()
            .createPresentation(Custodian.getService().listCredentials().map { PresentableCredential(it) }, TEST_DID)
        return PresentationResult(
            listOf(Json.parseToJsonElement(presentation)), PresentationSubmission(
                "submission 1", presentationDefinition.id, listOf(
                    DescriptorMapping(
                        "presentation 1", VCFormat.jwt_vc, "$"
                    )
                )
            )
        )*/
    }

    //val TEST_DID: String = DidService.create(DidMethod.jwk)

    val keyMapping = HashMap<String, Key>() // TODO: Hack as this is non stateless because of oidc4vc lib API


    // FIXME: USE DB INSTEAD OF KEY MAPPING

    override fun resolveDID(did: String): String {
        val key = runBlocking { DidService.resolveToKey(did) }.getOrElse {
            throw IllegalArgumentException(
                "Could not resolve DID in CredentialWallet: $did, error cause attached.",
                it
            )
        }
        val keyId = runBlocking { key.getKeyId() }

        keyMapping[keyId] = key

        println("RESOLVED DID: $did to keyId: $keyId")

        return did

        /*val didDoc = runBlocking { DidService.resolve(did) }.getOrElse {
            throw IllegalArgumentException(
                "Could not resolve DID in CredentialWallet: $did, error cause attached.",
                it
            )
        }
        return (didDoc["authentication"] ?: didDoc["assertionMethod"]
        ?: didDoc["verificationMethod"])?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: did*/
        //return (didObj.authentication ?: didObj.assertionMethod ?: didObj.verificationMethod)?.firstOrNull()?.id ?: did
    }

    fun resolveJSON(url: String): JsonObject? {
        return runBlocking { ktorClient.get(url).body() }
    }

    override fun isPresentationDefinitionSupported(presentationDefinition: PresentationDefinition): Boolean {
        return true
    }

    override val metadata: OpenIDProviderMetadata
        get() = createDefaultProviderMetadata()

    override fun createSIOPSession(
        id: String,
        authorizationRequest: AuthorizationRequest?,
        expirationTimestamp: Instant
    ) = VPresentationSession(id, authorizationRequest, expirationTimestamp, setOf())

    override fun getDidFor(session: VPresentationSession): String {
        return did
    }

    override fun getSession(id: String) = sessionCache[id]
    override fun getSessionByIdTokenRequestState(idTokenRequestState: String): VPresentationSession? {
        TODO("Not yet implemented")
    }

    override fun putSession(id: String, session: VPresentationSession) = sessionCache.put(id, session)
    override fun removeSession(id: String) = sessionCache.remove(id)

    suspend fun parsePresentationRequest(request: String): AuthorizationRequest {
        val reqParams = Url(request).parameters.toMap()
        return resolveVPAuthorizationParameters(AuthorizationRequest.fromHttpParametersAuto(reqParams))
    }

    fun initializeAuthorization(
        authorizationRequest: AuthorizationRequest,
        expiresIn: Duration,
        selectedCredentials: Set<String>
    ): VPresentationSession {
        return super.initializeAuthorization(authorizationRequest, expiresIn, null).copy(selectedCredentialIds = selectedCredentials).also {
            putSession(it.id, it)
        }
    }

    private fun buildDescriptorMapping(session: VPresentationSession, index: Int, vcJwsStr: String) = let {
        val vcJws = vcJwsStr.base64UrlToBase64().decodeJws()
        val type = vcJws.payload["vc"]?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
            ?: "VerifiableCredential"

        DescriptorMapping(
            id = getDescriptorId(type, session.presentationDefinition),//session.presentationDefinition?.inputDescriptors?.get(index)?.id,
            format = VCFormat.jwt_vp,  // jwt_vp_json
            path = "$",
            pathNested = DescriptorMapping(
                id = getDescriptorId(
                    type,
                    session.presentationDefinition
                ),//session.presentationDefinition?.inputDescriptors?.get(index)?.id,
                format = VCFormat.jwt_vc_json, // jwt_vc_json
                path = "$.verifiableCredential[$index]", //.vp.verifiableCredentials
            )
        )
    }

    private fun getDescriptorId(type: String, presentationDefinition: PresentationDefinition?) =
        presentationDefinition?.inputDescriptors?.find {
            (it.name ?: it.id) == type
        }?.id
}
