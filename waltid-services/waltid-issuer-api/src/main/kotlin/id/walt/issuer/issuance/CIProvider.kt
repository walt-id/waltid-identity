@file:Suppress("ExtractKtorModule")
@file:OptIn(ExperimentalTime::class)

package id.walt.issuer.issuance

import com.nimbusds.jose.util.X509CertUtils
import id.walt.commons.config.ConfigManager
import id.walt.commons.persistence.ConfiguredPersistence
import id.walt.cose.*
import id.walt.crypto.keys.EccUtils
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.KeyTypes
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.crypto.utils.Base64Utils.encodeToBase64
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.UuidUtils.randomUUIDString
import id.walt.issuer.config.CredentialTypeConfig
import id.walt.issuer.config.OIDCIssuerServiceConfig
import id.walt.mdoc.credsdata.CredentialManager
import id.walt.mdoc.encoding.MdocCbor
import id.walt.mdoc.objects.MdocsCborSerializer
import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.digest.ValueDigestList
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.mso.DeviceKeyInfo
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.objects.mso.ValidityInfo
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
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*
import net.orandja.obor.data.*
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import net.orandja.obor.codec.Cbor as OborCbor

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
    private suspend fun extractHolderKey(proof: ProofOfPossession): JWKKey? {
        when (proof.proofType) {
            ProofType.cwt -> {
                return proof.cwt?.base64UrlDecode()?.let { cwtBytes ->
                    try {
                        // Parse the CWT as a CoseSign1
                        val coseSign1 = CoseSign1.fromTagged(cwtBytes)
                        
                        // Decode protected headers using obor library to access COSE_Key or x5chain
                        // Protected headers are CBOR-encoded, so we decode them as a CborMap
                        val protectedHeadersMap = if (coseSign1.protected.isEmpty()) {
                            null
                        } else {
                            try {
                                OborCbor.decodeFromByteArray<CborObject>(coseSign1.protected) as? CborMap
                            } catch (e: Exception) {
                                log.warn(e) { "Failed to decode protected headers as CborMap: ${e.message}" }
                                null
                            }
                        }
                        
                        // Check for COSE_Key in protected headers (string key "COSE_Key")
                        val coseKeyBytes = protectedHeadersMap?.firstOrNull { entry ->
                            (entry.key as? CborText)?.value == "COSE_Key"
                        }?.value?.let { value ->
                            when (value) {
                                is CborBytes -> value.value
                                is ByteArray -> value
                                else -> null
                            }
                        }
                        
                        if (coseKeyBytes != null) {
                            // Decode the COSE_Key
                            val coseKey = coseCompliantCbor.decodeFromByteArray<CoseKey>(coseKeyBytes)
                            // Convert CoseKey to JWK using the toJWK() method
                            val jwkJson = coseKey.toJWK()
                            JWKKey.importJWK(jwkJson.toString()).getOrNull()
                        } else {
                            // Check for x5chain (label 33) in protected headers
                            // Try to find entry with integer key 33
                            // In obor, integer keys might be represented as CborObject that can be converted
                            val x5chainEntry = protectedHeadersMap?.firstOrNull { entry ->
                                try {
                                    // Try to convert the CborObject key to a number
                                    // First check if it's already a number type, or try to parse it
                                    val keyValue = when {
                                        entry.key.toString().toLongOrNull() == 33L -> true
                                        else -> {
                                            // Try to get the raw value - obor might represent integers differently
                                            // Check if the key's string representation equals "33"
                                            entry.key.toString() == "33"
                                        }
                                    }
                                    keyValue
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            
                            val certBytes = when (val x5chainValue = x5chainEntry?.value) {
                                is CborBytes -> x5chainValue.value
                                is ByteArray -> x5chainValue
                                is CborArray -> {
                                    // x5chain is a list, get the first certificate
                                    // CborArray should be iterable - try to get first element
                                    try {
                                        val firstItem = x5chainValue.firstOrNull()
                                        when (firstItem) {
                                            is CborBytes -> firstItem.value
                                            is ByteArray -> firstItem
                                            is CborObject -> {
                                                // If it's a CborObject wrapping bytes, try to extract
                                                (firstItem as? CborBytes)?.value
                                            }
                                            else -> null
                                        }
                                    } catch (e: Exception) {
                                        log.warn(e) { "Failed to extract certificate from x5chain array: ${e.message}" }
                                        null
                                    }
                                }
                                else -> null
                            }
                            
                            if (certBytes != null) {
                                JWKKey.importFromDerCertificate(certBytes).getOrNull()
                            } else {
                                // Also check unprotected headers for x5chain
                                coseSign1.unprotected.x5chain?.firstOrNull()?.let { cert ->
                                    JWKKey.importFromDerCertificate(cert.rawBytes).getOrNull()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log.warn(e) { "Failed to extract holder key from CWT proof: ${e.message}" }
                        null
                    }
                }
            }

            else -> {
                return proof.jwt?.let { JwtUtils.parseJWTHeader(it) }?.get(JWTClaims.Header.jwk)?.jsonObject?.let {
                    JWKKey.importJWK(it.toString()).getOrNull()
                }
            }
        }
    }

    /**
     * Converts a JsonElement to the appropriate Kotlin type for use in IssuerSignedItem.
     * This extracts the actual value from JsonPrimitive, converts dates to LocalDate,
     * converts number arrays to ByteArray when appropriate, and passes through complex types
     * for custom serializers to handle.
     * 
     * @param namespace The namespace of the data element (e.g., "org.iso.18013.5.1")
     * @param elementIdentifier The identifier of the data element (e.g., "birth_date", "portrait")
     */
    private fun JsonElement.toMdocValue(namespace: String, elementIdentifier: String): Any {
        return when (this) {
            is JsonPrimitive -> {
                when {
                    isString -> {
                        // Check if this is a date field and convert to LocalDate
                        // Date fields in mDL: birth_date, issue_date, expiry_date, portrait_capture_date
                        val dateFields = setOf("birth_date", "issue_date", "expiry_date", "portrait_capture_date")
                        if (dateFields.contains(elementIdentifier)) {
                            try {
                                // Parse date string in format "YYYY-MM-DD" to LocalDate
                                LocalDate.parse(content)
                            } catch (e: Exception) {
                                // If parsing fails, keep as string and let serializer handle it
                                log.warn(e) { "Failed to parse date string '$content' for $elementIdentifier, keeping as string" }
                                content
                            }
                        } else {
                            content
                        }
                    }
                    booleanOrNull != null -> boolean
                    intOrNull != null -> intOrNull!!
                    longOrNull != null -> longOrNull!!
                    doubleOrNull != null -> doubleOrNull!!.toLong() // Convert to Long for consistency
                    else -> content // Fallback to string content
                }
            }
            is JsonArray -> {
                // Check if this is a ByteArray field (like "portrait")
                // If all elements are numbers, convert to ByteArray
                val byteArrayFields = setOf("portrait", "signature_usual_mark", 
                    "biometric_template_face", "biometric_template_finger", 
                    "biometric_template_signature_sign", "biometric_template_iris")
                
                if (byteArrayFields.contains(elementIdentifier)) {
                    // Convert array of numbers to ByteArray
                    try {
                        mapNotNull { 
                            when (it) {
                                is JsonPrimitive -> it.intOrNull?.toByte() ?: it.longOrNull?.toByte()
                                else -> null
                            }
                        }.toByteArray()
                    } catch (e: Exception) {
                        log.warn(e) { "Failed to convert array to ByteArray for $elementIdentifier, keeping as List" }
                        // Fallback: convert array elements recursively
                        map { it.toMdocValue(namespace, elementIdentifier) }
                    }
                } else {
                    // Check if there's a registered serializer for this element
                    val serializer = MdocsCborSerializer.lookupSerializer(namespace, elementIdentifier)
                    if (serializer != null && serializer.descriptor.kind is StructureKind.LIST) {
                        // This is a list of complex objects (e.g., driving_privileges)
                        // Get the element descriptor to find the inner serializer
                        val elementDescriptor = serializer.descriptor.getElementDescriptor(0)
                        try {
                            // Try to deserialize using the registered serializer directly
                            // The serializer knows how to handle the list structure
                            @Suppress("UNCHECKED_CAST")
                            Json.decodeFromJsonElement(serializer as kotlinx.serialization.KSerializer<Any>, this)
                        } catch (e: Exception) {
                            log.warn(e) { "Failed to deserialize array using registered serializer for $elementIdentifier, falling back to element-by-element conversion" }
                            // Fallback: try to deserialize each element individually
                            // We need to find the element serializer - try to get it from the descriptor
                            try {
                                map { it.toMdocValue(namespace, elementIdentifier) }
                            } catch (e2: Exception) {
                                log.warn(e2) { "Failed to deserialize array elements for $elementIdentifier, keeping as List" }
                                // Final fallback: convert array elements recursively
                                map { it.toMdocValue(namespace, elementIdentifier) }
                            }
                        }
                    } else {
                        // For other arrays without registered serializers, convert elements recursively
                        map { it.toMdocValue(namespace, elementIdentifier) }
                    }
                }
            }
            is JsonObject -> {
                // Check if there's a registered serializer for this element
                val serializer = MdocsCborSerializer.lookupSerializer(namespace, elementIdentifier)
                if (serializer != null) {
                    // Deserialize the JsonObject to the actual type
                    try {
                        @Suppress("UNCHECKED_CAST")
                        Json.decodeFromJsonElement(serializer as kotlinx.serialization.KSerializer<Any>, this)
                    } catch (e: Exception) {
                        log.warn(e) { "Failed to deserialize JsonObject for $elementIdentifier, falling back to Map conversion" }
                        // Fallback: convert to Map
                        mapValues { it.value.toMdocValue(namespace, elementIdentifier) }
                    }
                } else {
                    // No registered serializer, convert to Map
                    mapValues { it.value.toMdocValue(namespace, elementIdentifier) }
                }
            }
            is JsonNull -> throw IllegalArgumentException("Null values are not supported in mdoc data")
        }
    }

    /**
     * Converts a Key to a CoseKey by extracting the JWK parameters and mapping them to COSE format.
     */
    private suspend fun Key.toCoseKey(): CoseKey {
        val jwk = exportJWKObject()
        val kty = jwk["kty"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing kty in JWK")
        val crv = jwk["crv"]?.jsonPrimitive?.content
        
        val coseKty = when (kty) {
            "EC" -> Cose.KeyTypes.EC2
            "OKP" -> Cose.KeyTypes.OKP
            else -> throw IllegalArgumentException("Unsupported key type: $kty")
        }
        
        val coseCrv = when (crv) {
            "P-256" -> Cose.EllipticCurves.P_256
            "P-384" -> Cose.EllipticCurves.P_384
            "P-521" -> Cose.EllipticCurves.P_521
            "Ed25519" -> Cose.EllipticCurves.Ed25519
            "Ed448" -> Cose.EllipticCurves.Ed448
            "secp256k1" -> Cose.EllipticCurves.secp256k1
            else -> throw IllegalArgumentException("Unsupported curve: $crv")
        }
        
        val x = jwk["x"]?.jsonPrimitive?.content?.base64UrlDecode()
            ?: throw IllegalArgumentException("Missing x coordinate in JWK")
        val y = jwk["y"]?.jsonPrimitive?.content?.base64UrlDecode()
        
        return CoseKey(
            kty = coseKty,
            crv = coseCrv,
            x = x,
            y = y
        )
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

        val issuerSignedItemsData = request.mdocData ?: throw MissingFieldException(listOf("mdocData"), "mdocData")

        // Initialize CredentialManager to register serializers for complex types
        CredentialManager.init()

        val docType = credentialRequest.docType
            ?: throw CredentialError(
                credentialRequest = credentialRequest,
                errorCode = CredentialErrorCode.invalid_request,
                message = "Missing doc type in credential request"
            )

        val resolvedIssuerKey = KeyManager.resolveSerializedKey(request.issuerKey)
        val issuerKey = resolvedIssuerKey

        // Convert holder key to CoseKey
        val deviceCoseKey = holderKey.toCoseKey()
        val deviceKeyInfo = DeviceKeyInfo(deviceKey = deviceCoseKey)

        // Create validity info
        val now = Clock.System.now()
        val validityInfo = ValidityInfo(
            signed = now,
            validFrom = now,
            validUntil = now.plus(365 * 24, DateTimeUnit.HOUR)
        )

        // Create IssuerSignedItem objects for each data element
        val namespacedIssuerSignedItems = mutableMapOf<String, MutableList<IssuerSignedItem>>()
        var digestIdCounter = 0u

        issuerSignedItemsData.forEach { (namespace, properties) ->
            val items = mutableListOf<IssuerSignedItem>()
            properties.forEach { (elementIdentifier, elementValue) ->
                // Convert JsonElement to appropriate Kotlin type
                // Pass namespace and elementIdentifier for smart conversion (dates, ByteArrays, etc.)
                val convertedValue = elementValue.toMdocValue(namespace, elementIdentifier)
                items.add(
                    IssuerSignedItem(
                        digestId = digestIdCounter++,
                        random = Random.nextBytes(16),
                        elementIdentifier = elementIdentifier,
                        elementValue = convertedValue
                    )
                )
            }
            namespacedIssuerSignedItems[namespace] = items
        }

        // Calculate value digests for each namespace
        val digestAlgorithm = "SHA-256"
        val valueDigests = namespacedIssuerSignedItems.map { (namespace, items) ->
            val digests = items.map { item ->
                ValueDigest.fromIssuerSignedItem(item, namespace, digestAlgorithm)
            }
            namespace to ValueDigestList(digests)
        }.toMap()

        // Create Mobile Security Object
        val mso = MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = digestAlgorithm,
            docType = docType,
            valueDigests = valueDigests,
            deviceKeyInfo = deviceKeyInfo,
            validityInfo = validityInfo
        )

        // Serialize MSO to CBOR and wrap in tag #6.24
        val msoBytes = MdocCbor.encodeToByteArray(mso)
        val msoPayload = byteArrayOf(0xd8.toByte(), 24.toByte()) + MdocCbor.encodeToByteArray(ByteArraySerializer(), msoBytes)

        // Create COSE headers with algorithm and certificate chain
        val algorithm = issuerKey.keyType.toCoseAlgorithm()
            ?: throw IllegalArgumentException("Unsupported key type for COSE signing: ${issuerKey.keyType}")
        
        val x5chain = request.x5Chain?.map { cert ->
            val certBytes = try {
                // Normalize the certificate string - handle escaped newlines and whitespace
                val normalizedCert = cert
                    .replace("\\n", "\n")  // Unescape newlines
                    .replace("\\r", "\r")  // Unescape carriage returns
                    .trim()
                
                // Check if it's PEM format (contains BEGIN CERTIFICATE)
                if (normalizedCert.contains("BEGIN CERTIFICATE", ignoreCase = true)) {
                    // Extract base64 content from PEM format
                    // Remove PEM headers/footers and all whitespace
                    val base64Content = normalizedCert
                        .replace("-----BEGIN CERTIFICATE-----", "", ignoreCase = true)
                        .replace("-----END CERTIFICATE-----", "", ignoreCase = true)
                        .replace("\n", "")
                        .replace("\r", "")
                        .replace(" ", "")
                        .replace("\t", "")
                        .trim()
                    // PEM uses standard base64, not base64Url
                    // Use java.util.Base64 for standard base64 decoding
                    java.util.Base64.getDecoder().decode(base64Content)
                } else {
                    // Assume it's base64Url encoded (strip all whitespace first)
                    normalizedCert
                        .replace("\n", "")
                        .replace("\r", "")
                        .replace(" ", "")
                        .replace("\t", "")
                        .trim()
                        .base64UrlDecode()
                }
            } catch (e: Exception) {
                log.warn(e) { "Failed to decode certificate: ${e.message}" }
                throw IllegalArgumentException("Invalid certificate format in x5Chain: ${e.message}", e)
            }
            id.walt.cose.CoseCertificate(certBytes)
        } ?: emptyList()

        val protectedHeaders = CoseHeaders(
            algorithm = algorithm,
            x5chain = if (x5chain.isNotEmpty()) x5chain else null
        )

        // Sign the MSO
        // Create a CoseSigner that works with cloud keys (AWS, Azure, etc.)
        // Cloud keys may have hasPrivateKey = false but can still sign via signRaw()
        val coseSigner = if (issuerKey.hasPrivateKey) {
            // Standard key with private key - use the standard converter
            issuerKey.toCoseSigner()
        } else {
            // Cloud key - create custom signer that uses signRaw directly
            // Cloud keys (AWS, Azure, etc.) can sign even without hasPrivateKey = true
            CoseSigner { dataToSign ->
                val customSignatureScheme = algorithm?.let { algo ->
                    when (algo) {
                        Cose.Algorithm.PS256 -> "SHA256withRSA/PSS"
                        Cose.Algorithm.PS384 -> "SHA384withRSA/PSS"
                        Cose.Algorithm.PS512 -> "SHA512withRSA/PSS"
                        else -> null
                    }
                }
                var signature = issuerKey.signRaw(dataToSign, customSignatureScheme) as ByteArray
                if (issuerKey.keyType in KeyTypes.EC_KEYS) {
                    signature = EccUtils.convertDERtoIEEEP1363(signature)
                }
                signature
            }
        }
        
        val issuerAuth = CoseSign1.createAndSign(
            protectedHeaders = protectedHeaders,
            unprotectedHeaders = CoseHeaders(),
            payload = msoPayload,
            signer = coseSigner
        )

        // Create IssuerSigned structure
        val issuerSigned = IssuerSigned.fromIssuerSignedItems(
            namespacedItems = namespacedIssuerSignedItems,
            issuerAuth = issuerAuth
        )

        // Create Document
        val document = Document(
            docType = docType,
            issuerSigned = issuerSigned,
            deviceSigned = null // Device signing happens later during presentation
        )

        // Serialize document to CBOR
        val documentCbor = MdocCbor.encodeToByteArray(document)

        // Send callback if configured
        if (!issuanceSession.callbackUrl.isNullOrEmpty()) {
            sendCallback(
                sessionId = issuanceSession.id,
                type = "generated_mdoc",
                data = buildJsonObject { put("mdoc", documentCbor.encodeToBase64Url()) },
                callbackUrl = issuanceSession.callbackUrl
            )
        }

        return CredentialResult(
            format = CredentialFormat.mso_mdoc,
            credential = JsonPrimitive(documentCbor.encodeToBase64Url()),
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
