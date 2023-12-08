package id.walt.service.oidc4vc

import id.walt.crypto.keys.Key
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
import id.walt.oid4vc.providers.SIOPCredentialProvider
import id.walt.oid4vc.providers.SIOPProviderConfig
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.service.SSIKit2WalletService
import id.walt.service.SessionAttributes.HACK_outsideMappedSelectedCredentialsPerSession
import id.walt.service.SessionAttributes.HACK_outsideMappedSelectedDisclosuresPerSession
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
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
const val WALLET_BASE_URL = "http://localhost:${WALLET_PORT}"

class TestCredentialWallet(
    config: SIOPProviderConfig,
    val walletService: SSIKit2WalletService,
    val did: String
) : SIOPCredentialProvider<VPresentationSession>(WALLET_BASE_URL, config) {

    private val sessionCache = mutableMapOf<String, VPresentationSession>() // TODO not stateless because of oidc4vc library

    private val ktorClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json()
        }
    }

    override fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject?, keyId: String?): String {
        fun debugStateMsg() = "(target: $target, payload: $payload, header: $header, keyId: $keyId)"
        println("SIGNING TOKEN: ${debugStateMsg()}")

        keyId ?: throw IllegalArgumentException("No keyId provided for signToken ${debugStateMsg()}")

        val key = runBlocking { walletService.getKeyByDid(keyId) }
        println("KEY FOR SIGNING: $key")

        return runBlocking {
            val didDoc = ktorClient.post("https://core.ssikit.walt.id/v1/did/resolve") {
                headers { contentType(ContentType.Application.Json) }
                setBody("{ \"did\": \"${this@TestCredentialWallet.did}\" }")
            }.body<JsonObject>()
            val authKeyId = didDoc.get("authentication")!!.jsonArray.first().let {
                if (it is JsonObject) {
                    it.jsonObject["id"]!!.jsonPrimitive.content
                } else {
                    it.jsonPrimitive.content
                }
            }
            key.signJws(Json.encodeToString(payload).encodeToByteArray(), mapOf("typ" to "JWT", "kid" to authKeyId))
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

    override fun generatePresentationForVPToken(session: VPresentationSession, tokenRequest: TokenRequest): PresentationResult {
        println("=== GENERATING PRESENTATION FOR VP TOKEN - Session: $session")

        val selectedCredentials =
            HACK_outsideMappedSelectedCredentialsPerSession[session.authorizationRequest!!.state + session.authorizationRequest.presentationDefinition]!!
        val selectedDisclosures =
            HACK_outsideMappedSelectedDisclosuresPerSession[session.authorizationRequest.state + session.authorizationRequest.presentationDefinition]

        println("Selected credentials: $selectedCredentials")
        val matchedCredentials = walletService.getCredentialsByIds(selectedCredentials)
        println("Matched credentials: $matchedCredentials")

        println("Using disclosures: $selectedDisclosures")

        val credentialsPresented = matchedCredentials.map {
            if (selectedDisclosures?.containsKey(it.id) == true) {
                it.document + "~${selectedDisclosures[it.id]!!.joinToString("~") }"
            } else {
                it.document
            }
        }

        println("Credentials presented: $credentialsPresented")


        val vp = Json.encodeToString(
            mapOf(
                "sub" to this.did,
                "nbf" to Clock.System.now().minus(1.minutes).epochSeconds,
                "iat" to Clock.System.now().epochSeconds,
                "jti" to "urn:uuid:" + UUID.generateUUID().toString(),
                "iss" to this.did,
                "nonce" to (session.nonce ?: ""),
                "vp" to mapOf(
                    "@context" to listOf("https://www.w3.org/2018/credentials/v1"),
                    "type" to listOf("VerifiablePresentation"),
                    "id" to "urn:uuid:${UUID.generateUUID().toString().lowercase()}",
                    "holder" to this.did,
                    "verifiableCredential" to credentialsPresented
                )
            ).toJsonElement()
        )

        val key = runBlocking { walletService.getKeyByDid(this@TestCredentialWallet.did) }
        val signed = runBlocking {
            // TODO
            // FIXME
            val didDoc = ktorClient.post("https://core.ssikit.walt.id/v1/did/resolve") {
                headers { contentType(ContentType.Application.Json) }
                setBody("{ \"did\": \"${this@TestCredentialWallet.did}\" }")
            }.body<JsonObject>()
            val authKeyId = didDoc.get("authentication")!!.jsonArray.first().let {
                if (it is JsonObject) {
                    it.jsonObject["id"]!!.jsonPrimitive.content
                } else {
                    it.jsonPrimitive.content
                }
            }

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
                id = "submission 1",
                definitionId = session.presentationDefinition?.id ?: "",
                descriptorMap = matchedCredentials.map { it.document }.mapIndexed { index, vcJwsStr ->
                    val vcJws = vcJwsStr.base64UrlToBase64().decodeJws()
                    val type =
                        vcJws.payload["vc"]?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
                            ?: "VerifiableCredential"

                    DescriptorMapping(
                        id = type,
                        format = VCFormat.jwt_vp_json,  // jwt_vp_json
                        path = "$[$index]",
                        pathNested = DescriptorMapping(
                            format = VCFormat.jwt_vc_json,
                            path = "$.vp.verifiableCredential[0]",
                        )
                    )
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

    override fun resolveJSON(url: String): JsonObject? {
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

    override fun getSession(id: String) = sessionCache[id]
    override fun putSession(id: String, session: VPresentationSession) = sessionCache.put(id, session)
    override fun removeSession(id: String) = sessionCache.remove(id)

    fun parsePresentationRequest(request: String): AuthorizationRequest {
        return resolveVPAuthorizationParameters(AuthorizationRequest.fromHttpQueryString(Url(request).encodedQuery))
    }

    fun initializeAuthorization(
        authorizationRequest: AuthorizationRequest,
        expiresIn: Duration,
        selectedCredentials: Set<String>
    ): VPresentationSession {
        return super.initializeAuthorization(authorizationRequest, expiresIn).copy(selectedCredentialIds = selectedCredentials).also {
            putSession(it.id, it)
        }
    }
}
