@file:Suppress("ExtractKtorModule")

package id.walt.issuer.issuance

import COSE.AlgorithmID
import COSE.OneKey
import cbor.Cbor
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.util.X509CertUtils
import com.upokecenter.cbor.CBORObject
import id.walt.commons.config.ConfigManager
import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.credentials.issuance.Issuer.mergingJwtIssue
import id.walt.credentials.issuance.Issuer.mergingSdJwtIssue
import id.walt.credentials.vc.vcs.W3CVC
import id.walt.crypto.keys.*
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.did.dids.DidService
import id.walt.did.dids.DidUtils
import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.*
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.doc.MDocTypes
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.errors.CredentialError
import id.walt.oid4vc.errors.DeferredCredentialError
import id.walt.oid4vc.interfaces.CredentialResult
import id.walt.oid4vc.providers.CredentialIssuerConfig
import id.walt.oid4vc.providers.IssuanceSession
import id.walt.oid4vc.providers.OpenIDCredentialIssuer
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.BatchCredentialRequest
import id.walt.oid4vc.requests.CredentialRequest
import id.walt.oid4vc.responses.BatchCredentialResponse
import id.walt.oid4vc.responses.CredentialErrorCode
import id.walt.oid4vc.responses.CredentialResponse
import id.walt.oid4vc.util.randomUUID
import id.walt.sdjwt.SDJwtVC
import id.walt.sdjwt.SDMap
import id.walt.sdjwt.SDPayload
import id.walt.sdjwt.WaltIdJWTCryptoProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration.Companion.minutes

val supportedCredentialTypes = ConfigManager.getConfig<CredentialTypeConfig>().supportedCredentialTypes

/**
 * OIDC for Verifiable Credential Issuance service provider, implementing abstract service provider from OIDC4VC library.
 */
open class CIProvider : OpenIDCredentialIssuer(
    baseUrl = let {
        ConfigManager.getConfig<OIDCIssuerServiceConfig>().baseUrl
    }, config = CredentialIssuerConfig(credentialConfigurationsSupported = supportedCredentialTypes.flatMap { entry ->
        CredentialFormat.entries.map { format -> Pair(
          "${entry.key}_${format.value}",
            CredentialSupported(
                format = format,
                cryptographicBindingMethodsSupported = setOf("did"),
                credentialSigningAlgValuesSupported = setOf("EdDSA", "ES256", "ES256K", "RSA"),
                types = entry.value
            ))
        }
    }.plus(Pair(
        MDocTypes.ISO_MDL, CredentialSupported(
        format = CredentialFormat.mso_mdoc,
        cryptographicBindingMethodsSupported = setOf("cose_key"),
        credentialSigningAlgValuesSupported = setOf("ES256"),
        proofTypesSupported = mapOf(ProofType.cwt to ProofTypeMetadata(setOf("ES256"))),
        types = listOf(MDocTypes.ISO_MDL),
        docType = MDocTypes.ISO_MDL
    ))).plus(
        Pair("urn:eu.europa.ec.eudi:pid:1", CredentialSupported(
        format = CredentialFormat.sd_jwt_vc,
            cryptographicBindingMethodsSupported = setOf("jwk"),
            credentialSigningAlgValuesSupported = setOf("ES256"),
            types = listOf("urn:eu.europa.ec.eudi:pid:1"),
            docType = "urn:eu.europa.ec.eudi:pid:1"
    )
        )
    ).plus(
        Pair("identity_credential_vc+sd-jwt", CredentialSupported(
            format = CredentialFormat.sd_jwt_vc,
            cryptographicBindingMethodsSupported = setOf("jwk"),
            credentialSigningAlgValuesSupported = setOf("ES256"),
            types = listOf("identity_credential_vc+sd-jwt"),
            docType = "identity_credential_vc+sd-jwt"
        )
        )
    ).toMap())
) {
    private val log = KotlinLogging.logger { }

    companion object {

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

    override fun getSession(id: String): IssuanceSession? {
        log.debug { "RETRIEVING CI AUTH SESSION: $id" }
        return authSessions[id]
    }

    override fun getSessionByAuthServerState(authServerState: String): IssuanceSession? {
        log.debug { "RETRIEVING CI AUTH SESSION by authServerState: $authServerState" }
        var properSession: IssuanceSession? = null
        authSessions.getAll().forEach { session ->
            if (session.authServerState == authServerState) {
                properSession = session
            }
        }
        return properSession
    }


    override fun putSession(id: String, session: IssuanceSession) {
        log.debug { "SETTING CI AUTH SESSION: $id = $session" }
        authSessions[id] = session
    }

    override fun removeSession(id: String) {
        log.debug { "REMOVING CI AUTH SESSION: $id" }
        authSessions.remove(id)
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
        privKey: Key?
    ): String {
        TODO("Not yet implemented")
    }

    @OptIn(ExperimentalEncodingApi::class)
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
    override fun generateCredential(credentialRequest: CredentialRequest): CredentialResult {
        log.debug { "GENERATING CREDENTIAL:" }
        log.debug { "Credential request: $credentialRequest" }
        log.debug { "CREDENTIAL REQUEST JSON -------:" }
        log.debug { Json.encodeToString(credentialRequest) }

        if (deferIssuance) return CredentialResult(credentialRequest.format, null, randomUUID()).also {
            deferredCredentialRequests[it.credentialId!!] = credentialRequest
        }
        return when (credentialRequest.format) {
            CredentialFormat.mso_mdoc -> doGenerateMDoc(credentialRequest)
            else -> doGenerateCredential(credentialRequest)
        }
    }

    override fun getDeferredCredential(credentialID: String): CredentialResult {
        return deferredCredentialRequests[credentialID]?.let {
            when (it.format) {
                CredentialFormat.mso_mdoc -> doGenerateMDoc(it)
                else -> doGenerateCredential(it)
            }
        }
            ?: throw DeferredCredentialError(CredentialErrorCode.invalid_request, message = "Invalid credential ID given")
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun doGenerateCredential(
        credentialRequest: CredentialRequest,
    ): CredentialResult {
        if (credentialRequest.format == CredentialFormat.mso_mdoc) throw CredentialError(
            credentialRequest, CredentialErrorCode.unsupported_credential_format
        )

        val proofPayload = credentialRequest.proof?.jwt?.let { parseTokenPayload(it) } ?: throw CredentialError(
            credentialRequest, CredentialErrorCode.invalid_or_missing_proof, message = "Proof must be JWT proof"
        )
        val proofHeader = credentialRequest.proof?.jwt?.let { parseTokenHeader(it) } ?: throw CredentialError(
            credentialRequest, CredentialErrorCode.invalid_or_missing_proof, message = "Proof must be JWT proof"
        )

        val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content
        val holderKey = proofHeader[JWTClaims.Header.jwk]?.jsonObject

        if (holderKey.isNullOrEmpty() && holderKid.isNullOrEmpty()) throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof,
            message = "Proof JWT header must contain kid or jwk claim"
        )
        val holderDid = if (!holderKid.isNullOrEmpty() && DidUtils.isDidUrl(holderKid)) holderKid.substringBefore("#") else holderKid
        val nonce = proofPayload["nonce"]?.jsonPrimitive?.content ?: throw CredentialError(
            credentialRequest,
            CredentialErrorCode.invalid_or_missing_proof, message = "Proof must contain nonce"
        )

        val data: IssuanceSessionData = (if (!tokenCredentialMapping.contains(nonce)) {
            repeat(10) {
                log.debug { "WARNING: RETURNING DEMO/EXAMPLE (= BOGUS) CREDENTIAL: nonce is not mapped to issuance request data (was deferred issuance tried?)" }
            }
            listOf(
                IssuanceSessionData(
                    exampleIssuerKey,
                    exampleIssuerDid,
                    IssuanceRequest(
                        issuerKey = Json.parseToJsonElement(KeySerialization.serializeKey(exampleIssuerKey)).jsonObject,
                        issuerDid = exampleIssuerDid,
                        credentialConfigurationId = "OpenBadgeCredential_${credentialRequest.format.value}",
                        credentialData = W3CVC.fromJson(IssuanceExamples.openBadgeCredentialData),
                        mdocData = null
                    )
                )
            )
        } else {
            log.debug { "RETRIEVING VC FROM TOKEN MAPPING: $nonce" }
            tokenCredentialMapping[nonce]
                ?: throw IllegalArgumentException("The issuanceIdCredentialMapping does not contain a mapping for: $nonce!")
        }).first()

        return CredentialResult(format = credentialRequest.format, credential = JsonPrimitive(runBlocking {
            val vc = data.request.credentialData ?: throw MissingFieldException(listOf("credentialData"), "credentialData")

            data.run {
                var issuerKid = issuerDid
                if (issuerDid.startsWith("did:key") && issuerDid.length == 186) // EBSI conformance corner case when issuer uses did:key instead of did:ebsi and no trust framework is defined
                    issuerKid = issuerDid + "#" + issuerDid.removePrefix("did:key:")

                if (issuerDid.startsWith("did:ebsi"))
                    issuerKid = issuerDid + "#" + issuerKey.key.getKeyId()

                when (credentialRequest.format) {
                    CredentialFormat.sd_jwt_vc -> sdJwtVc(JWKKey.importJWK(holderKey.toString()).getOrNull(), vc, data, holderDid)
                    else -> nonSdJwtVc(vc, issuerKid, holderDid, holderKey)
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
    private fun doGenerateMDoc(
        credentialRequest: CredentialRequest,
    ): CredentialResult {
        val coseSign1 = Cbor.decodeFromByteArray<COSESign1>(
            credentialRequest.proof?.cwt?.base64UrlDecode() ?: throw CredentialError(
                credentialRequest,
                CredentialErrorCode.invalid_or_missing_proof, message = "No CWT proof found on credential request"
            )
        )
        val holderKey = extractHolderKey(coseSign1)
        val nonce = getNonceFromProof(credentialRequest.proof!!) ?: throw CredentialError(
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
        )
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
    override fun generateBatchCredentialResponse(
        batchCredentialRequest: BatchCredentialRequest,
        accessToken: String,
    ): BatchCredentialResponse {
        val credentialRequestFormats = batchCredentialRequest.credentialRequests
            .map { it.format }

        require(credentialRequestFormats.distinct().size < 2) { "Credential requests don't have the same format: ${credentialRequestFormats.joinToString { it.value }}" }

        val keyIdsDistinct = batchCredentialRequest.credentialRequests.map { credReq ->
            credReq.proof?.jwt?.let { jwt -> parseTokenHeader(jwt) }
                ?.get(JWTClaims.Header.keyID)
                ?.jsonPrimitive?.content
                ?: throw CredentialError(
                    credReq,
                    CredentialErrorCode.invalid_or_missing_proof,
                    message = "Proof must be JWT proof"
                )
        }.distinct()

        require(keyIdsDistinct.size < 2) { "More than one key id requested" }

//        val keyId = keyIdsDistinct.first()


        batchCredentialRequest.credentialRequests.first().let { credentialRequest ->

            val (subjectDid, nonce) = parseFromJwt(
                credentialRequest.proof?.jwt ?: throw IllegalArgumentException("No proof.jwt in credential request!")
            )

            log.debug { "RETRIEVING VC FROM TOKEN MAPPING: $nonce" }
            val issuanceSessionData = tokenCredentialMapping[nonce]
                ?: throw IllegalArgumentException("The issuanceIdCredentialMapping does not contain a mapping for: $nonce!")

            val credentialResults = issuanceSessionData.map { data ->
                CredentialResponse.success(
                    format = credentialRequest.format,
                    credential = JsonPrimitive(
                        runBlocking {
                            val vc = data.request.credentialData ?: throw MissingFieldException(listOf("credentialData"), "credentialData")

                            data.run {
                                when (credentialRequest.format) {
                                    CredentialFormat.sd_jwt_vc -> vc.mergingSdJwtIssue(
                                        issuerKey = issuerKey.key,
                                        issuerDid = issuerDid,
                                        subjectDid = subjectDid,
                                        mappings = request.mapping ?: JsonObject(emptyMap()),
                                        additionalJwtHeaders = emptyMap(),
                                        additionalJwtOptions = emptyMap(),
                                        disclosureMap = data.request.selectiveDisclosure
                                            ?: SDMap.Companion.generateSDMap(
                                                JsonObject(emptyMap()),
                                                JsonObject(emptyMap())
                                            )
                                    )

                                    else -> vc.mergingJwtIssue(
                                        issuerKey = issuerKey.key,
                                        issuerDid = issuerDid,
                                        subjectDid = subjectDid,
                                        mappings = request.mapping ?: JsonObject(emptyMap()),
                                        additionalJwtHeader = emptyMap(),
                                        additionalJwtOptions = emptyMap(),
                                    )
                                }

                            }.also { log.debug { "Respond VC: $it" } }
                        }
                    )
                )
            }

            return BatchCredentialResponse.success(credentialResults, accessToken, 5.minutes)
        }
    }


    @Serializable
    data class IssuanceSessionData(
        val issuerKey: DirectSerializedKey, val issuerDid: String, val request: IssuanceRequest,
    ) {
        constructor(issuerKey: Key, issuerDid: String, request: IssuanceRequest) : this(DirectSerializedKey(issuerKey), issuerDid, request)
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
    fun setIssuanceDataForIssuanceId(issuanceId: String, data: List<IssuanceSessionData>) {
        log.debug { "DEPOSITED CREDENTIAL FOR ISSUANCE ID: $issuanceId" }
        sessionCredentialPreMapping[issuanceId] = data
    }

    // TODO: Hack as this is non stateless because of oidc4vc lib API
    fun mapSessionIdToToken(sessionId: String, token: String) {
        log.debug { "MAPPING SESSION ID TO TOKEN: $sessionId -->> $token" }
        val premappedVc = sessionCredentialPreMapping[sessionId]
            ?: throw IllegalArgumentException("No credential pre-mapped with any such session id: $sessionId (for use with token: $token)")
        sessionCredentialPreMapping.remove(sessionId)
        log.debug { "SWAPPING PRE-MAPPED VC FROM SESSION ID TO NEW TOKEN: $token" }
        tokenCredentialMapping[token] = premappedVc
    }

    private suspend fun IssuanceSessionData.sdJwtVc(
        holderKey: JWKKey?,
        vc: W3CVC,
        data: IssuanceSessionData,
        holderDid: String?
    ) = vc.mergingSdJwtIssue(
        issuerKey.key, issuerDid.ifEmpty { issuerKey.key.getKeyId() },
        holderDid ?: holderKey?.getKeyId() ?: throw IllegalArgumentException("Either holderKey or holderDid must be given"),
        request.mapping ?: JsonObject(emptyMap()),
        type = SDJwtVC.SD_JWT_VC_TYPE_HEADER,
        additionalJwtHeaders = request.x5Chain?.let {
            mapOf("x5c" to JsonArray(it.map { cert -> cert.toJsonElement() }))
        } ?: mapOf(),
        additionalJwtOptions = SDJwtVC.defaultPayloadProperties(
            issuerDid.ifEmpty { issuerKey.key.getKeyId() },
            JsonObject(holderKey?.let {
                buildJsonObject { put("jwk", it.exportJWKObject()) }
            } ?: buildJsonObject { put("kid", holderDid) }),
            vct = data.request.credentialConfigurationId
        ),
        disclosureMap = request.selectiveDisclosure ?: SDMap(emptyMap())
    )

    private suspend fun IssuanceSessionData.nonSdJwtVc(
        vc: W3CVC,
        issuerKid: String,
        holderDid: String?,
        holderKey: JsonObject?
    ) = vc.mergingJwtIssue(
        issuerKey = issuerKey.key,
        issuerDid = issuerDid,
        issuerKid = issuerKid,
        subjectDid = holderDid ?: "",
        mappings = request.mapping ?: JsonObject(emptyMap()),
        additionalJwtHeader = emptyMap(),
        additionalJwtOptions = holderKey?.let {
            mapOf(
                "cnf" to buildJsonObject { put("jwk", it) }
            )
        } ?: emptyMap(),
    )
}
