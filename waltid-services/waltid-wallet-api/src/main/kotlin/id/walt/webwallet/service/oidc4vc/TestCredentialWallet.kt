@file:OptIn(ExperimentalUuidApi::class)

package id.walt.webwallet.service.oidc4vc

import COSE.AlgorithmID
import com.nimbusds.jose.jwk.ECKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.base64UrlToBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.dataelement.EncodedCBORElement
import id.walt.mdoc.dataelement.ListElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.NullElement
import id.walt.mdoc.dataretrieval.DeviceResponse
import id.walt.mdoc.doc.MDoc
import id.walt.mdoc.docrequest.MDocRequestBuilder
import id.walt.mdoc.mdocauth.DeviceAuthentication
import id.walt.oid4vc.OpenID4VP
import id.walt.oid4vc.data.CredentialFormat
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
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.WaltIdJWTCryptoProvider
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


import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

const val WALLET_PORT = 8001
const val WALLET_BASE_URL = "http://localhost:$WALLET_PORT"

class TestCredentialWallet(
    config: CredentialWalletConfig,
    val did: String,
) : OpenIDCredentialWallet<VPresentationSession>(WALLET_BASE_URL, config) {

    private val sessionCache = mutableMapOf<String, VPresentationSession>() // TODO not stateless because of oidc4vc library

    private val ktorClient = getHttpClient()
    private val credentialsService = CredentialsService()

    private suspend fun resolveDidAuthentication(did: String): String {
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

    override fun signToken(target: TokenTarget, payload: JsonObject, header: JsonObject?, keyId: String?, privKey: Key?): String =
        runBlocking {
            fun debugStateMsg() = "(target: $target, payload: $payload, header: $header, keyId: $keyId)"

            val key = privKey ?: keyId?.let { tryResolveKeyId(it) }
            ?: throw IllegalArgumentException("No key given or found for given keyId ${debugStateMsg()}")

            val authKeyId = resolveDidAuthentication(did)
            val payloadToSign = Json.encodeToString(payload).encodeToByteArray()
            val headersToSign = mapOf("typ" to "JWT".toJsonElement(), "kid" to authKeyId.toJsonElement()).plus(
                header?.toMap() ?: mapOf()
            )
            key.signJws(payloadToSign, headersToSign)
        }

    override fun signCWTToken(
        target: TokenTarget,
        payload: MapElement,
        header: MapElement?,
        keyId: String?,
        privKey: Key?,
    ): String = runBlocking {
        fun debugStateMsg() = "(target: $target, payload: $payload, header: $header, keyId: $keyId)"

        val key = privKey ?: keyId?.let { tryResolveKeyId(it) }
        ?: throw IllegalArgumentException("No key given or found for given keyId ${debugStateMsg()}")

        val ecKey = ECKey.parseFromPEMEncodedObjects(key.exportPEM()).toECKey()
        val cryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(key.getKeyId(), AlgorithmID.ECDSA_256, ecKey.toECPublicKey(), ecKey.toECPrivateKey())
            )
        )
        return@runBlocking cryptoProvider.sign1(payload.toCBOR(), header, null, keyId).toCBOR().encodeToBase64Url()
    }

    override fun verifyTokenSignature(target: TokenTarget, token: String): Boolean {
        println("VERIFYING TOKEN: ($target) $token")
        val jwtHeader = runCatching {
            Json.parseToJsonElement(token.split(".")[0].base64UrlDecode().decodeToString()).jsonObject
        }.getOrElse {
            throw IllegalArgumentException(
                "Could not verify token signature, as JWT header could not be coded for token: $token, cause attached.", it
            )
        }

        val kid = jwtHeader["kid"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("Could not verify token signature, as no kid in jwtHeader")

        val key = keyMapping[kid]
            ?: throw IllegalStateException("Could not verify token signature, as Key with keyId $kid has not been mapped")

        val result = runBlocking { key.verifyJws(token) }
        return result.isSuccess
    }

    override fun verifyCOSESign1Signature(target: TokenTarget, token: String): Boolean {
        TODO("Not yet implemented")
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
            HACK_outsideMappedSelectedCredentialsPerSession[session.authorizationRequest!!.state + session.authorizationRequest.presentationDefinition?.id]!!
        val selectedDisclosures =
            HACK_outsideMappedSelectedDisclosuresPerSession[session.authorizationRequest.state + session.authorizationRequest.presentationDefinition?.id]

        println("Selected credentials: $selectedCredentials")
//        val matchedCredentials = walletService.getCredentialsByIds(selectedCredentials)
        val matchedCredentials = credentialsService.get(selectedCredentials)
        println("Matched credentials: $matchedCredentials")

        println("Using disclosures: $selectedDisclosures")
        val key = runBlocking {
            runCatching {
                DidService.resolveToKey(did).getOrThrow().let { KeysService.get(it.getKeyId()) }
                    ?.let { KeyManager.resolveSerializedKey(it.document) }
            }
        }.getOrElse {
            throw IllegalArgumentException("Could not resolve key to sign JWS to generate presentation for vp_token", it)
        } ?: error("No key was resolved when trying to resolve key to sign JWS to generate presentation for vp_token")

        val jwtsPresented = CredentialFilterUtils.getJwtVcList(matchedCredentials, selectedDisclosures)
        println("jwtsPresented: $jwtsPresented")

        val sdJwtVCsPresented = runBlocking {
            matchedCredentials.filter { it.format == CredentialFormat.sd_jwt_vc }.map {
                // TODO: adopt selective disclosure selection (doesn't work with jwts other than sd_jwt anyway, like above)
                val documentWithDisclosures = if (selectedDisclosures?.containsKey(it.id) == true) {
                    it.document + "~${selectedDisclosures[it.id]!!.joinToString("~")}"
                } else {
                   it.document
                }

                SDJwtVC.parse(documentWithDisclosures).present(
                    true, audience = session.authorizationRequest.clientId, nonce = session.nonce ?: "",
                    WaltIdJWTCryptoProvider(mapOf(key.getKeyId() to key)), key.getKeyId()
                )
            }
        }.map { it.toString(true, true) }
        println("sdJwtVCsPresented: $sdJwtVCsPresented")

        val mdocsPresented = runBlocking {
            val matchingMDocs = matchedCredentials.filter { it.format == CredentialFormat.mso_mdoc }
            if (matchingMDocs.isNotEmpty()) {
                val mdocHandover = OpenID4VP.generateMDocOID4VPHandover(session.authorizationRequest, session.nonce!!)
                val ecKey = ECKey.parse(key.exportJWK()).toECKey()
                val cryptoProvider = SimpleCOSECryptoProvider(
                    listOf(
                        COSECryptoProviderKeyInfo(key.getKeyId(), AlgorithmID.ECDSA_256, ecKey.toECPublicKey(), ecKey.toECPrivateKey())
                    )
                )
                matchingMDocs.map { cred ->
                    val mdoc = MDoc.fromCBORHex(cred.document)
                    mdoc.presentWithDeviceSignature(
                        MDocRequestBuilder(mdoc.docType.value).also {
                            session.authorizationRequest.presentationDefinition!!.inputDescriptors.forEach { inputDescriptor ->
                                inputDescriptor.constraints!!.fields!!.forEach { field ->
                                    field.addToMdocRequest(it)
                                }
                            }
                        }.build(),
                        DeviceAuthentication(
                            sessionTranscript = ListElement(
                                listOf(
                                    NullElement(),
                                    NullElement(), //EncodedCBORElement(ephemeralReaderKey.getPublicKeyRepresentation()),
                                    mdocHandover
                                )
                            ), mdoc.docType.value, EncodedCBORElement(MapElement(mapOf()))
                        ), cryptoProvider, key.getKeyId()
                    )
                }
            } else listOf()
        }
        println("mdocsPresented: $mdocsPresented")

        val presentationId = (session.presentationDefinition?.id ?: "urn:uuid:${Uuid.random().toString().lowercase()}")

        val vp = if (jwtsPresented.isNotEmpty()) getVpJson(
            credentialsPresented = jwtsPresented,
            presentationId = presentationId,
            nonce = session.nonce,
            aud = session.authorizationRequest.clientId
        ) else null

        val signedJwtVP = if (!vp.isNullOrEmpty()) runBlocking {
            val authKeyId = resolveDidAuthentication(this@TestCredentialWallet.did)

            key.signJws(
                vp.toByteArray(), mapOf(
                    "kid" to authKeyId.toJsonElement(),
                    "typ" to "JWT".toJsonElement()
                )
            )
        } else null

        val deviceResponse = if (mdocsPresented.isNotEmpty()) mdocsPresented.let { DeviceResponse(it).toCBORBase64URL() } else null

        println("GENERATED VP: $signedJwtVP")

        // DONE: filter presentations if null
        // DONE: generate descriptor mappings based on type (vp or mdoc device response)
        // DONE: set root path of descriptor mapping based on whether there are multiple presentations or just one ("$" or "$[idx]")
        val presentations = listOf(signedJwtVP, deviceResponse).filterNotNull().plus(sdJwtVCsPresented).map { JsonPrimitive(it) }
        println("presentations: $presentations")

        val rootPathVP = "$" + (if (presentations.size == 2) "[0]" else "")
        val rootPathMDoc = "$" + (if (presentations.size == 2) "[1]" else "")
        return PresentationResult(
            presentations, PresentationSubmission(
                id = presentationId,
                definitionId = presentationId,
                descriptorMap = matchedCredentials.mapIndexed { index, credential ->
                    when (credential.format) {
                        CredentialFormat.mso_mdoc -> buildDescriptorMappingMDoc(
                            session.presentationDefinition,
                            index,
                            credential.document,
                            rootPathMDoc,
                        )
                        CredentialFormat.sd_jwt_vc -> buildDescriptorMappingSDJwtVC(
                            session.presentationDefinition,
                            index,
                            credential.document,
                            "$",
                        )
                        else -> buildDescriptorMappingJwtVP(
                            session.presentationDefinition,
                            index,
                            credential.document,
                            rootPathVP,
                        )
                    }
                }
            )
        )
    }

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
    }

    override fun isPresentationDefinitionSupported(presentationDefinition: PresentationDefinition): Boolean {
        return true
    }

    override val metadata: OpenIDProviderMetadata
        get() = createDefaultProviderMetadata()

    override fun createSIOPSession(
        id: String,
        authorizationRequest: AuthorizationRequest?,
        expirationTimestamp: Instant,
    ) = VPresentationSession(id, authorizationRequest, expirationTimestamp, setOf())

    override fun getDidFor(session: VPresentationSession): String {
        return did
    }

    override fun getSession(id: String) = sessionCache[id]
    override fun getSessionByAuthServerState(authServerState: String): VPresentationSession? {
        TODO("Not yet implemented")
    }

    override fun putSession(id: String, session: VPresentationSession) {
        sessionCache[id] = session
    }

    override fun removeSession(id: String) {
        sessionCache.remove(id)
    }

    suspend fun parsePresentationRequest(request: String): AuthorizationRequest {
        val reqParams = Url(request).parameters.toMap()
        return resolveVPAuthorizationParameters(AuthorizationRequest.fromHttpParametersAuto(reqParams))
    }

    fun initializeAuthorization(
        authorizationRequest: AuthorizationRequest,
        expiresIn: Duration,
        selectedCredentials: Set<String>,
    ): VPresentationSession {
        return super.initializeAuthorization(authorizationRequest, expiresIn, null).copy(selectedCredentialIds = selectedCredentials).also {
            putSession(it.id, it)
        }
    }

    fun getVpJson(credentialsPresented: List<String>, presentationId: String, nonce: String?, aud: String): String {
        println("Credentials presented: $credentialsPresented")
        return Json.encodeToString(
            mapOf(
                "sub" to this.did,
                "nbf" to Clock.System.now().minus(1.minutes).epochSeconds,
                "iat" to Clock.System.now().epochSeconds,
                "jti" to presentationId,
                "iss" to this.did,
                "nonce" to (nonce ?: ""),
                "aud" to aud,
                "vp" to mapOf(
                    "@context" to listOf("https://www.w3.org/2018/credentials/v1"),
                    "type" to listOf("VerifiablePresentation"),
                    "id" to presentationId,
                    "holder" to this.did,
                    "verifiableCredential" to credentialsPresented
                )
            ).toJsonElement()
        )
    }

    fun buildDescriptorMappingJwtVP(
        presentationDefinition: PresentationDefinition?,
        index: Int,
        vcJwsStr: String,
        rootPath: String = "$",
    ) = let {
        val vcJws = vcJwsStr.base64UrlToBase64().decodeJws()
        val type = vcJws.payload["vc"]?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
            ?: "VerifiableCredential"

        DescriptorMapping(
            id = getDescriptorId(type, presentationDefinition),//session.presentationDefinition?.inputDescriptors?.get(index)?.id,
            format = VCFormat.jwt_vp,  // jwt_vp_json
            path = rootPath,
            pathNested = DescriptorMapping(
                id = getDescriptorId(
                    type,
                    presentationDefinition,
                ),//session.presentationDefinition?.inputDescriptors?.get(index)?.id,
                format = VCFormat.jwt_vc_json, // jwt_vc_json
                path = "$rootPath.verifiableCredential[$index]", //.vp.verifiableCredentials
            )
        )
    }

    fun buildDescriptorMappingMDoc(
        presentationDefinition: PresentationDefinition?,
        index: Int,
        mdoc: String,
        rootPath: String = "$",
    ) = let {
        val mdoc = MDoc.fromCBORHex(mdoc)
        val type = mdoc.docType.value

        DescriptorMapping(
            id = getDescriptorId(type, presentationDefinition),
            format = VCFormat.mso_mdoc,
            path = rootPath,
            pathNested = DescriptorMapping(
                id = getDescriptorId(type, presentationDefinition),
                format = VCFormat.mso_mdoc,
                path = "$rootPath.documents[$index]",
            )
        )
    }

    fun buildDescriptorMappingSDJwtVC(
        presentationDefinition: PresentationDefinition?,
        index: Int,
        vcJwsStr: String,
        rootPath: String = "$",
    ) = let {
        val vcJws = vcJwsStr.base64UrlToBase64().decodeJws()
        val type = vcJws.payload["vc"]?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
            ?: "VerifiableCredential"

        DescriptorMapping(
            id = presentationDefinition!!.id,
            format = VCFormat.sd_jwt_vc,
            path = rootPath
        )
    }

    private fun getDescriptorId(
        type: String,
        presentationDefinition: PresentationDefinition?,
    ) =
        presentationDefinition?.inputDescriptors?.find {
            (it.name ?: it.id) == type
        }?.id

    private suspend fun tryResolveKeyId(keyId: String) = runCatching {
        val kid = keyId.takeIf { DidUtils.isDidUrl(it) }?.let {
            DidService.resolveToKey(it).getOrThrow().getKeyId()
        } ?: keyId
        KeysService.get(kid)?.let {
            KeyManager.resolveSerializedKey(it.document)
        }
    }.getOrElse { throw IllegalArgumentException("Could not resolve key to sign token", it) }
        ?: error("No key was resolved when trying to resolve key to sign token")
}
