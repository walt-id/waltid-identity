@file:Suppress("ExtractKtorModule")
@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.issuance

import cbor.Cbor
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jose.util.X509CertUtils
import id.walt.commons.config.ConfigManager
import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig
import id.walt.mdoc.COSECryptoProviderKeyInfo
import id.walt.mdoc.SimpleCOSECryptoProvider
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.DataElement
import id.walt.mdoc.dataelement.json.toDataElement
import id.walt.mdoc.doc.MDocBuilder
import id.walt.mdoc.mso.DeviceKeyInfo
import id.walt.mdoc.mso.ValidityInfo
import id.walt.oid4vc.OpenID4VC
import id.walt.oid4vc.OpenID4VCI
import id.walt.oid4vc.OpenID4VCIVersion
import id.walt.oid4vc.data.*
import id.walt.oid4vc.definitions.JWTClaims
import id.walt.oid4vc.definitions.OPENID_CREDENTIAL_AUTHORIZATION_TYPE
import id.walt.oid4vc.errors.AuthorizationError
import id.walt.oid4vc.errors.CredentialError
import id.walt.oid4vc.errors.DeferredCredentialError
import id.walt.oid4vc.errors.TokenError
import id.walt.oid4vc.interfaces.CredentialResult
import id.walt.oid4vc.providers.CredentialIssuerConfig
import id.walt.oid4vc.providers.TokenTarget
import id.walt.oid4vc.requests.*
import id.walt.oid4vc.responses.*
import id.walt.oid4vc.util.COSESign1Utils
import id.walt.oid4vc.util.JwtUtils
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.*
import org.cose.java.AlgorithmID
import org.cose.java.OneKey
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/**
 * OIDC for Verifiable Credential Issuance service provider, implementing abstract service provider from OIDC4VC library.
 */
open class CIProvider(
    val baseUrl: String = let { ConfigManager.getConfig<OIDCIssuerServiceConfig>().baseUrl + "/${OpenID4VCIVersion.DRAFT13.versionString}" },
    val baseUrlDraft11: String = let { ConfigManager.getConfig<OIDCIssuerServiceConfig>().baseUrl + "/${OpenID4VCIVersion.DRAFT11.versionString}" },

    val config: CredentialIssuerConfig = CredentialIssuerConfig(
        credentialConfigurationsSupported = ConfigManager.getConfig<CredentialTypeConfig>().parse()
    )
) {

    val metadata
        get() = (OpenID4VCI.createDefaultProviderMetadata(
            baseUrl = baseUrl,
            credentialSupported = config.credentialConfigurationsSupported,
            version = OpenID4VCIVersion.DRAFT13
        ) as OpenIDProviderMetadata.Draft13)

    val metadataDraft11
        get() = (OpenID4VCI.createDefaultProviderMetadata(
            baseUrl = baseUrlDraft11,
            credentialSupported = config.credentialConfigurationsSupported,
            version = OpenID4VCIVersion.DRAFT11
        ) as OpenIDProviderMetadata.Draft11)

    val openIdMetadata
        get() = (OpenID4VCI.createDefaultProviderMetadata(
            baseUrl = baseUrl,
            version = OpenID4VCIVersion.DRAFT13
        ) as OpenIDProviderMetadata.Draft13)

    val openIdMetadataDraft11
        get() = (OpenID4VCI.createDefaultProviderMetadata(
            baseUrl = baseUrlDraft11,
            version = OpenID4VCIVersion.DRAFT11
        ) as OpenIDProviderMetadata.Draft11)

    companion object {
        private val log = KotlinLogging.logger { }
        private val http = HttpClient {
            install(ContentNegotiation) {
                json()
            }
            defaultRequest {
                contentType(ContentType.Application.Json)
            }
        }

        val CI_TOKEN_KEY =
            runBlocking { KeyManager.resolveSerializedKey(ConfigManager.getConfig<OIDCIssuerServiceConfig>().ciTokenKey) }

        suspend fun sendCallback(
            sessionId: String,
            type: String,
            data: JsonObject,
            callbackUrl: String
        ) {
            try {
                val response = http.post(callbackUrl.replace("\$id", sessionId)) {
                    setBody(buildJsonObject {
                        put("id", sessionId)
                        put("type", type)
                        put("data", data)
                    })
                }
                log.trace { "Sent issuance status callback: $callbackUrl, $type, $sessionId; response: ${response.status}" }
            } catch (ex: Exception) {
                // Never break issuance flow due to callback; just log
                log.warn(ex) { "Failed to send issuance callback to $callbackUrl for session $sessionId (type=$type)" }
            }
        }
    }

    // -------------------------------
    // Issuance status & callback helpers
    private suspend fun emitIssuanceStatus(session: IssuanceSession) {
        if (session.callbackUrl.isNullOrBlank()) return
        val credConfId = session.issuanceRequests.firstOrNull()?.credentialConfigurationId
        val data = buildJsonObject {
            put("sessionId", JsonPrimitive(session.id))
            put("status", JsonPrimitive(session.status.name))
            if (session.statusReason != null) put("reason", JsonPrimitive(session.statusReason))
            put("closed", JsonPrimitive(session.isClosed))
            if (credConfId != null) put("credentialConfigurationId", JsonPrimitive(credConfId))
        }
        sendCallback(session.id, "issuance_status", data, session.callbackUrl)
    }

    fun updateSessionStatus(
        session: IssuanceSession,
        newStatus: IssuanceSessionStatus,
        reason: String? = null,
        close: Boolean = false,
    ): IssuanceSession {
        val updated = session.copy(
            status = newStatus,
            statusReason = reason,
            isClosed = session.isClosed || close
        )
        val remaining =
            (updated.expirationTimestamp - Clock.System.now()).let { d -> if (d.isNegative()) 0.minutes else d }
        putSession(updated.id, updated, remaining)
        runBlocking {
            try {
                emitIssuanceStatus(updated)
            } catch (_: Exception) {
            }
        }
        return updated
    }

    // -------------------------------
    // Simple in-memory session management
    private val authSessions = ConfiguredPersistence<IssuanceSession>(
        "auth_sessions", defaultExpiration = 5.minutes,
        encoding = { Json.encodeToString(it) },
        decoding = { Json.decodeFromString(it) },
    )


    private val deferredCredentialRequests = ConfiguredPersistence<CredentialRequest>(
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

    private fun putSession(id: String, session: IssuanceSession, ttl: Duration? = null) {
        log.debug { "SETTING CI AUTH SESSION: $id = $session" }
        authSessions.set(id, session, ttl)
    }

    private fun removeSession(id: String) {
        log.debug { "REMOVING CI AUTH SESSION: $id" }
        authSessions.remove(id)
    }

    private fun getVerifiedSession(sessionId: String): IssuanceSession? {
        return getSession(sessionId)?.let {
            if (it.isExpired) {
                // Mark session as expired, persist, emit callback, then remove
                val expiredSession = it.copy(
                    status = IssuanceSessionStatus.EXPIRED,
                    statusReason = "Issuance session expired",
                    isClosed = true
                )
                val remaining = 0.minutes
                putSession(sessionId, expiredSession, remaining)
                runBlocking {
                    if (!expiredSession.callbackUrl.isNullOrEmpty()) {
                        try {
                            emitIssuanceStatus(expiredSession)
                        } catch (e: Exception) {
                            // ignore
                        }
                    }
                }
                removeSession(sessionId)
                null
            } else {
                it
            }
        }
    }

    // -------------------------------------
    // Implementation of abstract issuer service provider interface
    private fun generateCredential(credentialRequest: CredentialRequest, session: IssuanceSession): CredentialResult {
        log.debug { "GENERATING CREDENTIAL:" }
        log.debug { "Credential request: $credentialRequest" }
        log.debug { "CREDENTIAL REQUEST JSON -------:" }
        log.debug { Json.encodeToString(credentialRequest) }

        if (session.issuanceRequests.firstOrNull()?.issuanceType != null && session.issuanceRequests.firstOrNull()!!.issuanceType == "DEFERRED") {
            return CredentialResult(
                format = credentialRequest.format,
                credential = null,
                credentialId = randomUUIDString()
            ).also {
                deferredCredentialRequests[it.credentialId!!] = credentialRequest
            }
        }

        return when (credentialRequest.format) {
            CredentialFormat.mso_mdoc -> runBlocking { doGenerateMDoc(credentialRequest, session) }
            else -> doGenerateCredential(credentialRequest, session)
        }
    }

    private fun getDeferredCredential(
        credentialID: String,
        session: IssuanceSession
    ): CredentialResult {
        return deferredCredentialRequests[credentialID]?.let {
            when (it.format) {
                CredentialFormat.mso_mdoc -> runBlocking { doGenerateMDoc(it, session) }
                else -> doGenerateCredential(it, session)
            }
        } ?: throw DeferredCredentialError(
            errorCode = CredentialErrorCode.invalid_request,
            message = "Invalid credential ID given"
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun doGenerateCredential(
        credentialRequest: CredentialRequest,
        issuanceSession: IssuanceSession
    ): CredentialResult {
        if (credentialRequest.format == CredentialFormat.mso_mdoc) throw CredentialError(
            credentialRequest = credentialRequest,
            errorCode = CredentialErrorCode.unsupported_credential_format
        )

        val proofHeader = credentialRequest.proof?.jwt?.let { JwtUtils.parseJWTHeader(it) }
            ?: throw CredentialError(
                credentialRequest = credentialRequest,
                errorCode = CredentialErrorCode.invalid_or_missing_proof,
                message = "Proof must be JWT proof"
            )

        val holderKid = proofHeader[JWTClaims.Header.keyID]?.jsonPrimitive?.content
        val holderKey = proofHeader[JWTClaims.Header.jwk]?.jsonObject

        if (holderKey.isNullOrEmpty() && holderKid.isNullOrEmpty()) throw CredentialError(
            credentialRequest = credentialRequest,
            errorCode = CredentialErrorCode.invalid_or_missing_proof,
            message = "Proof JWT header must contain kid or jwk claim"
        )

        log.debug { "RETRIEVING ISSUANCE REQUEST FOR CREDENTIAL REQUEST" }

        val request = findMatchingIssuanceRequest(
            credentialRequest = credentialRequest,
            issuanceRequests = issuanceSession.issuanceRequests
        )
            ?: throw IllegalArgumentException("No matching issuance request found for this session: ${issuanceSession.id}!")

        val credential = JsonPrimitive(runBlocking {
            val vc = request.credentialData
                ?: throw MissingFieldException(listOf("credentialData"), "credentialData")

            val resolvedIssuerKey = KeyManager.resolveSerializedKey(request.issuerKey)

            val x5c = request.x5Chain?.map {
                X509CertUtils.parse(it).encoded.encodeToBase64()
            }

            request.run {
                when (credentialFormat) {
                    CredentialFormat.sd_jwt_vc -> OpenID4VCI.generateSdJwtVC(
                        credentialRequest = credentialRequest,
                        credentialData = vc,
                        issuerId = issuerDid ?: baseUrl,
                        issuerKey = resolvedIssuerKey,
                        selectiveDisclosure = request.selectiveDisclosure,
                        dataMapping = request.mapping,
                        display = credentialRequest.display ?: vc["display"]?.jsonArray?.map {
                            DisplayProperties.fromJSON(it.jsonObject)
                        },
                        x5Chain = x5c,
                    ).also {
                        if (!issuanceSession.callbackUrl.isNullOrEmpty())
                            sendCallback(
                                sessionId = issuanceSession.id,
                                type = "sdjwt_issue",
                                data = buildJsonObject { put("sdjwt", it) },
                                callbackUrl = issuanceSession.callbackUrl
                            )
                    }

                    else -> OpenID4VCI.generateW3CJwtVC(
                        credentialRequest = credentialRequest,
                        credentialData = vc,
                        issuerKey = resolvedIssuerKey,
                        issuerId = issuerDid
                            ?: throw BadRequestException("Issuer API currently supports only issuer DID for issuer ID property in W3C credentials. Issuer DID was not given in issuance request."),
                        selectiveDisclosure = request.selectiveDisclosure,
                        dataMapping = request.mapping,
                        x5Chain = x5c,
                        display = credentialRequest.display

                    ).also {
                        if (!issuanceSession.callbackUrl.isNullOrEmpty())
                            sendCallback(
                                sessionId = issuanceSession.id,
                                type = "jwt_issue",
                                data = buildJsonObject { put("jwt", it) },
                                callbackUrl = issuanceSession.callbackUrl
                            )
                    }
                }
            }.also { log.debug { "Respond VC: $it" } }
        })

        return CredentialResult(
            format = credentialRequest.format,
            credential = credential
        )
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun extractHolderKey(proof: ProofOfPossession): COSECryptoProviderKeyInfo? {
        when (proof.proofType) {
            ProofType.cwt -> {
                return proof.cwt?.base64UrlDecode()?.let {
                    COSESign1Utils.extractHolderKey(Cbor.decodeFromByteArray<COSESign1>(it))
                }
            }

            else -> {
                return proof.jwt?.let { JwtUtils.parseJWTHeader(it) }?.get(JWTClaims.Header.jwk)?.jsonObject?.let {
                    JWKKey.importJWK(it.toString()).getOrNull()?.let { key ->
                        COSECryptoProviderKeyInfo(
                            keyID = key.getKeyId(),
                            algorithmID = AlgorithmID.ECDSA_256,
                            publicKey = ECKey.parse(key.exportJWK()).toECPublicKey(),
                            privateKey = null
                        )
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun doGenerateMDoc(
        credentialRequest: CredentialRequest,
        issuanceSession: IssuanceSession
    ): CredentialResult {
        val proof = credentialRequest.proof ?: throw CredentialError(
            credentialRequest = credentialRequest,
            errorCode = CredentialErrorCode.invalid_or_missing_proof,
            message = "No proof found on credential request"
        )
        val holderKey = extractHolderKey(proof) ?: throw CredentialError(
            credentialRequest = credentialRequest,
            errorCode = CredentialErrorCode.invalid_or_missing_proof,
            message = "No holder key could be extracted from proof"
        )

        val nonce = OpenID4VCI.getNonceFromProof(credentialRequest.proof!!) ?: throw CredentialError(
            credentialRequest = credentialRequest,
            errorCode = CredentialErrorCode.invalid_or_missing_proof,
            message = "No nonce found on proof"
        )

        log.debug { "RETRIEVING ISSUANCE REQUEST FOR CREDENTIAL REQUEST: $nonce" }

        val request = findMatchingIssuanceRequest(
            credentialRequest = credentialRequest,
            issuanceRequests = issuanceSession.issuanceRequests
        )
            ?: throw IllegalArgumentException("No matching issuance request found for this session: ${issuanceSession.id}!")

        val issuerSignedItems = request.mdocData ?: throw MissingFieldException(listOf("mdocData"), "mdocData")

        val resolvedIssuerKey = KeyManager.resolveSerializedKey(request.issuerKey)

        val issuerKey = JWK.parse(resolvedIssuerKey.exportJWK()).toECKey()

        val keyID = resolvedIssuerKey.getKeyId()

        val cryptoProvider = SimpleCOSECryptoProvider(
            listOf(
                COSECryptoProviderKeyInfo(
                    keyID = keyID,
                    algorithmID = AlgorithmID.ECDSA_256,
                    publicKey = issuerKey.toECPublicKey(),
                    privateKey = issuerKey.toECPrivateKey(),
                    x5Chain = request.x5Chain?.map { X509CertUtils.parse(it) } ?: listOf(),
                    trustedRootCAs = request.trustedRootCAs?.map { X509CertUtils.parse(it) } ?: listOf()
                )
            )
        )

        val mdoc = MDocBuilder(
            credentialRequest.docType
                ?: throw CredentialError(
                    credentialRequest = credentialRequest,
                    errorCode = CredentialErrorCode.invalid_request,
                    message = "Missing doc type in credential request"
                )
        ).apply {
            issuerSignedItems.forEach { namespace ->
                namespace.value.forEach { property ->
                    addItemToSign(
                        nameSpace = namespace.key,
                        elementIdentifier = property.key,
                        elementValue = property.value.toDataElement(),
                    )
                }
            }
        }.sign( // TODO: expiration date!
            validityInfo = ValidityInfo(
                signed = Clock.System.now(),
                validFrom = Clock.System.now(),
                validUntil = Clock.System.now().plus(365 * 24, DateTimeUnit.HOUR)
            ),
            deviceKeyInfo = DeviceKeyInfo(
                deviceKey = DataElement.fromCBOR(
                    OneKey(
                        holderKey.publicKey,
                        null
                    ).AsCBOR().EncodeToBytes()
                )
            ),
            cryptoProvider = cryptoProvider,
            keyID = keyID
        ).also {
            if (!issuanceSession.callbackUrl.isNullOrEmpty())
                sendCallback(
                    sessionId = issuanceSession.id,
                    type = "generated_mdoc",
                    data = buildJsonObject { put("mdoc", it.toCBORHex()) },
                    callbackUrl = issuanceSession.callbackUrl
                )
        }
        return CredentialResult(
            format = CredentialFormat.mso_mdoc,
            credential = JsonPrimitive(mdoc.issuerSigned.toMapElement().toCBOR().encodeToBase64Url()),
            customParameters = mapOf("credential_encoding" to JsonPrimitive("issuer-signed"))
        )
    }

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
                    credentialRequest = credReq,
                    errorCode = CredentialErrorCode.invalid_or_missing_proof,
                    message = "Proof must be JWT proof"
                )
        }.distinct()

        require(keyIdsDistinct.size < 2) { "More than one key id requested" }

        return BatchCredentialResponse.success(
            credentialResponses = batchCredentialRequest.credentialRequests.map {
                generateCredentialResponse(
                    credentialRequest = it,
                    session = session
                )
            }
        )
    }

    suspend fun getJwksSessions(): JsonObject {
        var jwksList = buildJsonObject {
            put("keys", buildJsonArray {
                add(buildJsonObject {
                    CI_TOKEN_KEY.getPublicKey().exportJWKObject().forEach {
                        put(it.key, it.value)
                    }
                    put(JWTClaims.Header.keyID, CI_TOKEN_KEY.getKeyId())
                })
            })
        }
        authSessions.getAll().forEach { session ->
            session.issuanceRequests.forEach {
                val resolvedIssuerKey = KeyManager.resolveSerializedKey(it.issuerKey)
                jwksList = buildJsonObject {
                    put("keys", buildJsonArray {
                        val jwkWithKid = buildJsonObject {
                            resolvedIssuerKey.getPublicKey().exportJWKObject().forEach {
                                put(it.key, it.value)
                            }
                            put(JWTClaims.Header.keyID, resolvedIssuerKey.getPublicKey().getKeyId())
                        }
                        add(jwkWithKid)
                        jwksList.forEach {
                            it.value.jsonArray.forEach {
                                add(it)
                            }
                        }
                    })
                }
            }
        }
        return jwksList
    }

    fun getFormatByCredentialConfigurationId(id: String) = metadata.credentialConfigurationsSupported?.get(id)?.format

    private fun getTypesByCredentialConfigurationId(id: String) =
        metadata.credentialConfigurationsSupported?.get(id)?.credentialDefinition?.type

    private fun getDocTypeByCredentialConfigurationId(id: String) =
        metadata.credentialConfigurationsSupported?.get(id)?.docType

    // Use format, type, vct and docType checks to filter matching entries
    private fun findMatchingIssuanceRequest(
        credentialRequest: CredentialRequest,
        issuanceRequests: List<IssuanceRequest>
    ): IssuanceRequest? {
        return issuanceRequests.find { sessionData ->
            val credentialConfigurationId = sessionData.credentialConfigurationId
            val credentialFormat = getFormatByCredentialConfigurationId(credentialConfigurationId)
            log.debug {
                "Checking format - Request format: ${credentialRequest.format}, " +
                        "Session format: $credentialFormat"
            }

            require(credentialFormat == credentialRequest.format) { "Format does not match" }
            // Depending on the format, perform specific checks
            val additionalMatches =
                when (credentialRequest.format) {
                    CredentialFormat.jwt_vc_json, CredentialFormat.jwt_vc -> {
                        val types = getTypesByCredentialConfigurationId(credentialConfigurationId)
                        // same order, same elements
                        (types == credentialRequest.credentialDefinition?.type) || (types == credentialRequest.types)
                    }

                    CredentialFormat.sd_jwt_vc -> {
                        val vct = metadata.getVctByCredentialConfigurationId(credentialConfigurationId)
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
        // Calculate remaining TTL based on session expiration
        val remainingTtl = session.expirationTimestamp.let {
            val now = Clock.System.now()
            if (it > now) {
                it - now  // Calculate duration between now and expiration
            } else {
                null  // Already expired
            }
        }

        return session.copy(
            cNonce = randomUUIDString()
        ).also {
            putSession(it.id, it, remainingTtl)
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

    private fun validateAuthorizationRequest(authorizationRequest: AuthorizationRequest): Boolean {
        return authorizationRequest.authorizationDetails != null && authorizationRequest.authorizationDetails!!.any {
            isSupportedAuthorizationDetails(it)
        }
    }

    fun initializeIssuanceSession(
        authorizationRequest: AuthorizationRequest,
        expiresIn: Duration,
        authServerState: String?, //the state used for additional authentication with pwd, id_token or vp_token.
    ): IssuanceSession {
        return if (authorizationRequest.issuerState.isNullOrEmpty()) {
            if (!validateAuthorizationRequest(authorizationRequest)) {
                throw AuthorizationError(
                    authorizationRequest = authorizationRequest,
                    errorCode = AuthorizationErrorCode.invalid_request,
                    message = "No valid authorization details for credential issuance found on authorization request"
                )
            }
            IssuanceSession(
                id = randomUUIDString(),
                authorizationRequest = authorizationRequest,
                expirationTimestamp = Clock.System.now().plus(expiresIn),
                issuanceRequests = listOf(),
                authServerState = authServerState
            )
        } else {
            getVerifiedSession(authorizationRequest.issuerState!!)?.copy(authorizationRequest = authorizationRequest)
                ?: throw AuthorizationError(
                    authorizationRequest = authorizationRequest,
                    errorCode = AuthorizationErrorCode.invalid_request,
                    message = "No valid issuance session found for given issuer state"
                )
        }.also {
            val updatedSession = IssuanceSession(
                id = it.id,
                authorizationRequest = authorizationRequest,
                expirationTimestamp = Clock.System.now().plus(expiresIn),
                issuanceRequests = it.issuanceRequests,
                authServerState = authServerState,
                txCode = it.txCode,
                txCodeValue = it.txCodeValue,
                credentialOffer = it.credentialOffer,
                cNonce = it.cNonce,
                callbackUrl = it.callbackUrl,
                customParameters = it.customParameters
            )
            putSession(it.id, updatedSession, expiresIn)
        }
    }

    open fun initializeCredentialOffer(
        issuanceRequests: List<IssuanceRequest>,
        expiresIn: Duration,
        callbackUrl: String? = null,
        txCode: TxCode? = null,
        txCodeValue: String? = null,
        standardVersion: OpenID4VCIVersion = OpenID4VCIVersion.DRAFT13
    ): IssuanceSession = runBlocking {
        val sessionId = randomUUIDString()

        val credentialOfferBuilder = OidcIssuance.issuanceRequestsToCredentialOfferBuilder(
            issuanceRequests = issuanceRequests,
            standardVersion = standardVersion
        )

        when (issuanceRequests[0].authenticationMethod) {
            AuthenticationMethod.PRE_AUTHORIZED -> {
                credentialOfferBuilder.addPreAuthorizedCodeGrant(
                    preAuthCode = OpenID4VC.generateAuthorizationCodeFor(sessionId, metadata.issuer!!, CI_TOKEN_KEY),
                    txCode = txCode
                )
            }

            else -> {
                credentialOfferBuilder.addAuthorizationCodeGrant(sessionId)
            }
        }

        return@runBlocking IssuanceSession(
            id = sessionId,
            authorizationRequest = null,
            expirationTimestamp = Clock.System.now().plus(expiresIn),
            issuanceRequests = issuanceRequests,
            txCode = txCode,
            txCodeValue = txCodeValue,
            credentialOffer = credentialOfferBuilder.build(),
            callbackUrl = callbackUrl
        ).also {
            putSession(it.id, it, expiresIn)
        }
    }

    private fun createCredentialResponseFor(
        credentialResult: CredentialResult,
        session: IssuanceSession
    ): CredentialResponse = runBlocking {
        return@runBlocking credentialResult.credential?.let { credential ->
            // Successful immediate issuance
            updateSessionStatus(
                session,
                IssuanceSessionStatus.SUCCESSFUL,
                "Credential issued successfully",
                close = true
            )
            CredentialResponse.success(
                format = credentialResult.format,
                credential = credential,
                cNonce = session.cNonce,
                cNonceExpiresIn = (session.expirationTimestamp - Clock.System.now()),
                customParameters = credentialResult.customParameters,
            )
        } ?: generateProofOfPossessionNonceFor(session).let { updatedSession ->
            // Deferred issuance: keep session ACTIVE, do not close
            CredentialResponse.deferred(
                format = credentialResult.format,
                acceptanceToken = OpenID4VCI.generateDeferredCredentialToken(
                    sessionId = session.id,
                    issuer = metadata.issuer ?: throw Exception("No issuer defined in provider metadata"),
                    credentialId = credentialResult.credentialId
                        ?: throw Exception("credentialId must not be null, if credential issuance is deferred."),
                    tokenKey = CI_TOKEN_KEY
                ),
                cNonce = updatedSession.cNonce,
                cNonceExpiresIn = (updatedSession.expirationTimestamp - Clock.System.now())
            )
        }
    }

    fun generateCredentialResponse(
        credentialRequest: CredentialRequest,
        session: IssuanceSession
    ): CredentialResponse =
        runBlocking {
            // access_token should be validated on API level and issuance session extracted
            // Validate credential request (proof of possession, etc.)
            val nonce = session.cNonce
                ?: throw CredentialError(
                    credentialRequest = credentialRequest,
                    errorCode = CredentialErrorCode.invalid_or_missing_proof,
                    message = "No cNonce found on current issuance session"
                )

            val validationResult = OpenID4VCI.validateCredentialRequest(
                credentialRequest = credentialRequest,
                nonce = nonce,
                openIDProviderMetadata = metadata
            )

            if (!validationResult.success) throw CredentialError(
                credentialRequest = credentialRequest,
                errorCode = CredentialErrorCode.invalid_request,
                message = validationResult.message
            )

            // create credential result
            val credentialResult = generateCredential(
                credentialRequest = credentialRequest,
                session = session
            )

            return@runBlocking createCredentialResponseFor(
                credentialResult = credentialResult,
                session = session
            )
        }

    fun generateDeferredCredentialResponse(acceptanceToken: String): CredentialResponse = runBlocking {
        val accessInfo =
            OpenID4VC.verifyAndParseToken(
                token = acceptanceToken,
                issuer = metadata.issuer!!,
                target = TokenTarget.DEFERRED_CREDENTIAL,
                tokenKey = CI_TOKEN_KEY
            )

        val sessionId = accessInfo[JWTClaims.Payload.subject]!!.jsonPrimitive.content
        val credentialId = accessInfo[JWTClaims.Payload.jwtID]!!.jsonPrimitive.content
        val session = getVerifiedSession(sessionId)
            ?: throw DeferredCredentialError(
                errorCode = CredentialErrorCode.invalid_token,
                errorUri = "Session not found for given access token, or session expired."
            )

        // issue credential for credential request
        val response = createCredentialResponseFor(
            credentialResult = getDeferredCredential(
                credentialID = credentialId,
                session = session
            ),
            session = session
        )
        // Mark successful after deferred issuance is actually delivered
        updateSessionStatus(session, IssuanceSessionStatus.SUCCESSFUL, "Credential issued successfully", close = true)
        return@runBlocking response
    }

    fun processTokenRequest(tokenRequest: TokenRequest): TokenResponse = runBlocking {
        val payload = OpenID4VC.validateAndParseTokenRequest(
            tokenRequest = tokenRequest,
            issuer = metadata.issuer!!,
            tokenKey = CI_TOKEN_KEY
        )

        val sessionId = payload[JWTClaims.Payload.subject]?.jsonPrimitive?.content ?: throw TokenError(
            tokenRequest = tokenRequest,
            errorCode = TokenErrorCode.invalid_request,
            message = "Token contains no session ID in subject"
        )

        val session = getVerifiedSession(sessionId) ?: throw TokenError(
            tokenRequest = tokenRequest,
            errorCode = TokenErrorCode.invalid_request,
            message = "No authorization session found for given authorization code, or session expired."
        )

        if (tokenRequest is TokenRequest.PreAuthorizedCode &&
            session.txCode != null &&
            session.txCodeValue != tokenRequest.txCode
        ) {
            throw TokenError(
                tokenRequest = tokenRequest,
                errorCode = TokenErrorCode.invalid_grant,
                message = "User PIN required for this issuance session has not been provided or PIN is wrong."
            )
        }

        // Expiration time required by EBSI
        val currentTime = Clock.System.now().epochSeconds
        val expirationTime = (currentTime + 864000L) // ten days in milliseconds

        return@runBlocking TokenResponse.success(
            accessToken = OpenID4VC.generateToken(
                sub = sessionId,
                issuer = metadata.issuer!!,
                audience = TokenTarget.ACCESS,
                tokenId = null,
                tokenKey = CI_TOKEN_KEY
            ),
            tokenType = "bearer",
            expiresIn = expirationTime,
            cNonce = generateProofOfPossessionNonceFor(session).cNonce,
            cNonceExpiresIn = session.expirationTimestamp - Clock.System.now(),
            state = session.authorizationRequest?.state
        ).also {
            if (!session.callbackUrl.isNullOrEmpty())
                sendCallback(
                    sessionId = sessionId,
                    type = "requested_token",
                    data = buildJsonObject {
                        put("request", Json.encodeToJsonElement(session.issuanceRequests.first()))
                    },
                    callbackUrl = session.callbackUrl
                )
        }
    }

    private fun resolveBaseUrl(version: OpenID4VCIVersion): String {
        return when (version) {
            OpenID4VCIVersion.DRAFT13 -> baseUrl
            OpenID4VCIVersion.DRAFT11 -> baseUrlDraft11
            OpenID4VCIVersion.V1 -> throw IllegalArgumentException("standardVersion v1 is not supported")
        }
    }

    fun buildCredentialOfferUri(standardVersion: OpenID4VCIVersion, issuanceSessionId: String): String {
        val baseUrl = resolveBaseUrl(standardVersion)
        return "$baseUrl/credentialOffer?id=$issuanceSessionId"
    }

    fun buildOfferUri(
        offerRequest: CredentialOfferRequest
    ) = OpenID4VCI.getCredentialOfferRequestUrl(
        credOfferReq = offerRequest,
    )

    fun getMetadataByVersion(
        standardVersion: String?,
    ): OpenIDProviderMetadata {
        val version = OpenID4VCIVersion.from(
            standardVersion ?: throw IllegalArgumentException("standardVersion parameter is required")
        )

        return when (version) {
            OpenID4VCIVersion.DRAFT11 -> metadataDraft11
            OpenID4VCIVersion.DRAFT13 -> metadata
            OpenID4VCIVersion.V1 -> throw IllegalArgumentException("standardVersion v1 is not supported")
        }
    }

    fun getOpenIdProviderMetadataByVersion(
        standardVersion: String?,
    ): OpenIDProviderMetadata {
        val version = OpenID4VCIVersion.from(
            standardVersion ?: throw IllegalArgumentException("standardVersion parameter is required")
        )

        return when (version) {
            OpenID4VCIVersion.DRAFT11 -> openIdMetadataDraft11
            OpenID4VCIVersion.DRAFT13 -> openIdMetadata
            OpenID4VCIVersion.V1 -> throw IllegalArgumentException("standardVersion v1 is not supported")
        }
    }

    fun getPushedAuthorizationSession(authorizationRequest: AuthorizationRequest): IssuanceSession {
        return authorizationRequest.requestUri?.let {
            getVerifiedSession(OpenID4VC.getPushedAuthorizationSessionId(it)) ?: throw AuthorizationError(
                authorizationRequest = authorizationRequest,
                errorCode = AuthorizationErrorCode.invalid_request,
                message = "No session found for given request URI, or session expired"
            )
        } ?: throw AuthorizationError(
            authorizationRequest = authorizationRequest,
            errorCode = AuthorizationErrorCode.invalid_request,
            message = "Authorization request does not refer to a pushed authorization session"
        )
    }
}
