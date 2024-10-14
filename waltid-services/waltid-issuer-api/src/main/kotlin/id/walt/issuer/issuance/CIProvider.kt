@file:Suppress("ExtractKtorModule")

package id.walt.issuer.issuance

import COSE.AlgorithmID
import COSE.OneKey
import cbor.Cbor
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.util.X509CertUtils
import com.sksamuel.hoplite.ConfigException
import com.upokecenter.cbor.CBORObject
import id.walt.commons.config.ConfigManager
import id.walt.commons.config.ConfigurationException
import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.credentials.issuance.Issuer.mergingJwtIssue
import id.walt.credentials.issuance.Issuer.mergingSdJwtIssue
import id.walt.credentials.issuance.dataFunctions
import id.walt.credentials.utils.CredentialDataMergeUtils.mergeSDJwtVCPayloadWithMapping
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.*
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto.utils.JsonUtils.toJsonObject
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import id.walt.oid4vc.OpenID4VC
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_AUTHORIZATION_TYPE
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.errors.CredentialError
import id.walt.oid4vc.errors.DeferredCredentialError
import id.walt.oid4vc.errors.TokenError
import id.walt.oid4vc.interfaces.CredentialResult
import id.walt.oid4vc.interfaces.ITokenProvider
import id.walt.oid4vc.providers.*
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.JwtUtils
import id.walt.oid4vc.util.randomUUID
import id.walt.sdjwt.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * OIDC for Verifiable Credential Issuance service provider, implementing abstract service provider from OIDC4VC library.
 */
@OptIn(ExperimentalUuidApi::class)
open class CIProvider(
    val baseUrl: String = let { ConfigManager.getConfig<OIDCIssuerServiceConfig>().baseUrl },
    val config: CredentialIssuerConfig = CredentialIssuerConfig(credentialConfigurationsSupported = ConfigManager.getConfig<CredentialTypeConfig>().parse())
): ITokenProvider {
    private val log = KotlinLogging.logger { }
    val metadata
        get() = OpenID4VCI.createDefaultProviderMetadata(baseUrl).copy(
            credentialConfigurationsSupported = config.credentialConfigurationsSupported
        )

    companion object {

        private val http = HttpClient() {
            install(ContentNegotiation) {
                json()
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }

        val exampleIssuerKey by lazy { runBlocking { JWKKey.generate(KeyType.Ed25519) } }
        val exampleIssuerDid by lazy { runBlocking { DidService.registerByKey("jwk", exampleIssuerKey).did } }

        // TODO: make configurable
//        private val CI_TOKEN_KEY by lazy { KeyManager.resolveSerializedKeyBlocking("""""") }
        private val CI_TOKEN_KEY =
            runBlocking { KeyManager.resolveSerializedKey(ConfigManager.getConfig<OIDCIssuerServiceConfig>().ciTokenKey) }
//        private val CI_TOKEN_KEY by lazy { runBlocking { JWKKey.generate(KeyType.Ed25519) } }
    }

    // -------------------------------
    // Simple in-memory session management
    private val authSessions = ConfiguredPersistence<IssuanceSession>(
        "auth_sessions", defaultExpiration = 5.minutes,
        encoding = { Json.encodeToString(it) },
        decoding = { Json.decodeFromString(it) },
    )


    var deferIssuance = false
    val deferredCredentialRequests = ConfiguredPersistence<CredentialRequest>(
        "deferred_credential_requests", defaultExpiration = 5.minutes,
        encoding = { Json.encodeToString(it) },
        decoding = { Json.decodeFromString(it) },
    )

    fun getSession(id: String): IssuanceSession? {
        log.debug { "RETRIEVING CI AUTH SESSION: $id" }
        return authSessions[id]
    }

    fun getSessionByAuthServerState(authServerState: String): IssuanceSession? {
        log.debug { "RETRIEVING CI AUTH SESSION by authServerState: $authServerState" }
        var properSession: IssuanceSession? = null
        authSessions.getAll().forEach { session ->
            if (session.authServerState == authServerState) {
                properSession = session
            }
        }
        return properSession
    }

    fun getSessionForAccessToken(parsedAccessToken: JsonObject): IssuanceSession? {
        val sessionId = parsedAccessToken.get(JWTClaims.Payload.subject)?.jsonPrimitive?.content ?: throw IllegalArgumentException("Access token has no subject or invalid subject type")
        return getSession(sessionId)
    }


    fun putSession(id: String, session: IssuanceSession) {
        log.debug { "SETTING CI AUTH SESSION: $id = $session" }
        authSessions[id] = session
    }

    fun removeSession(id: String) {
        log.debug { "REMOVING CI AUTH SESSION: $id" }
        authSessions.remove(id)
    }

    fun getVerifiedSession(sessionId: String): IssuanceSession? {
        return getSession(sessionId)?.let {
            if (it.isExpired) {
                removeSession(sessionId)
                null
            } else {
                it
            }
        }
    }

    // ------------------------------------------
    // Simple cryptographics operation interface implementations
    override fun signToken(
        target: TokenTarget,
        payload: JsonObject,
        header: JsonObject?,
        keyId: String?,
        privKey: Key?,
    ) =
        runBlocking {
            log.debug { "Signing JWS:   $payload" }
            log.debug { "JWS Signature: target: $target, keyId: $keyId, header: $header" }
            if (header != null && keyId != null && privKey != null) {
                val headers = header.toMutableMap()
                    .plus(mapOf("alg" to "ES256".toJsonElement(), "type" to "jwt".toJsonElement(), "kid" to keyId.toJsonElement()))
                privKey.signJws(payload.toString().toByteArray(), headers).also {
                    log.debug { "Signed JWS: >> $it" }
                }

            } else {
                CI_TOKEN_KEY.signJws(payload.toString().toByteArray()).also {
                    log.debug { "Signed JWS: >> $it" }
                }
            }
        }

    override fun signCWTToken(
        target: TokenTarget,
        payload: MapElement,
        header: MapElement?,
        keyId: String?,
        privKey: Key?,
    ): String {
        TODO("Not yet implemented")
    }

    override fun verifyTokenSignature(target: TokenTarget, token: String) = runBlocking {
        log.debug { "Verifying JWS: $token" }
        log.debug { "JWS Verification: target: $target" }

        val tokenHeader = Json.parseToJsonElement(token.split(".")[0].base64UrlDecode().decodeToString()).jsonObject
        val key = if (tokenHeader["jwk"] != null) {
            JWKKey.importJWK(tokenHeader["jwk"].toString()).getOrThrow()
        } else if (tokenHeader["kid"] != null) {
            val did = tokenHeader["kid"]!!.jsonPrimitive.content.split("#")[0]
            log.debug { "Resolving DID: $did" }
            DidService.resolveToKey(did).getOrThrow()
        } else {
            CI_TOKEN_KEY
        }
        key.verifyJws(token).also { log.debug { "VERIFICATION IS: $it" } }
    }.isSuccess

    @OptIn(ExperimentalSerializationApi::class)
    override fun verifyCOSESign1Signature(target: TokenTarget, token: String) = runBlocking {
        println("Verifying JWS: $token")
        println("JWS Verification: target: $target")
        val coseSign1 = Cbor.decodeFromByteArray<COSESign1>(token.base64UrlDecode())
        val keyInfo = extractHolderKey(coseSign1)
        val cryptoProvider = SimpleCOSECryptoProvider(listOf(keyInfo))

        cryptoProvider.verify1(coseSign1, "pub-key")
    }

    // -------------------------------------
    // Implementation of abstract issuer service provider interface
    fun generateCredential(credentialRequest: CredentialRequest): CredentialResult {
        log.debug { "GENERATING CREDENTIAL:" }
        log.debug { "Credential request: $credentialRequest" }
        log.debug { "CREDENTIAL REQUEST JSON -------:" }
        log.debug { Json.encodeToString(credentialRequest) }

        if (deferIssuance) return CredentialResult(credentialRequest.format, null, randomUUID()).also {
            deferredCredentialRequests[it.credentialId!!] = credentialRequest
        }

        return when (credentialRequest.format) {
            CredentialFormat.mso_mdoc -> runBlocking { doGenerateMDoc(credentialRequest) }
            else -> doGenerateCredential(credentialRequest)
        }
    }

    fun getDeferredCredential(credentialID: String): CredentialResult {
        return deferredCredentialRequests[credentialID]?.let {
            when (it.format) {
                CredentialFormat.mso_mdoc -> runBlocking { doGenerateMDoc(it) }
                else -> doGenerateCredential(it)
            }
        }
            ?: throw DeferredCredentialError(CredentialErrorCode.invalid_request, message = "Invalid credential ID given")
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalStdlibApi::class)
    private fun doGenerateCredential(
        credentialRequest: CredentialRequest,
    ): CredentialResult {
        if (credentialRequest.format == CredentialFormat.mso_mdoc) throw CredentialError(
            credentialRequest, CredentialErrorCode.unsupported_credential_format
        )

        val proofHeader = credentialRequest.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) } ?: throw CredentialError(
            credentialRequest, CredentialErrorCode.invalid_or_missing_proof, message = "Proof must be JWT proof"
        )

        val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content
        val holderKey = proofHeader[JWTClaims.Header.jwk]?.jsonObject

        if (holderKey.isNullOrEmpty() && holderKid.isNullOrEmpty()) throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof,
            message = "Proof JWT header must contain kid or jwk claim"
        )
        val holderDid = if (!holderKid.isNullOrEmpty() && DidUtils.isDidUrl(holderKid)) holderKid.substringBefore("#") else null
        val nonce = OpenID4VCI.getNonceFromProof(credentialRequest.proof!!) ?: throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof, message = "Proof must contain nonce"
        )

        val data: IssuanceSessionData = if (!tokenCredentialMapping.contains(nonce)) {
            repeat(10) {
                log.debug { "WARNING: RETURNING DEMO/EXAMPLE (= BOGUS) CREDENTIAL: nonce is not mapped to issuance request data (was deferred issuance tried?)" }
            }
            findMatchingSessionData(
                credentialRequest,
                listOf(
                    IssuanceSessionData(
                        id = Uuid.random().toString(),
                        exampleIssuerKey,
                        exampleIssuerDid,
                        IssuanceRequest(
                            issuerKey = Json.parseToJsonElement(KeySerialization.serializeKey(exampleIssuerKey)).jsonObject,
                            credentialConfigurationId = "OpenBadgeCredential_${credentialRequest.format.value}",
                            credentialData = Json.parseToJsonElement(IssuanceExamples.openBadgeCredentialData).jsonObject,
                            mdocData = null,
                            issuerDid = exampleIssuerDid
                        )
                    )
                )
            ) ?: throw IllegalArgumentException("No matching issuance session data for nonce: $nonce")
        } else {
            log.debug { "RETRIEVING VC FROM TOKEN MAPPING: $nonce" }
            findMatchingSessionData(
                credentialRequest,
                tokenCredentialMapping[nonce]
                    ?: throw IllegalArgumentException("No matching issuance session data found for nonce: $nonce!")
            ) ?: throw IllegalArgumentException("No matching issuance session data found for nonce: $nonce!")
        }

        return CredentialResult(format = credentialRequest.format, credential = JsonPrimitive(runBlocking {
            val vc = data.request.credentialData ?: throw MissingFieldException(listOf("credentialData"), "credentialData")

            data.run {
              var issuerKid = issuerDid ?: data.issuerKey.key.getKeyId()
              if(!issuerDid.isNullOrEmpty()) {
                if (issuerDid.startsWith("did:key") && issuerDid.length == 186) // EBSI conformance corner case when issuer uses did:key instead of did:ebsi and no trust framework is defined
                  issuerKid = issuerDid + "#" + issuerDid.removePrefix("did:key:")
                else if (issuerDid.startsWith("did:ebsi"))
                  issuerKid = issuerDid + "#" + issuerKey.key.getKeyId()
              }

                val holderKeyJWK =  JWKKey.importJWK(holderKey.toString()).getOrNull()?.exportJWKObject()?.plus("kid" to JWKKey.importJWK(holderKey.toString()).getOrThrow().getKeyId())?.toJsonObject()

                when (data.request.credentialFormat) {
                  CredentialFormat.sd_jwt_vc -> sdJwtVc(
                      holderKeyJWK,
                      vc,
                      holderDid, issuerKid)
                  else -> w3cSdJwtVc(W3CVC(vc), issuerKid, holderDid, holderKey)
              }
            }.also { log.debug { "Respond VC: $it" } }
        }))
    }

    private fun extractHolderKey(coseSign1: COSESign1): COSECryptoProviderKeyInfo {
        val tokenHeader = coseSign1.decodeProtectedHeader()
        return if (tokenHeader.value.containsKey(MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_COSE_KEY))) {
            val rawKey = (tokenHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_COSE_KEY)] as ByteStringElement).value
            COSECryptoProviderKeyInfo(
                "pub-key", AlgorithmID.ECDSA_256,
                OneKey(CBORObject.DecodeFromBytes(rawKey)).AsPublicKey()
            )
        } else {
            val x5c = tokenHeader.value[MapKey(ProofOfPossession.CWTProofBuilder.HEADER_LABEL_X5CHAIN)]
            val x5Chain = when (x5c) {
                is ListElement -> x5c.value.map { X509CertUtils.parse((it as ByteStringElement).value) }
                else -> listOf(X509CertUtils.parse((x5c as ByteStringElement).value))
            }
            COSECryptoProviderKeyInfo(
                "pub-key", AlgorithmID.ECDSA_256,
                x5Chain.first().publicKey, x5Chain = x5Chain
            )
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun doGenerateMDoc(
        credentialRequest: CredentialRequest,
    ): CredentialResult {
        val coseSign1 = Cbor.decodeFromByteArray<COSESign1>(
            credentialRequest.proof?.cwt?.base64UrlDecode() ?: throw CredentialError(
                credentialRequest,
                CredentialErrorCode.invalid_or_missing_proof, message = "No CWT proof found on credential request"
            )
        )
        val holderKey = extractHolderKey(coseSign1)
        val nonce = OpenID4VCI.getNonceFromProof(credentialRequest.proof!!) ?: throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof,
            message = "No nonce found on proof"
        )
        println("RETRIEVING VC FROM TOKEN MAPPING: $nonce")
        val data: IssuanceSessionData = tokenCredentialMapping[nonce]?.first()
            ?: throw CredentialError(
                credentialRequest,
                CredentialErrorCode.invalid_request,
                "The issuanceIdCredentialMapping does not contain a mapping for: $nonce!"
            )
        val issuerSignedItems = data.request.mdocData ?: throw MissingFieldException(listOf("mdocData"), "mdocData")
        val issuerKey = JWK.parse(runBlocking { data.issuerKey.key.exportJWK() }).toECKey()
        val keyId = runBlocking { data.issuerKey.key.getKeyId() }
        val cryptoProvider = SimpleCOSECryptoProvider(listOf(
            COSECryptoProviderKeyInfo(
                keyId, AlgorithmID.ECDSA_256, issuerKey.toECPublicKey(), issuerKey.toECPrivateKey(),
                x5Chain = data.request.x5Chain?.map { X509CertUtils.parse(it) } ?: listOf(),
                trustedRootCAs = data.request.trustedRootCAs?.map { X509CertUtils.parse(it) } ?: listOf()
            )
        ))
        val mdoc = MDocBuilder(
            credentialRequest.docType
                ?: throw CredentialError(
                    credentialRequest,
                    CredentialErrorCode.invalid_request,
                    message = "Missing doc type in credential request"
                )
        ).apply {
            issuerSignedItems.forEach { namespace ->
                namespace.value.forEach { property ->
                    addItemToSign(namespace.key, property.key, property.value.toDataElement())
                }
            }
        }.sign( // TODO: expiration date!
            ValidityInfo(Clock.System.now(), Clock.System.now(), Clock.System.now().plus(365 * 24, DateTimeUnit.HOUR)),
            DeviceKeyInfo(
                DataElement.fromCBOR(
                    OneKey(holderKey.publicKey, null).AsCBOR().EncodeToBytes()
                )
            ), cryptoProvider, keyId
        ).also {
            data.sendCallback("generated_mdoc", buildJsonObject { put("mdoc", it.toCBORHex()) })
        }
        return CredentialResult(
            CredentialFormat.mso_mdoc, JsonPrimitive(mdoc.issuerSigned.toMapElement().toCBOR().encodeToBase64Url()),
            customParameters = mapOf("credential_encoding" to JsonPrimitive("issuer-signed"))
        )
    }


    @OptIn(ExperimentalEncodingApi::class)
    fun parseFromJwt(jwt: String): Pair<String, String> {
        val jwtParts = jwt.split(".")

        fun decodeJwtPart(idx: Int) =
            Json.parseToJsonElement(jwtParts[idx].base64UrlDecode().decodeToString()).jsonObject

        val header = decodeJwtPart(0)
        val payload = decodeJwtPart(1)

        val subjectDid =
            header["kid"]?.jsonPrimitive?.contentOrNull
                ?: throw IllegalArgumentException("No kid in proof.jwt header!")
        val nonce = payload["nonce"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("No nonce in proof.jwt payload!")

        return Pair(subjectDid, nonce)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun generateBatchCredentialResponse(
        batchCredentialRequest: BatchCredentialRequest,
        session: IssuanceSession,
    ): BatchCredentialResponse {
        val credentialRequestFormats = batchCredentialRequest.credentialRequests
            .map { it.format }

        require(credentialRequestFormats.distinct().size < 2) { "Credential requests don't have the same format: ${credentialRequestFormats.joinToString { it.value }}" }

        val keyIdsDistinct = batchCredentialRequest.credentialRequests.map { credReq ->
            credReq.proof?.jwt?.let { jwt -> JwtUtils.parseJWTHeader(jwt) }
                ?.get(JWTClaims.Header.keyID)
                ?.jsonPrimitive?.content
                ?: throw CredentialError(
                    credReq,
                    CredentialErrorCode.invalid_or_missing_proof,
                    message = "Proof must be JWT proof"
                )
        }.distinct()

        require(keyIdsDistinct.size < 2) { "More than one key id requested" }

        return BatchCredentialResponse.success(
            batchCredentialRequest.credentialRequests.map { generateCredentialResponse(it, session) }
        )
    }


    @Serializable
    data class IssuanceSessionData(
        val id: String,
        val issuerKey: DirectSerializedKey,
        val issuerDid: String?,
        val request: IssuanceRequest,
        val callbackUrl: String? = null,
    ) {
        constructor(
            id: String,
            issuerKey: Key,
            issuerDid: String?,
            request: IssuanceRequest,
            callbackUrl: String? = null,
        ) : this(id, DirectSerializedKey(issuerKey), issuerDid, request, callbackUrl)

        suspend fun sendCallback(type: String, data: JsonObject) {
            if (callbackUrl != null) {
                try {
                    http.post(callbackUrl.replace("\$id", id)) {
                        setBody(buildJsonObject {
                            put("id", id)
                            put("type", type)
                            put("data", data)
                        })
                    }
                } catch (ex: Exception) {
                    throw IllegalArgumentException("Error sending HTTP POST request to issuer callback url.", ex)
                }
            }
        }

        val jwtCryptoProvider
            get() = SingleKeyJWTCryptoProvider(issuerKey.key)
    }

    // TODO: Hack as this is non stateless because of oidc4vc lib API
    val sessionCredentialPreMapping = ConfiguredPersistence<List<IssuanceSessionData>>(
        // session id -> VC
        "sessionid_vc", defaultExpiration = 5.minutes,
        encoding = { Json.encodeToString(it) },
        decoding = { Json.decodeFromString(it) },
    )

    // TODO: Hack as this is non stateless because of oidc4vc lib API
    private val tokenCredentialMapping = ConfiguredPersistence<List<IssuanceSessionData>>(
        // token -> VC
        "token_vc", defaultExpiration = 5.minutes,
        encoding = { Json.encodeToString(it) },
        decoding = { Json.decodeFromString(it) },
    )

    //private val sessionTokenMapping = HashMap<String, String>() // session id -> token

    // TODO: Hack as this is non stateless because of oidc4vc lib API
    suspend fun setIssuanceDataForIssuanceId(issuanceId: String, data: List<IssuanceSessionData>) {
        log.debug { "DEPOSITED CREDENTIAL FOR ISSUANCE ID: $issuanceId" }
        sessionCredentialPreMapping[issuanceId] = data
    }

    // TODO: Hack as this is non stateless because of oidc4vc lib API
    suspend fun mapSessionIdToToken(sessionId: String, token: String) {
        log.debug { "MAPPING SESSION ID TO TOKEN: $sessionId -->> $token" }
        val premappedVc = sessionCredentialPreMapping[sessionId]
            ?: throw IllegalArgumentException("No credential pre-mapped with any such session id: $sessionId (for use with token: $token)")
        sessionCredentialPreMapping.remove(sessionId)
        log.debug { "SWAPPING PRE-MAPPED VC FROM SESSION ID TO NEW TOKEN: $token" }
        tokenCredentialMapping[token] = premappedVc

        premappedVc.first().let {
            it.sendCallback("requested_token", buildJsonObject {
                put("request", Json.encodeToJsonElement(it.request).jsonObject)
            })
        }
    }

    private suspend fun IssuanceSessionData.sdJwtVc(
        holderKey: JsonObject?,
        vc: JsonObject,
        holderDid: String?, issuerKid: String?
    ): String = SDJwtVC.sign(
        sdPayload = SDPayload.createSDPayload(
            vc.mergeSDJwtVCPayloadWithMapping(
                mapping = request.mapping ?: JsonObject(emptyMap()),
                context = mapOf(
                    "issuerDid" to issuerDid,
                    "subjectDid" to holderDid
                ).filterValues { !it.isNullOrEmpty() }.mapValues { JsonPrimitive(it.value) },
                dataFunctions
            ),
            request.selectiveDisclosure ?: SDMap(mapOf())
        ),
        jwtCryptoProvider = jwtCryptoProvider,
        issuerDid = (issuerDid ?: "").ifEmpty { issuerKey.key.getKeyId() },
        holderDid = holderDid,
        holderKeyJWK = holderKey,
        issuerKeyId = issuerKey.key.getKeyId(),
        vct = metadata.credentialConfigurationsSupported?.get(request.credentialConfigurationId)?.vct ?: throw ConfigurationException(
            ConfigException("No vct configured for given credential configuration id: ${request.credentialConfigurationId}")
        ),
        additionalJwtHeader = request.x5Chain?.let {
            mapOf("x5c" to JsonArray(it.map { cert -> cert.toJsonElement() }))
        } ?: mapOf()
    ).toString().also {
        sendCallback("sdjwt_issue", buildJsonObject {
            put("sdjwt", it)
        })
    }

    private suspend fun IssuanceSessionData.w3cSdJwtVc(
        vc: W3CVC,
        issuerKid: String,
        holderDid: String?,
        holderKey: JsonObject?
    ) = when(request.selectiveDisclosure.isNullOrEmpty()) {
        true -> vc.mergingJwtIssue(
            issuerKey = issuerKey.key,
            issuerDid = issuerDid,
            issuerKid = issuerKid,
            subjectDid = holderDid ?: "",
            mappings = request.mapping ?: JsonObject(emptyMap()),
            additionalJwtHeader = emptyMap(),
            additionalJwtOptions = emptyMap()
        )
        else -> vc.mergingSdJwtIssue(
            issuerKey = issuerKey.key,
            issuerDid = issuerDid,
            subjectDid = holderDid ?: "",
            mappings = request.mapping ?: JsonObject(emptyMap()),
            additionalJwtHeaders = emptyMap(),
            additionalJwtOptions = emptyMap(),
            disclosureMap = request.selectiveDisclosure
        )
    }.also {
        sendCallback("jwt_issue", buildJsonObject {
            put("jwt", it)
        })
    }

    suspend fun getJwksSessions() : JsonObject{
        var jwksList = buildJsonObject {}
        sessionCredentialPreMapping.getAll().forEach {
            it.forEach {
                jwksList = buildJsonObject {
                    put("keys", buildJsonArray {
                        val jwkWithKid = buildJsonObject {
                            it.issuerKey.key.getPublicKey().exportJWKObject().forEach {
                                put(it.key, it.value)
                            }
                            put("kid", it.issuerKey.key.getPublicKey().getKeyId())
                        }
                        add(jwkWithKid)
                        jwksList.forEach { it.value.jsonArray.forEach {
                                add(it)
                            }
                        }
                    })
                }
            }
        }
        return jwksList
    }

    fun getVctByCredentialConfigurationId(credentialConfigurationId: String) = metadata.credentialConfigurationsSupported?.get(credentialConfigurationId)?.vct

    fun getVctBySupportedCredentialConfiguration(
        baseUrl: String,
        credType: String
    ): CredentialSupported {
        val expectedVct = "$baseUrl/$credType"

        metadata.credentialConfigurationsSupported?.entries?.forEach { entry ->
            if (getVctByCredentialConfigurationId(entry.key) == expectedVct) {
                return entry.value
            }
        }

       throw IllegalArgumentException("Invalid type value: $credType. The $credType type is not supported")
    }

    fun getFormatByCredentialConfigurationId(id: String) = metadata.credentialConfigurationsSupported?.get(id)?.format

    fun getTypesByCredentialConfigurationId(id: String) = metadata.credentialConfigurationsSupported?.get(id)?.credentialDefinition?.type

    fun getDocTypeByCredentialConfigurationId(id: String) = metadata.credentialConfigurationsSupported?.get(id)?.docType

    // Use format, type, vct and docType checks to filter matching entries
    private fun findMatchingSessionData(
        credentialRequest: CredentialRequest,
        sessionDataList: List<IssuanceSessionData>
    ): IssuanceSessionData? {
        return sessionDataList.find { sessionData ->
            val credentialConfigurationId = sessionData.request.credentialConfigurationId
            val credentialFormat = getFormatByCredentialConfigurationId(credentialConfigurationId)
            require(credentialFormat == credentialRequest.format) { "Format does not match" }
            // Depending on the format, perform specific checks
            val additionalMatches =
                when (credentialRequest.format) {
                    CredentialFormat.jwt_vc_json, CredentialFormat.jwt_vc -> {
                        val types = getTypesByCredentialConfigurationId(credentialConfigurationId)
                        types?.containsAll(credentialRequest.credentialDefinition?.type ?: emptyList()) ?: false
                    }
                    CredentialFormat.sd_jwt_vc -> {
                        val vct = getVctByCredentialConfigurationId(credentialConfigurationId)
                        vct == credentialRequest.vct
                    }
                    else -> {
                        val docType = getDocTypeByCredentialConfigurationId(credentialConfigurationId)
                        docType == credentialRequest.docType
                    }
                }

            additionalMatches
        }
    }

    private fun generateProofOfPossessionNonceFor(session: IssuanceSession): IssuanceSession {
        return session.copy(
            cNonce = randomUUID()
        ).also {
            putSession(it.id, it)
        }
    }

    private fun isSupportedAuthorizationDetails(authorizationDetails: AuthorizationDetails): Boolean {
        return authorizationDetails.type == OPENID_CREDENTIAL_AUTHORIZATION_TYPE &&
            config.credentialConfigurationsSupported.values.any { credentialSupported ->
                credentialSupported.format == authorizationDetails.format &&
                    ((authorizationDetails.credentialDefinition?.type != null && credentialSupported.credentialDefinition?.type?.containsAll(
                        authorizationDetails.credentialDefinition!!.type!!
                    ) == true) ||
                        (authorizationDetails.docType != null && credentialSupported.docType == authorizationDetails.docType)
                        )
                // TODO: check other supported credential parameters
            }
    }

    fun validateAuthorizationRequest(authorizationRequest: AuthorizationRequest): Boolean {
        return authorizationRequest.authorizationDetails != null && authorizationRequest.authorizationDetails!!.any {
            isSupportedAuthorizationDetails(it)
        }
    }

    fun initializeAuthorization(
        authorizationRequest: AuthorizationRequest,
        expiresIn: Duration,
        authServerState: String?, //the state used for additional authentication with pwd, id_token or vp_token.
    ): IssuanceSession {
        return if (authorizationRequest.issuerState.isNullOrEmpty()) {
            if (!validateAuthorizationRequest(authorizationRequest)) {
                throw AuthorizationError(
                    authorizationRequest, AuthorizationErrorCode.invalid_request,
                    "No valid authorization details for credential issuance found on authorization request"
                )
            }
            IssuanceSession(
                randomUUID(), authorizationRequest,
                Clock.System.now().plus(expiresIn), authServerState = authServerState
            )
        } else {
            getVerifiedSession(authorizationRequest.issuerState!!)?.copy(authorizationRequest = authorizationRequest)
                ?: throw AuthorizationError(
                    authorizationRequest, AuthorizationErrorCode.invalid_request,
                    "No valid issuance session found for given issuer state"
                )
        }.also {
            val updatedSession = IssuanceSession(
                id = it.id,
                authorizationRequest = authorizationRequest,
                expirationTimestamp = Clock.System.now().plus(5.minutes),
                authServerState = authServerState,
                txCode = it.txCode,
                txCodeValue = it.txCodeValue,
                credentialOffer = it.credentialOffer,
                cNonce = it.cNonce,
                customParameters = it.customParameters
            )
            putSession(it.id, updatedSession)
        }
    }

    open fun initializeCredentialOffer(
        credentialOfferBuilder: CredentialOffer.Builder,
        expiresIn: Duration,
        allowPreAuthorized: Boolean,
        txCode: TxCode? = null, txCodeValue: String? = null,
    ): IssuanceSession {
        val sessionId = randomUUID()
        credentialOfferBuilder.addAuthorizationCodeGrant(sessionId)
        if (allowPreAuthorized)
            credentialOfferBuilder.addPreAuthorizedCodeGrant(
                OpenID4VC.generateAuthorizationCodeFor(this, sessionId, metadata.issuer!!),
                txCode
            )
        return IssuanceSession(
            id = sessionId,
            authorizationRequest = null,
            expirationTimestamp = Clock.System.now().plus(expiresIn),
            txCode = txCode,
            txCodeValue = txCodeValue,
            credentialOffer = credentialOfferBuilder.build()
        ).also {
            putSession(it.id, it)
        }
    }

    private fun createCredentialResponseFor(credentialResult: CredentialResult, session: IssuanceSession): CredentialResponse {
        return credentialResult.credential?.let { credential ->
            CredentialResponse.success(credentialResult.format, credential, customParameters = credentialResult.customParameters)
        } ?: generateProofOfPossessionNonceFor(session).let { updatedSession ->
            CredentialResponse.deferred(
                credentialResult.format,
                OpenID4VCI.generateDeferredCredentialToken(this,
                    session.id, metadata.issuer ?: throw Exception("No issuer defined in provider metadata"),
                    credentialResult.credentialId ?: throw Exception("credentialId must not be null, if credential issuance is deferred.")
                ),
                updatedSession.cNonce,
                updatedSession.expirationTimestamp - Clock.System.now()
            )
        }
    }

    fun generateCredentialResponse(credentialRequest: CredentialRequest, session: IssuanceSession): CredentialResponse {
        // access_token should be validated on API level and issuance session extracted
        // Validate credential request (proof of possession, etc)
        val validationResult = OpenID4VCI.validateCredentialRequest(credentialRequest, session, metadata,this)
        if(!validationResult.success)
            throw CredentialError(credentialRequest, CredentialErrorCode.invalid_request, message = validationResult.message)
        // create credential result
        val credentialResult = generateCredential(credentialRequest)
        return createCredentialResponseFor(credentialResult, session)
    }

    fun generateDeferredCredentialResponse(acceptanceToken: String): CredentialResponse {
        val accessInfo =
            OpenID4VC.verifyAndParseToken(this, acceptanceToken, metadata.issuer!!, TokenTarget.DEFERRED_CREDENTIAL) ?: throw DeferredCredentialError(
                CredentialErrorCode.invalid_token,
                message = "Invalid acceptance token"
            )
        val sessionId = accessInfo[JWTClaims.Payload.subject]!!.jsonPrimitive.content
        val credentialId = accessInfo[JWTClaims.Payload.jwtID]!!.jsonPrimitive.content
        val session = getVerifiedSession(sessionId) ?: throw DeferredCredentialError(
            CredentialErrorCode.invalid_token,
            "Session not found for given access token, or session expired."
        )
        // issue credential for credential request
        return createCredentialResponseFor(getDeferredCredential(credentialId), session)
    }

    fun processTokenRequest(tokenRequest: TokenRequest): TokenResponse {
        val payload = OpenID4VC.validateAndParseTokenRequest(this, tokenRequest, metadata.issuer!!) ?: throw TokenError(tokenRequest, TokenErrorCode.invalid_request, "Token request could not be validated")
        val sessionId = payload[JWTClaims.Payload.subject]?.jsonPrimitive?.content ?: throw TokenError(tokenRequest, TokenErrorCode.invalid_request, "Token contains no session ID in subject")
        val session = getVerifiedSession(sessionId) ?: throw TokenError(
            tokenRequest = tokenRequest,
            errorCode = TokenErrorCode.invalid_request,
            message = "No authorization session found for given authorization code, or session expired."
        )
        if (tokenRequest.grantType == GrantType.pre_authorized_code && session.txCode != null &&
            session.txCodeValue != tokenRequest.txCode
        ) {
            throw TokenError(
                tokenRequest,
                TokenErrorCode.invalid_grant,
                message = "User PIN required for this issuance session has not been provided or PIN is wrong."
            )
        }
        // Expiration time required by EBSI
        val currentTime = Clock.System.now().epochSeconds
        val expirationTime = (currentTime + 864000L) // ten days in milliseconds
        return TokenResponse.success(
            OpenID4VC.generateToken(this, sessionId, metadata.issuer!!, TokenTarget.ACCESS),
            tokenType = "bearer",
            expiresIn = expirationTime,
            cNonce = generateProofOfPossessionNonceFor(session).cNonce,
            cNonceExpiresIn = session.expirationTimestamp - Clock.System.now(),
            state = session.authorizationRequest?.state
        )
    }
}
