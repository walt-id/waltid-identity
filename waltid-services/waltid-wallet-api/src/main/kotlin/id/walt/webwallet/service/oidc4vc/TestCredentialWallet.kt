@file:OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)

package id.walt.webwallet.service.oidc4vc

import id.walt.cose.*
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.base64UrlToBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import com.nimbusds.jose.jwk.ECKey
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.encoding.MdocCbor
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.handover.OpenID4VPHandover
import id.walt.mdoc.objects.handover.OpenID4VPHandoverInfo
import id.walt.mdoc.objects.sha256
import id.walt.mdoc.parser.MdocParser
import org.cose.java.AlgorithmID
import org.cose.java.OneKey
import id.walt.oid4vc.data.CredentialFormat
import id.walt.oid4vc.data.OpenIDProviderMetadata
import id.walt.oid4vc.data.dif.DescriptorMapping
import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.interfaces.PresentationResult
import id.walt.oid4vc.interfaces.SimpleHttpResponse
import id.walt.oid4vc.providers.CredentialWalletConfig
import id.walt.oid4vc.providers.OpenIDCredentialWallet
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.AuthorizationRequest
import id.walt.oid4vc.requests.TokenRequest
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.WaltIdJWTCryptoProvider
import id.walt.w3c.utils.VCFormat
import id.walt.webwallet.service.SessionAttributes.HACK_outsideMappedSelectedCredentialsPerSession
import id.walt.webwallet.service.SessionAttributes.HACK_outsideMappedSelectedDisclosuresPerSession
import id.walt.webwallet.service.credentials.CredentialsService
import id.walt.webwallet.service.keys.KeysService
import id.walt.webwallet.utils.WalletHttpClients.getHttpClient
import io.klogging.Klogging
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse as MdocDeviceResponse

const val WALLET_PORT = 8001
const val WALLET_BASE_URL = "http://localhost:$WALLET_PORT"

class TestCredentialWallet(
    val account: Uuid,
    val wallet: Uuid,
    config: CredentialWalletConfig,
    val did: String,
) : OpenIDCredentialWallet<VPresentationSession>(WALLET_BASE_URL, config), Klogging {

    private val sessionCache =
        mutableMapOf<String, VPresentationSession>() // TODO not stateless because of oidc4vc library

    private val ktorClient = getHttpClient()
    private val credentialsService = CredentialsService()

    private suspend fun resolveDidAuthentication(did: String): String {
        return DidService.resolve(did).getOrThrow()["authentication"]!!.jsonArray.first().let {
            if (it is JsonObject) {
                it.jsonObject["id"]!!.jsonPrimitive.content
            } else {
                it.jsonPrimitive.content
            }
        }
    }

    override fun signToken(
        target: TokenTarget,
        payload: JsonObject,
        header: JsonObject?,
        keyId: String?,
        privKey: Key?
    ): String =
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
        // For CWT proofs, we need to use the old library's SimpleCOSECryptoProvider
        // because the issuer still uses the old library's COSESign1Utils.extractHolderKey to parse it.
        // The header parameter contains the coseKey in the old library's MapElement format.
        fun debugStateMsg() = "(target: $target, payload: $payload, header: $header, keyId: $keyId)"

        val key = privKey ?: keyId?.let { tryResolveKeyId(it) }
        ?: throw IllegalArgumentException("No key given or found for given keyId ${debugStateMsg()}")

        // Convert Key to old library's OneKey format
        val oneKey = OneKey(
            ECKey.parse(key.getPublicKey().exportJWK()).toECPublicKey(),
            if (key.hasPrivateKey) ECKey.parse(key.exportJWK()).toECPrivateKey() else null
        )
        
        // Create COSECryptoProviderKeyInfo for signing
        val keyInfo = COSECryptoProviderKeyInfo(
            keyID = key.getKeyId(),
            algorithmID = AlgorithmID.ECDSA_256,
            publicKey = oneKey.AsPublicKey(),
            privateKey = oneKey.AsPrivateKey()
        )
        
        // Create SimpleCOSECryptoProvider with the key
        val cryptoProvider = SimpleCOSECryptoProvider(listOf(keyInfo))
        
        // Use the provided header (which contains coseKey) or create empty headers
        // Note: CWTProofBuilder passes headers as headersProtected, so we do the same here
        val headersToUse = header ?: MapElement(emptyMap())
        
        // Sign using the old library's crypto provider
        // The headers (including coseKey) must be in headersProtected because
        // COSESign1Utils.extractHolderKey looks in the protected headers
        val signedCoseSign1 = cryptoProvider.sign1(
            payload = payload.toCBOR(),
            headersProtected = headersToUse, // Headers with coseKey go here
            headersUnprotected = null,
            keyID = key.getKeyId()
        )
        
        // Encode to base64Url
        return@runBlocking signedCoseSign1.toCBOR().encodeToBase64Url()
    }

    override fun verifyTokenSignature(target: TokenTarget, token: String): Boolean = runBlocking {
        logger.debug("VERIFYING TOKEN: ({target}) {token}", target, token)
        val jwtHeader = runCatching {
            Json.parseToJsonElement(token.split(".")[0].base64UrlDecode().decodeToString()).jsonObject
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
        result.isSuccess
    }

    override fun verifyCOSESign1Signature(target: TokenTarget, token: String): Boolean = runBlocking {
        return@runBlocking runCatching {
            // Decode base64Url token
            val tokenBytes = token.base64UrlDecode()
            
            // Parse as CoseSign1
            val coseSign1 = CoseSign1.fromTagged(tokenBytes)
            
            // Decode protected headers from ByteArray
            val protectedHeaders = if (coseSign1.protected.isEmpty()) {
                CoseHeaders()
            } else {
                coseCompliantCbor.decodeFromByteArray(CoseHeaders.serializer(), coseSign1.protected)
            }
            
            // Get the key from keyMapping or resolve it
            val keyIdBytes = protectedHeaders.kid
                ?: coseSign1.unprotected.kid
                ?: throw IllegalArgumentException("No key ID found in COSE Sign1 headers")
            
            val keyId = keyIdBytes.decodeToString()
            val key = keyMapping[keyId] ?: tryResolveKeyId(keyId)
                ?: throw IllegalStateException("Could not resolve key with keyId $keyId")
            
            // Verify the signature using CoseVerifier
            val verifier = key.toCoseVerifier()
            coseSign1.verify(verifier)
        }.fold(
            onSuccess = { it },
            onFailure = { 
                logger.error(it) { "Failed to verify COSE Sign1 signature: ${it.message}" }
                false
            }
        )
    }

    override fun httpGet(url: Url, headers: Headers?): SimpleHttpResponse {
        return runBlocking {
            ktorClient.get(url) {
                headers {
                    headers?.forEach { s, strings -> headersOf(s, strings) }
                }
            }.let {
                SimpleHttpResponse(it.status, it.headers, it.bodyAsText())
            }
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
            }.let { httpResponse ->
                SimpleHttpResponse(
                    httpResponse.status,
                    httpResponse.headers,
                    httpResponse.bodyAsText()
                )
            }
        }
    }

    override fun httpSubmitForm(url: Url, formParameters: Parameters, headers: Headers?): SimpleHttpResponse {
        return runBlocking {
            ktorClient.submitForm(
                url = url.toString(),
                formParameters = formParameters
            ).let { httpResponse ->
                SimpleHttpResponse(
                    httpResponse.status,
                    httpResponse.headers,
                    httpResponse.bodyAsText()
                )
            }
        }
    }

    override fun generatePresentationForVPToken(
        session: VPresentationSession,
        tokenRequest: TokenRequest
    ): PresentationResult = runBlocking {
        logger.debug("=== GENERATING PRESENTATION FOR VP TOKEN - Session: {session}", session)

        val selectedCredentials =
            HACK_outsideMappedSelectedCredentialsPerSession[session.authorizationRequest!!.state + session.authorizationRequest.presentationDefinition?.id]!!
        val selectedDisclosures =
            HACK_outsideMappedSelectedDisclosuresPerSession[session.authorizationRequest.state + session.authorizationRequest.presentationDefinition?.id]

        logger.debug("Selected credentials: {selectedCredentials}", selectedCredentials)
        val matchedCredentials = credentialsService.get(wallet, selectedCredentials)
        logger.debug("Matched credentials: {matchedCredentials}", matchedCredentials)

        logger.debug("Using disclosures: {selectedDisclosures}", selectedDisclosures)
        val key = runBlocking {
            runCatching {
                DidService.resolveToKey(did).getOrThrow().let { KeysService.get(it.getKeyId()) }
                    ?.let { KeyManager.resolveSerializedKey(it.document) }
            }
        }.getOrElse {
            throw IllegalArgumentException(
                "Could not resolve key to sign JWS to generate presentation for vp_token",
                it
            )
        } ?: error("No key was resolved when trying to resolve key to sign JWS to generate presentation for vp_token")

        val jwtsPresented = CredentialFilterUtils.getJwtVcList(matchedCredentials, selectedDisclosures)
        logger.debug("jwtsPresented: {jwtsPresented}", jwtsPresented)

        val sdJwtVCsPresented = runBlocking {
            matchedCredentials.filter { it.format == CredentialFormat.sd_jwt_vc }.map {
                // TODO: adopt selective disclosure selection (doesn't work with jwts other than sd_jwt anyway, like above)
                val documentWithDisclosures = if (selectedDisclosures?.containsKey(it.id) == true) {
                    it.document + "~${selectedDisclosures[it.id]!!.joinToString("~")}"
                } else {
                    it.document
                }
                val sdJwtVC = SDJwtVC.parse(documentWithDisclosures)

                val finalSDJwtVC = sdJwtVC.present(
                    discloseAll = true,
                    audience = session.authorizationRequest.clientId,
                    nonce = session.nonce ?: "",
                    kbCryptoProvider = WaltIdJWTCryptoProvider(
                        keys = mapOf(key.getKeyId() to key)
                    ),
                    kbKeyId = key.getKeyId()
                )

                finalSDJwtVC
            }
        }.map {
            it.toString(
                formatForPresentation = true,
                withKBJwt = true
            )
        }
        logger.debug("sdJwtVCsPresented: {sdJwtVCsPresented}", sdJwtVCsPresented)

        val mDocsPresented = runBlocking {
            val matchingMDocs = matchedCredentials.filter { it.format == CredentialFormat.mso_mdoc }
            if (matchingMDocs.isNotEmpty()) {
                // Create OpenID4VPHandoverInfo from authorization request
                val handoverInfo = OpenID4VPHandoverInfo(
                    clientId = session.authorizationRequest.clientId,
                    nonce = session.nonce ?: "",
                    jwkThumbprint = null, // TODO: Extract from request if available
                    responseUri = session.authorizationRequest.responseUri
                )
                
                // Create OpenID4VPHandover
                val handoverInfoBytes = coseCompliantCbor.encodeToByteArray(OpenID4VPHandoverInfo.serializer(), handoverInfo)
                val infoHash = handoverInfoBytes.sha256()
                val handover = OpenID4VPHandover(
                    identifier = "OpenID4VPHandover",
                    infoHash = infoHash
                )
                
                // Create SessionTranscript
                val sessionTranscript = SessionTranscript.forOpenId(handover)
                
                matchingMDocs.map { cred ->
                    // Parse the stored mdoc document
                    val document = MdocParser.parseToDocument(cred.document)
                    
                    // Build device authentication bytes
                    val deviceAuthBytes = MdocCryptoHelper.buildDeviceAuthenticationBytes(
                        transcript = sessionTranscript,
                        docType = document.docType,
                        namespaces = ByteStringWrapper(DeviceNameSpaces(emptyMap()))
                    )
                    
                    // Sign device authentication with device key
                    // According to ISO 18013-5:2021, device signatures must use detached payload (payload = null)
                    // The DeviceAuthenticationBytes are verified separately as a detached payload
                    val deviceAuthCoseSign1 = CoseSign1.createAndSignDetached(
                        protectedHeaders = CoseHeaders(
                            algorithm = Cose.Algorithm.ES256,
                            kid = key.getKeyId().encodeToByteArray()
                        ),
                        unprotectedHeaders = CoseHeaders(),
                        detachedPayload = deviceAuthBytes,
                        signer = key.toCoseSigner()
                    )
                    
                    // Create DeviceSigned structure (empty namespaces for now - can be extended later)
                    val deviceSigned = DeviceSigned.fromDeviceSignedItems(
                        namespacedItems = emptyMap<String, List<id.walt.mdoc.objects.elements.DeviceSignedItem>>(), // TODO: Filter based on presentation definition if needed
                        deviceAuth = deviceAuthCoseSign1
                    )
                    
                    // Create new Document with device signature
                    val presentedDocument = Document(
                        docType = document.docType,
                        issuerSigned = document.issuerSigned,
                        deviceSigned = deviceSigned,
                        errors = null
                    )
                    
                    // Serialize document to CBOR
                    MdocCbor.encodeToByteArray(Document.serializer(), presentedDocument)
                }
            } else listOf()
        }
        logger.debug("mDocsPresented: {mDocsPresented}", mDocsPresented)

        val presentationId = (session.presentationDefinition?.id ?: "urn:uuid:${randomUUIDString().lowercase()}")

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

        val deviceResponse = if (mDocsPresented.isNotEmpty()) {
            // Create DeviceResponse wrapper
            val deviceResponseObj = MdocDeviceResponse(
                version = "1.0",
                documents = mDocsPresented.map { 
                    MdocCbor.decodeFromByteArray(Document.serializer(), it)
                }.toTypedArray(),
                documentErrors = null,
                status = 0u
            )
            // Serialize to CBOR and encode as base64Url
            coseCompliantCbor.encodeToByteArray(MdocDeviceResponse.serializer(), deviceResponseObj).encodeToBase64Url()
        } else null

        logger.debug("GENERATED VP: {signedJwtVP}", signedJwtVP)

        // DONE: filter presentations if null
        // DONE: generate descriptor mappings based on type (vp or mdoc device response)
        // DONE: set root path of descriptor mapping based on whether there are multiple presentations or just one ("$" or "$[idx]")
        val presentations =
            listOf(signedJwtVP, deviceResponse).filterNotNull().plus(sdJwtVCsPresented).map { JsonPrimitive(it) }
        logger.debug("presentations: {presentations}", presentations)

        val rootPathVP = "$" + (if (presentations.size == 2) "[0]" else "")
        val rootPathMDoc = "$" + (if (presentations.size == 2) "[1]" else "")
        PresentationResult(
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

    override fun resolveDID(did: String): String = runBlocking {
        val key = runBlocking { DidService.resolveToKey(did) }.getOrElse {
            throw IllegalArgumentException(
                "Could not resolve DID in CredentialWallet: $did, error cause attached.",
                it
            )
        }
        val keyId = runBlocking { key.getKeyId() }

        keyMapping[keyId] = key

        logger.debug("RESOLVED DID: {did} to keyId: {keyId}", did, keyId)

        did
    }

    override fun isPresentationDefinitionSupported(presentationDefinition: PresentationDefinition): Boolean {
        return true
    }

    override val metadata
        get() = createDefaultProviderMetadata() as OpenIDProviderMetadata.Draft13

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

    override fun putSession(id: String, session: VPresentationSession, ttl: Duration?) {
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
        return super.initializeAuthorization(authorizationRequest, expiresIn, null)
            .copy(selectedCredentialIds = selectedCredentials).also {
                putSession(it.id, it)
            }
    }

    suspend fun getVpJson(credentialsPresented: List<String>, presentationId: String, nonce: String?, aud: String): String {
        logger.debug("Credentials presented: {credentialsPresented}", credentialsPresented)
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
            id = presentationDefinition?.inputDescriptors?.get(index)?.id ?: getDescriptorId(
                type,
                presentationDefinition
            ),//session.presentationDefinition?.inputDescriptors?.get(index)?.id,
            format = VCFormat.jwt_vp,  // jwt_vp_json
            path = rootPath,
            pathNested = DescriptorMapping(
                id = getDescriptorId(
                    type,
                    presentationDefinition,
                ),//session.presentationDefinition?.inputDescriptors?.get(index)?.id,
                format = VCFormat.jwt_vc, // jwt_vc_json
                path = "$rootPath.vp.verifiableCredential[$index]", //.vp.verifiableCredentials
            )
        )
    }

    fun buildDescriptorMappingMDoc(
        presentationDefinition: PresentationDefinition?,
        index: Int,
        mdoc: String,
        rootPath: String = "$",
    ) = let {
        val document = MdocParser.parseToDocument(mdoc)
        val type = document.docType

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
        vcJwsStr: String,
        rootPath: String = "$",
    ) = let {
        val vcJws = vcJwsStr.base64UrlToBase64().decodeJws()
        vcJws.payload["vc"]?.jsonObject?.get("type")?.jsonArray?.last()?.jsonPrimitive?.contentOrNull
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
