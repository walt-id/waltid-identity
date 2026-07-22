package id.walt.verifier2.handlers.sessioncreation

import id.walt.cose.*
import id.walt.cose.toCoseKey
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto.utils.JsonUtils.toJsonElement
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.JwsAlgorithm
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.EncodedKey
import id.walt.crypto2.keys.KeyId
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.SoftwareKey
import id.walt.crypto2.providers.GenerateSoftwareKeyRequest
import id.walt.crypto2.providers.cryptography.CryptographySoftwareKeyProvider
import id.walt.crypto2.serialization.StoredKeyCodec
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.dcql.models.CredentialFormat
import id.walt.iso18013.annexc.AnnexC
import id.walt.iso18013.annexc.AnnexCTranscriptBuilder
import id.walt.iso18013.annexc.protocol.AnnexCRequestResponse
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.dcapi.DCAPIEncryptionInfo
import id.walt.mdoc.objects.deviceretrieval.DeviceRequest
import id.walt.mdoc.objects.deviceretrieval.DeviceRequestInfo
import id.walt.mdoc.objects.deviceretrieval.UseCase
import id.walt.policies2.vc.VCPolicyList
import id.walt.policies2.vc.policies.CredentialSignaturePolicy
import id.walt.policies2.vp.policies.TransactionDataHashCheckSdJwtVPPolicy
import id.walt.policies2.vp.policies.TransactionDataHashesVPPolicy
import id.walt.policies2.vp.policies.TransactionDataMdocVpPolicy
import id.walt.policies2.vp.policies.VPPolicy2
import id.walt.policies2.vp.policies.VPPolicyList
import id.walt.policies2.vp.policies.VPVerificationPolicyManager
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.authorization.ClientMetadata
import id.walt.verifier.openid.models.authorization.RequestUriHttpMethod
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.verifier.openid.models.openid.OpenID4VPResponseType
import id.walt.verifier.openid.transactiondata.validateRequestTransactionDataStructure
import id.walt.verifier2.data.*
import id.walt.verifier2.handlers.sessioncreation.annexc.ReaderAuthentication
import id.walt.verifier2.handlers.sessioncreation.annexc.ReaderAuthenticationAll
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalSerializationApi::class)
object VerificationSessionCreator {

    private val log = KotlinLogging.logger { }
    private val crypto2Runtime = CryptoRuntime(listOf(CryptographySoftwareKeyProvider()))

    private fun defaultVpPolicies() = VPPolicyList(
        jwtVcJson = VPVerificationPolicyManager.defaultJwtVcJsonPolicies,
        dcSdJwt = VPVerificationPolicyManager.defaultDcSdJwtPolicies,
        msoMdoc = VPVerificationPolicyManager.defaultMsoMdocPolicies
    )

    @Suppress("DEPRECATION")
    private fun VPPolicyList.withMandatoryTransactionDataPolicies(formats: Set<CredentialFormat>): VPPolicyList = copy(
        dcSdJwt = dcSdJwt.withPolicyIfMissing(
            shouldInclude = CredentialFormat.DC_SD_JWT in formats,
            policy = TransactionDataHashCheckSdJwtVPPolicy(),
            equivalentPolicyIds = setOf(TransactionDataHashesVPPolicy.ID),
        ),
        msoMdoc = msoMdoc.withPolicyIfMissing(
            shouldInclude = CredentialFormat.MSO_MDOC in formats,
            policy = TransactionDataMdocVpPolicy(),
        ),
    )

    private fun <Policy : VPPolicy2> List<Policy>.withPolicyIfMissing(
        shouldInclude: Boolean,
        policy: Policy,
        equivalentPolicyIds: Set<String> = emptySet(),
    ): List<Policy> =
        if (!shouldInclude || any { it.id == policy.id || it.id in equivalentPolicyIds }) this else this + policy

    private suspend fun getKid(clientId: String?, key: VerifierSigningKey): String {
        val keyId = when (key) {
            is VerifierSigningKey.Legacy -> key.key.getKeyId()
            is VerifierSigningKey.Crypto2 -> key.key.id.value
        }
        val prefix = "decentralized_identifier:"
        return clientId
            ?.takeIf { it.startsWith(prefix) && it.substringAfter(prefix).isNotBlank() }
            ?.let { "${it.substringAfter(prefix)}#$keyId" }
            ?: keyId
    }

    @Deprecated("Use the crypto2 Key overload with explicit JWS and COSE algorithms for signed sessions")
    suspend fun createVerificationSession(
        setup: VerificationSessionSetup,
        clientId: String?,
        clientMetadata: ClientMetadata? = null,
        urlPrefix: String?,
        urlHost: String,
        key: Key? = null,
        x5c: List<String>? = null,
    ): Verification2Session = createVerificationSessionInternal(
        setup = setup,
        clientId = clientId,
        clientMetadata = clientMetadata,
        urlPrefix = urlPrefix,
        urlHost = urlHost,
        key = key,
        x5c = x5c,
        crypto2Key = null,
        crypto2JwsAlgorithm = null,
        crypto2CoseAlgorithm = null,
        signingKeyReference = null,
    )

    suspend fun createVerificationSession(
        setup: VerificationSessionSetup,
        clientId: String?,
        clientMetadata: ClientMetadata? = null,
        urlPrefix: String?,
        urlHost: String,
        key: Crypto2Key,
        x5c: List<String>? = null,
        jwsAlgorithm: JwsAlgorithm,
        coseAlgorithm: Int,
        signingKeyReference: String? = null,
    ): Verification2Session = createVerificationSessionInternal(
        setup = setup,
        clientId = clientId,
        clientMetadata = clientMetadata,
        urlPrefix = urlPrefix,
        urlHost = urlHost,
        key = null,
        x5c = x5c,
        crypto2Key = key,
        crypto2JwsAlgorithm = jwsAlgorithm,
        crypto2CoseAlgorithm = coseAlgorithm,
        signingKeyReference = signingKeyReference,
    )

    private suspend fun createVerificationSessionInternal(
        setup: VerificationSessionSetup,

        clientId: String?,
        clientMetadata: ClientMetadata?,

        /** Is used to build request URL and response URL */
        urlPrefix: String?,

        /**
         * Is used to build bootstrap- & authorizationRequestUrl
         * for DC API: origin
         * */
        urlHost: String,

        // Both are required for signed requests:
        key: Key?,
        x5c: List<String>?,
        crypto2Key: Crypto2Key?,
        crypto2JwsAlgorithm: JwsAlgorithm?,
        crypto2CoseAlgorithm: Int?,
        signingKeyReference: String?,
    ): Verification2Session {
        require(key == null || crypto2Key == null) { "Provide either a v1 or crypto2 verifier signing key" }
        val signingKey = crypto2Key?.let {
            VerifierSigningKey.Crypto2(
                key = it,
                jwsAlgorithm = requireNotNull(crypto2JwsAlgorithm) { "crypto2JwsAlgorithm is required" },
                coseAlgorithm = requireNotNull(crypto2CoseAlgorithm) { "crypto2CoseAlgorithm is required" },
            )
        } ?: key?.let(VerifierSigningKey::Legacy)
        val sessionId = setup.core.sessionId ?: Uuid.random().toString()

        val isAnnexC = setup is DcApiAnnexCFlowSetup
        val isSignedRequest = setup.core.signedRequest
        val isEncryptedResponse = setup.core.encryptedResponse || isAnnexC
        val isCrossDevice = setup is CrossDeviceFlowSetup
        val isDcApi = setup is DcApiAnnexDFlowSetup || isAnnexC
        val isDcApiHaip = isDcApi && (setup is DcApiAnnexDFlowSetup && setup.haip)
        val openIdConfig = (setup as? OpenID4VP1FlowSetup)?.openid
        val responseType = openIdConfig?.responseType ?: OpenID4VPResponseType.VP_TOKEN
        val isSiop = responseType == OpenID4VPResponseType.VP_TOKEN_ID_TOKEN
        require(!isSiop || isCrossDevice) { "SIOPv2 combined responses require an OpenID4VP cross-device flow" }
        val origins =
            if (setup is DcApiAnnexDFlowSetup) setup.expectedOrigins else if (setup is DcApiAnnexCFlowSetup) listOf(setup.origin) else null

        var ephemeralKey: JWKKey? = null
        var crypto2EphemeralKey: SoftwareKey? = null
        var crypto2EphemeralPublicJwk: EncodedKey.Jwk? = null

        if (isDcApi) {
            require(urlPrefix == null) { "URL prefix is not used for DC API" }
            require(!urlHost.startsWith("openid4vp://authorize")) { "URL Host has to be set to the DC API origin" }
            if (isSignedRequest && !isAnnexC) {
                require(!clientId.isNullOrBlank()) { "Signed DC API requests require non-empty client_id" }
            }
        }

        // Preserve OpenID4VP 1.0 algorithms while also advertising fully specified identifiers.
        val supportedJwsAlgorithms = JsonArray(
            JwsAlgorithm.entries
                .filterNot { it == JwsAlgorithm.ED448 }
                .map { JsonPrimitive(it.identifier) }
        )
        val defaultVpFormatsSupported = mapOf(
            "jwt_vc_json" to buildJsonObject {
                put("alg_values", supportedJwsAlgorithms)
            },
            "dc+sd-jwt" to buildJsonObject {
                put("sd-jwt_alg_values", supportedJwsAlgorithms)
                put("kb-jwt_alg_values", supportedJwsAlgorithms)
            },
            "mso_mdoc" to buildJsonObject {
                put("issuerauth_alg_values", JsonArray(listOf(Cose.Algorithm.ESP256.toJsonElement())))
                put("deviceauth_alg_values", JsonArray(listOf(Cose.Algorithm.ESP256.toJsonElement())))
            },
        )

        val effectiveClientMetadata = (if (isEncryptedResponse) {
            val keyType = KeyType.secp256r1

            if (isDcApiHaip) {
                // HAIP mandates P-256 (secp256r1)
                require(keyType == KeyType.secp256r1) { "HAIP profile requires P-256 keys" }
            }

            crypto2EphemeralKey = crypto2Runtime.generateSoftwareKey(
                GenerateSoftwareKeyRequest(
                    id = KeyId("response-encryption-$sessionId"),
                    spec = KeySpec.Ec(EcCurve.P256),
                    usages = setOf(KeyUsage.KEY_AGREEMENT),
                )
            )
            crypto2EphemeralPublicJwk = crypto2EphemeralKey.capabilities.publicKeyExporter
                ?.exportPublicKey() as? EncodedKey.Jwk
                ?: error("Ephemeral response key does not export a public JWK")
            // Temporary dual-write for rolling compatibility with replicas that only understand v1 sessions.
            val privateJwk = crypto2EphemeralKey.capabilities.privateKeyExporter
                ?.exportPrivateKey() as? EncodedKey.Jwk
                ?: error("Ephemeral response key does not export its private JWK")
            val legacyJwk = JsonObject(
                Jwk.parse(privateJwk) + mapOf(
                    "kid" to JsonPrimitive(crypto2EphemeralKey.id.value),
                    "alg" to JsonPrimitive("ECDH-ES"),
                    "use" to JsonPrimitive("enc"),
                )
            )
            ephemeralKey = JWKKey.importJWK(legacyJwk.toString()).getOrThrow()

            // Construct JWKS
            val publicJwk = Jwk.parse(crypto2EphemeralPublicJwk)
            val encryptionKeyId = crypto2EphemeralKey.id.value
            val jwks = ClientMetadata.Jwks(
                listOf(
                    JsonObject(
                        publicJwk
                            .toMutableMap().apply {
                                set("alg", JsonPrimitive("ECDH-ES"))
                                set("use", JsonPrimitive("enc"))
                                set("kid", JsonPrimitive(encryptionKeyId))
                            }
                    )
                )
            )
            // TODO: check if jwks contains `alg` by default (should be "alg": "ECDH-ES")

            // Merge into clientMetadata
            val baseMetadata = clientMetadata ?: ClientMetadata()
            baseMetadata.copy(
                jwks = jwks,
                vpFormatsSupported = baseMetadata.vpFormatsSupported ?: defaultVpFormatsSupported,
                encryptedResponseEncValuesSupported = listOf("A128GCM", "A256GCM")
            )
        } else {
            val baseMetadata = clientMetadata ?: ClientMetadata()
            baseMetadata.copy(
                vpFormatsSupported = baseMetadata.vpFormatsSupported ?: defaultVpFormatsSupported,
            )
        }).let { metadata ->
            if (isSiop) {
                metadata.copy(
                    subjectSyntaxTypesSupported = metadata.subjectSyntaxTypesSupported
                        ?: listOf("did", "urn:ietf:params:oauth:jwk-thumbprint"),
                    idTokenSignedResponseAlg = metadata.idTokenSignedResponseAlg ?: "RS256",
                )
            } else metadata
        }


        // TODO: Annex C is actually a kind of DC API...
        require(isCrossDevice || isDcApi || isAnnexC) { "No flow is selected" } // list all flows here
        val nonce = Uuid.random().toString()
        val state = if (!isDcApi) Uuid.random().toString() else null

//        ClientMetadata(
//            clientName = "Badge Verifier",
//            logoUri = "https://xyz.example/logo.png",
//            vpFormatsSupported = mapOf(
//                "jwt_vc_json" to JsonObject(
//                    mapOf("alg_values" to JsonArray(listOf("RSA", "ES256", "ES256K", "EdDSA").map { JsonPrimitive(it) }))
//                )
//            )
//        )


        // TODO: Build AuthorizationRequest based on preset

        val bootstrapAuthorizationRequest = if (isDcApi) null
        else AuthorizationRequest(
            // TODO: url building (handle host alias)
            requestUri = "$urlPrefix/$sessionId/request",

            // Per OID4VP 1.0 §5.1: when the verifier uses signed requests, advertise
            // request_uri_method=post so wallets can send wallet_nonce to prevent replay.
            requestUriMethod = if (isSignedRequest) RequestUriHttpMethod.POST else null,

            clientId = clientId,

            nonce = null, // not required in the initial request yet
            responseType = null
        )
        val transactionDataJsonObjects = openIdConfig?.transactionData
        val transactionData = transactionDataJsonObjects?.map { jsonObj ->
            jsonObj.toString().encodeToByteArray().encodeToBase64Url()
        }
        val credentialQueriesById = transactionData?.let {
            requireNotNull(setup.core.dcqlQuery) { "transaction_data requires a dcql_query" }
                .credentials
                .associateBy { credentialQuery -> credentialQuery.id }
        }
        val decodedTransactionData = validateRequestTransactionDataStructure(
            transactionData = transactionData,
            credentialQueriesById = credentialQueriesById,
        )
        val transactionDataFormats = decodedTransactionData
            .flatMap { decodedItem -> decodedItem.transactionData.credentialIds }
            .mapNotNull { credentialId -> credentialQueriesById?.get(credentialId)?.format }
            .toSet()

        val effectiveClientId = if ((isDcApi && !isSignedRequest) || isAnnexC) null else clientId

        val authorizationRequest = AuthorizationRequest(
            responseType = if (!isAnnexC) responseType else null,

            // For Unsigned DC API, client_id MUST be omitted.
            // For Signed DC API, it MUST be present.
            clientId = effectiveClientId,
            issuer = effectiveClientId.takeIf { isSignedRequest },
            redirectUri = null, // For Same-Device flow (fragment/query/after code exchange etc)
            // TODO: url building (handle host alias)
            responseUri = when {
                isDcApi || isAnnexC -> null
                isCrossDevice -> "$urlPrefix/$sessionId/response" // For Cross-Device flow (direct_post, direct_post.jwt)
                else -> throw IllegalStateException("No flow is selected")
            },
            scope = openIdConfig?.scope,//OPTIONAL. OAuth 2.0 Scope value. Can be used for pre-defined DCQL queries or OpenID Connect scopes (e.g., "openid").
            state = state, // Opaque value used by the Verifier to maintain state between the request and callback.
            nonce = nonce, // String value used to mitigate replay attacks. Also used to establish holder binding.
            responseMode = when {
                isDcApi && isEncryptedResponse -> OpenID4VPResponseMode.DC_API_JWT // HAIP requires dc_api.jwt (encrypted)
                isDcApi -> OpenID4VPResponseMode.DC_API
                isCrossDevice && isEncryptedResponse -> OpenID4VPResponseMode.DIRECT_POST_JWT
                isCrossDevice -> OpenID4VPResponseMode.DIRECT_POST

                isAnnexC -> null // not OpenID4VP

                else -> throw IllegalStateException("No flow is selected")
            },
            // JAR (RFC 9101) Parameters (Section 5)
            /*
             * OPTIONAL. The Authorization Request parameters are represented as a JWT [RFC7519].
             * If present, this JWT contains all other Authorization Request parameters as claims.
             */
            request = null, // This would be the compact JWT string

            // OpenID4VP New Parameters (Section 5.1)
            dcqlQuery = setup.core.dcqlQuery, // REQUIRED (unless 'scope' parameter represents a DCQL Query).
            clientMetadata = effectiveClientMetadata,


            /*
             * OPTIONAL. Array of strings, where each string is a base64url encoded JSON object
             * containing details about the transaction the Verifier is requesting the End-User to authorize.
             * The decoded JSON object structure is represented by [TransactionDataItem].
             */
            transactionData = transactionData, // List of base64url encoded JSON strings

            /*
             * OPTIONAL. An array of attestations about the Verifier relevant to the Credential Request.
             * Each object structure is represented by [VerifierInfoItem].
             */
            verifierInfo = setup.core.verifierInfo,

            // SIOPv2 specific parameters (if scope includes "openid") - common but technically from SIOPv2
            /*
             * OPTIONAL (but common with SIOPv2). Specifies the type of ID Token the RP wants.
             * E.g., "subject_signed", "attester_signed".
             */
            idTokenType = openIdConfig?.idTokenType,

            // DC API specific parameter (Appendix A.2 of draft 28)
            /*
             * REQUIRED when signed requests (Appendix A.3.2) are used with the Digital Credentials API (DC API).
             * An array of strings, each string representing an Origin of the Verifier that is making the request.
             * Not for use in unsigned requests.
             */
            expectedOrigins = if (isDcApi) origins else null,
        )
        log.trace { "Constructed AuthorizationRequest: $authorizationRequest" }

        val authorizationRequestUrl = authorizationRequest.toHttpUrl(URLBuilder(urlHost))
        val bootstrapAuthorizationRequestUrl = bootstrapAuthorizationRequest?.toHttpUrl(URLBuilder(urlHost))

        val now = Clock.System.now()
        val expiration = setup.core.expirationDate
        val retentionDate = now.plus(10, DateTimeUnit.YEAR, TimeZone.UTC)

        val signedAuthorizationRequest = if (isSignedRequest) {
            val requestSigningKey = requireNotNull(signingKey)

            val headers = hashMapOf<String, JsonElement>(
                "typ" to JsonPrimitive("oauth-authz-req+jwt"),
                "kid" to JsonPrimitive(getKid(clientId, requestSigningKey))
            )
            if (x5c != null) headers["x5c"] = JsonArray(x5c.map { JsonPrimitive(it) })

            // OID4VP 1.0 Final §5.8: when static discovery metadata is used (no dynamic discovery),
            // aud MUST be "https://self-issued.me/v2"
            val payloadWithAud = Json.encodeToJsonElement(authorizationRequest).jsonObject
                .toMutableMap()
                .apply {
                    put("aud", JsonPrimitive("https://self-issued.me/v2"))
                    put("iat", JsonPrimitive(now.epochSeconds))
                    expiration?.let { put("exp", JsonPrimitive(it.epochSeconds)) }
                }
                .let { JsonObject(it) }

            requestSigningKey.signJws(Json.encodeToString(payloadWithAud).encodeToByteArray(), headers)
        } else null

        val effectiveVpPolicies = (setup.core.policies.vp_policies ?: defaultVpPolicies())
            .withMandatoryTransactionDataPolicies(transactionDataFormats)
        val effectivePolicies = Verification2Session.DefinedVerificationPolicies(
            vp_policies = effectiveVpPolicies,
            vc_policies = setup.core.policies.vc_policies ?: VCPolicyList(
                policies = listOf(CredentialSignaturePolicy())
            ),
            specific_vc_policies = setup.core.policies.specific_vc_policies
        )

        val customData = when {
            isAnnexC -> {

                val encryptionInfoObj = DCAPIEncryptionInfo(
                    nonce = nonce.toByteArray(),
                    recipientPublicKey = requireNotNull(crypto2EphemeralPublicJwk) {
                        "Missing ephemeral public key for Annex C verification"
                    }.toCoseKey()
                )
                val encryptionInfoB64 = encryptionInfoObj.encodeToBase64Url()


                val deviceRequest = if (isSignedRequest) {
                    // --- Reader authentication

                    val annexSigningKey = requireNotNull(signingKey) {
                        "Signing key is required for signed Annex C requests"
                    }
                    require(!x5c.isNullOrEmpty()) { "x5c is required for signed Annex C requests" }

                    // Build the DC API Session Transcript
                    val sessionTranscript = AnnexCTranscriptBuilder.buildSessionTranscript(
                        encryptionInfoB64 = encryptionInfoB64,
                        origin = setup.origin
                    )

                    // Prepare the base request without signatures
                    val initialDeviceRequest = DeviceRequest(setup.requestedElements)

                    // Create the DeviceRequestInfo (Use Cases)
                    // By grouping all indices into a single documentSet, we make ALL requested documents mandatory.
                    val deviceRequestInfo = ByteStringWrapper(
                        DeviceRequestInfo(
                            useCases = listOf(
                                UseCase(
                                    mandatory = true,
                                    documentSets = listOf(initialDeviceRequest.docRequests.indices.map { it.toUInt() })
                                )
                            )
                        )
                    )

                    // cryptography setup for both signature types
                    val coseSigner = annexSigningKey.toCoseSigner()
                    val x5cByteArrays = x5c.map { Base64.decode(it) }
                    val protectedHeaders = CoseHeaders(algorithm = annexSigningKey.coseAlgorithm)
                    val unprotectedHeaders = CoseHeaders(x5chain = x5cByteArrays.map { CoseCertificate(it) })

                    // Generate readerAuth for EACH document requested (Per-Document Signature)
                    val signedDocRequests = initialDeviceRequest.docRequests.map { docReq ->
                        val itemsRequestBytes = docReq.itemsRequest.serialized

                        val readerAuthPayload = ReaderAuthentication(
                            context = ReaderAuthentication.CONTEXT,
                            sessionTranscript = sessionTranscript,
                            itemsRequestBytes = itemsRequestBytes
                        )

                        val readerAuthSignature = CoseSign1.createAndSignDetached(
                            protectedHeaders = protectedHeaders,
                            unprotectedHeaders = unprotectedHeaders,
                            detachedPayload = coseCompliantCbor.encodeToByteArray(readerAuthPayload),
                            signer = coseSigner
                        )

                        // Attach the signature to this specific document request
                        docReq.copy(readerAuth = readerAuthSignature)
                    }

                    // Generate readerAuthAll for the entire set (Global Signature)
                    val itemsRequestBytesAll = initialDeviceRequest.docRequests.map { it.itemsRequest.serialized }

                    val readerAuthAllPayload = ReaderAuthenticationAll(
                        context = ReaderAuthenticationAll.CONTEXT,
                        sessionTranscript = sessionTranscript,
                        itemsRequestBytesAll = itemsRequestBytesAll,
                        docRequestsInfoBytes = deviceRequestInfo.serialized
                    )

                    val readerAuthAllSignature = CoseSign1.createAndSignDetached(
                        protectedHeaders = protectedHeaders,
                        unprotectedHeaders = unprotectedHeaders,
                        detachedPayload = coseCompliantCbor.encodeToByteArray(readerAuthAllPayload),
                        signer = coseSigner
                    )

                    // Assemble final request
                    DeviceRequest(
                        version = DeviceRequest.VERSION_WITH_SIGNING,
                        docRequests = signedDocRequests,
                        deviceRequestInfo = deviceRequestInfo,
                        readerAuthAll = listOf(readerAuthAllSignature)
                    )
                } else {
                    DeviceRequest(setup.requestedElements).copy(version = DeviceRequest.VERSION)
                }

                AnnexCRequestResponse(
                    protocol = AnnexC.PROTOCOL,
                    data = AnnexCRequestResponse.Data(
                        deviceRequest = deviceRequest.encodeToBase64Url(),
                        encryptionInfo = encryptionInfoB64
                    )
                )
            }

            else -> null
        }

        @Suppress("SENSELESS_COMPARISON") // TODO
        val newSession = Verification2Session(
            id = sessionId,
            setup = setup,
            data = customData?.let { Json.encodeToJsonElement(it) },

            creationDate = now,
            expirationDate = expiration,
            retentionDate = retentionDate,

            //status = if (expiration != null) Verification2Session.VerificationSessionStatus.UNUSED else Verification2Session.VerificationSessionStatus.ACTIVE,
            status = Verification2Session.VerificationSessionStatus.UNUSED,

            bootstrapAuthorizationRequest = if (!isAnnexC) bootstrapAuthorizationRequest else null,
            bootstrapAuthorizationRequestUrl = if (!isAnnexC) bootstrapAuthorizationRequestUrl else null,

            authorizationRequest = authorizationRequest,
            authorizationRequestUrl = if (!isAnnexC) authorizationRequestUrl else null,
            signedAuthorizationRequestJwt = signedAuthorizationRequest,
            requestSigningKeyReference = signingKeyReference,
            ephemeralDecryptionKey = ephemeralKey?.let { DirectSerializedKey(it) },
            crypto2EphemeralDecryptionKey = crypto2EphemeralKey?.storedKey?.let(StoredKeyCodec::encodeToString),
            jwkThumbprint = crypto2EphemeralPublicJwk?.let { Jwk.sha256Thumbprint(it) }
                ?: ephemeralKey?.getPublicKey()?.getThumbprint(),

            requestMode = if (isSignedRequest) Verification2Session.RequestMode.REQUEST_URI_SIGNED else Verification2Session.RequestMode.REQUEST_URI,

            policies = effectivePolicies,
            notifications = setup.core.notifications,
            redirects = when (setup) {
                is SameDeviceFlowSetup -> setup.redirects
                is CrossDeviceFlowSetup -> setup.redirects
                else -> null
            }
        )
        log.trace { "Created verification session ${newSession.id} in mode ${newSession.requestMode}" }

        return newSession
    }

    private suspend fun VerifierSigningKey.signJws(
        payload: ByteArray,
        headers: Map<String, JsonElement>,
    ): String = when (this) {
        is VerifierSigningKey.Legacy -> key.signJws(payload, headers)
        is VerifierSigningKey.Crypto2 -> CompactJws.sign(payload, key, jwsAlgorithm, JsonObject(headers))
    }

    private fun VerifierSigningKey.toCoseSigner(): CoseSigner = when (this) {
        is VerifierSigningKey.Legacy -> key.toCoseSigner()
        is VerifierSigningKey.Crypto2 -> key.toCoseSigner(coseAlgorithm)
    }

    private val VerifierSigningKey.coseAlgorithm: Int
        get() = when (this) {
            is VerifierSigningKey.Legacy -> requireNotNull(key.keyType.toCoseAlgorithm()) {
                "Verifier signing key type has no COSE algorithm: ${key.keyType}"
            }
            is VerifierSigningKey.Crypto2 -> coseAlgorithm
        }

    private sealed interface VerifierSigningKey {
        data class Legacy(val key: Key) : VerifierSigningKey
        data class Crypto2(
            val key: Crypto2Key,
            val jwsAlgorithm: JwsAlgorithm,
            val coseAlgorithm: Int,
        ) : VerifierSigningKey
    }

}
