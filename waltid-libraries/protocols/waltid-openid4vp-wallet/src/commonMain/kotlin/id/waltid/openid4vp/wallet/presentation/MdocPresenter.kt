@file:OptIn(ExperimentalSerializationApi::class)

package id.waltid.openid4vp.wallet.presentation

import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.acceptsCoseAlgorithm
import id.walt.cose.coseCompliantCbor
import id.walt.cose.createAndSignDetached
import id.walt.cose.protectedAlgorithm
import id.walt.cose.selectCoseSignatureAlgorithm
import id.walt.cose.toCoseAlgorithm
import id.walt.cose.toCoseSigner
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.crypto.keys.Key
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto.keys.KeyType
import id.walt.crypto.utils.Base64Utils.encodeToBase64Url
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.dcql.DcqlMatcher
import id.walt.mdoc.crypto.MdocCryptoHelper
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.SessionTranscript
import id.walt.mdoc.objects.deviceretrieval.DeviceResponse
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.elements.DeviceSignedItemList
import id.walt.mdoc.objects.elements.IssuerSignedList
import id.walt.mdoc.objects.handover.OpenID4VPHandover
import id.walt.mdoc.objects.handover.OpenID4VPHandoverInfo
import id.walt.mdoc.objects.sha256
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import id.walt.crypto.utils.Base64Utils.decodeFromBase64Url
import id.walt.verifier.openid.transactiondata.DEFAULT_HASH_ALGORITHM
import id.walt.verifier.openid.transactiondata.calculateTransactionDataHashes
import id.walt.verifier.openid.transactiondata.decodeList
import id.walt.verifier.openid.transactiondata.filterTransactionDataForCredentialId
import id.walt.verifier.openid.transactiondata.normalizeHashAlgorithm
import id.walt.verifier.openid.transactiondata.TransactionDataTypeRegistry
import id.walt.verifier.openid.transactiondata.resolveHashAlgorithm
import id.waltid.openid4vp.wallet.WalletCrypto2KeyAdapter
import id.waltid.openid4vp.wallet.WalletPresentationFormatRegistry
import id.waltid.openid4vp.wallet.supportedPresentationAlgorithms
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

object MdocPresenter {

    val log = KotlinLogging.logger {}

    suspend fun buildSessionTranscript(
        authorizationRequest: AuthorizationRequest,
        responseUri: String,
    ): SessionTranscript = buildSessionTranscriptWithThumbprint(
        authorizationRequest,
        responseUri,
        ResponseEncryption.resolveCrypto2(authorizationRequest)?.thumbprintBytes(),
    )

    fun buildSessionTranscript(
        authorizationRequest: AuthorizationRequest,
        responseUri: String,
        verifierJwkThumbprint: String?,
    ): SessionTranscript = buildSessionTranscriptWithThumbprint(
        authorizationRequest,
        responseUri,
        verifierJwkThumbprint?.decodeFromBase64Url(),
    )

    @Deprecated("Use the Crypto2Key overload")
    suspend fun buildDeviceAuth(
        sessionTranscript: SessionTranscript,
        credential: MdocsCredential,
        disclosedDeviceNamespaces: DeviceNameSpaces,
        holderKey: Key,
    ): DeviceAuth = buildDeviceAuthWithKey(
        sessionTranscript,
        credential,
        disclosedDeviceNamespaces,
        holderKey,
        null,
        null,
    )

    @Deprecated("Use the Crypto2Key overload")
    suspend fun buildDeviceAuth(
        sessionTranscript: SessionTranscript,
        credential: MdocsCredential,
        disclosedDeviceNamespaces: DeviceNameSpaces,
        holderKey: Key,
        allowedAlgorithms: Set<Int>? = null,
    ): DeviceAuth = buildDeviceAuthWithKey(
        sessionTranscript,
        credential,
        disclosedDeviceNamespaces,
        holderKey,
        allowedAlgorithms,
        null,
    )

    suspend fun buildDeviceAuth(
        sessionTranscript: SessionTranscript,
        credential: MdocsCredential,
        disclosedDeviceNamespaces: DeviceNameSpaces,
        holderKey: Crypto2Key,
        allowedAlgorithms: Set<Int>? = null,
    ): DeviceAuth = buildDeviceAuthWithKey(
        sessionTranscript,
        credential,
        disclosedDeviceNamespaces,
        null,
        allowedAlgorithms,
        holderKey,
    )

    private fun buildSessionTranscriptWithThumbprint(
        authorizationRequest: AuthorizationRequest,
        responseUri: String,
        verifierJwkThumbprint: ByteArray?,
    ): SessionTranscript {
        requireNotNull(authorizationRequest.clientId) { "Missing client id in authorization request - is this a DC API flow?" }
        val handoverInfo = OpenID4VPHandoverInfo(
            clientId = authorizationRequest.clientId!!,
            nonce = authorizationRequest.nonce!!,
            jwkThumbprint = verifierJwkThumbprint,
            responseUri = responseUri
        )
        log.trace { "Client side session transaction openid4vp handover info: $handoverInfo" }

        val handoverInfoCbor = coseCompliantCbor.encodeToByteArray(handoverInfo)
        log.trace { "Client side Handover info cbor: ${handoverInfoCbor.toHexString()}" }

        val handoverInfoHash = handoverInfoCbor.sha256()
        log.trace { "Client side Handover info hash: ${handoverInfoHash.toHexString()}" }

        val handover = OpenID4VPHandover(
            identifier = "OpenID4VPHandover",
            infoHash = handoverInfoHash
        )
        val sessionTranscript = SessionTranscript.forOpenId(handover)
        log.trace { "Client side Session transcript: $sessionTranscript" }

        return sessionTranscript
    }

    @Deprecated("Use the Crypto2Key overload")
    suspend fun buildDeviceAuth(
        sessionTranscript: SessionTranscript,
        credential: MdocsCredential,
        disclosedDeviceNamespaces: DeviceNameSpaces,
        holderKey: Key,
        allowedAlgorithms: Set<Int>? = null,
        holderCrypto2Key: Crypto2Key?,
    ): DeviceAuth = buildDeviceAuthWithKey(
        sessionTranscript,
        credential,
        disclosedDeviceNamespaces,
        holderKey,
        allowedAlgorithms,
        holderCrypto2Key,
    )

    private suspend fun buildDeviceAuthWithKey(
        sessionTranscript: SessionTranscript,
        credential: MdocsCredential,
        disclosedDeviceNamespaces: DeviceNameSpaces,
        holderKey: Key?,
        allowedAlgorithms: Set<Int>?,
        holderCrypto2Key: Crypto2Key?,
    ): DeviceAuth {
        // Create the DeviceAuthentication structure to be signed
        val deviceAuthBytes = MdocCryptoHelper.buildDeviceAuthenticationBytes(
            sessionTranscript, credential.docType, ByteStringWrapper(disclosedDeviceNamespaces)
        )

        // Sign using the detached payload function
        val crypto2Key = holderCrypto2Key ?: holderKey?.let { WalletCrypto2KeyAdapter.signingKey(it) }
        val algorithm = crypto2Key?.selectCoseSignatureAlgorithm(allowedAlgorithms)
            ?: requireNotNull(requireNotNull(holderKey).keyType.toCoseAlgorithm()) {
                "Holder key type ${holderKey.keyType} does not support COSE signing"
            }
        val p256Key = crypto2Key?.spec == KeySpec.Ec(EcCurve.P256) || holderKey?.keyType == KeyType.secp256r1
        require(allowedAlgorithms == null || allowedAlgorithms.acceptsCoseAlgorithm(algorithm, p256Key)) {
            "Verifier does not support mdoc device authentication algorithm $algorithm"
        }
        val deviceSignature = if (crypto2Key != null) {
            CoseSign1.createAndSignDetached(
                protectedHeaders = CoseHeaders(algorithm = algorithm),
                detachedPayload = deviceAuthBytes,
                key = crypto2Key,
            )
        } else {
            CoseSign1.createAndSignDetached(
                protectedHeaders = CoseHeaders(algorithm = algorithm),
                detachedPayload = deviceAuthBytes,
                signer = requireNotNull(holderKey).toCoseSigner(),
            )
        }
        val deviceAuth = DeviceAuth(deviceSignature = deviceSignature)

        return deviceAuth
    }

    @Deprecated("Use the Crypto2Key overload")
    suspend fun presentMdoc(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        typeRegistry: TransactionDataTypeRegistry,
    ): JsonPrimitive = presentMdocWithKey(
        digitalCredential,
        matchResult,
        authorizationRequest,
        holderKey,
        typeRegistry,
        null,
        null,
    )

    @Deprecated("Use the Crypto2Key overload")
    suspend fun presentMdoc(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        typeRegistry: TransactionDataTypeRegistry,
        verifierJwkThumbprint: String?,
    ): JsonPrimitive = presentMdocWithKey(
        digitalCredential,
        matchResult,
        authorizationRequest,
        holderKey,
        typeRegistry,
        verifierJwkThumbprint,
        null,
    )

    suspend fun presentMdoc(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Crypto2Key,
        typeRegistry: TransactionDataTypeRegistry,
        verifierJwkThumbprint: String? = null,
    ): JsonPrimitive = presentMdocWithKey(
        digitalCredential,
        matchResult,
        authorizationRequest,
        null,
        typeRegistry,
        verifierJwkThumbprint,
        holderKey,
    )

    @Deprecated("Use the Crypto2Key overload")
    suspend fun presentMdoc(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key,
        typeRegistry: TransactionDataTypeRegistry,
        verifierJwkThumbprint: String?,
        holderCrypto2Key: Crypto2Key?,
    ): JsonPrimitive = presentMdocWithKey(
        digitalCredential,
        matchResult,
        authorizationRequest,
        holderKey,
        typeRegistry,
        verifierJwkThumbprint,
        holderCrypto2Key,
    )

    private suspend fun presentMdocWithKey(
        digitalCredential: DigitalCredential,
        matchResult: DcqlMatcher.DcqlMatchResult,
        authorizationRequest: AuthorizationRequest,
        holderKey: Key?,
        typeRegistry: TransactionDataTypeRegistry,
        verifierJwkThumbprint: String?,
        holderCrypto2Key: Crypto2Key?,
    ): JsonPrimitive {
        log.debug { "Handling mso_mdoc credential" }

        val mdocsCredential = digitalCredential as MdocsCredential
        val responseUri = when (authorizationRequest.responseMode) {
            OpenID4VPResponseMode.DIRECT_POST, OpenID4VPResponseMode.DIRECT_POST_JWT ->
                authorizationRequest.responseUri
            else -> authorizationRequest.redirectUri
        } ?: throw IllegalArgumentException("A response_uri or redirect_uri is required for mso_mdoc presentation")

        val document: Document = mdocsCredential.document
        val issuerSigned: IssuerSigned = document.issuerSigned
        val format = WalletPresentationFormatRegistry.SupportedFormat.MSO_MDOC
        val allowedIssuerAlgorithms = authorizationRequest.supportedPresentationAlgorithms(
            format,
            "issuerauth_alg_values",
        )?.mapTo(mutableSetOf(), String::toInt)
        val issuerAlgorithm = issuerSigned.issuerAuth.protectedAlgorithm()
        val issuerKey = issuerSigned.getParsedIssuerAuthCrypto2().signerKey
        require(
            allowedIssuerAlgorithms == null || allowedIssuerAlgorithms.acceptsCoseAlgorithm(
                issuerAlgorithm,
                issuerKey.spec == KeySpec.Ec(EcCurve.P256),
            )
        ) { "Verifier does not support mdoc issuer authentication algorithm $issuerAlgorithm" }
        val allowedDeviceAlgorithms = authorizationRequest.supportedPresentationAlgorithms(
            format,
            "deviceauth_alg_values",
        )?.mapTo(mutableSetOf(), String::toInt)

        // Build OpenID4VPHandover (OID4VP Appendix B.2.6.1) without ISO-specific wallet nonce
        val sessionTranscript = verifierJwkThumbprint
            ?.let { buildSessionTranscript(authorizationRequest, responseUri, it) }
            ?: buildSessionTranscript(authorizationRequest, responseUri)

        // Determine which namespaces and elements to disclose based on the DCQL match
        val disclosedDeviceNamespaces = buildTransactionDataNamespaces(
            mdocsCredential = mdocsCredential,
            transactionData = filterTransactionDataForCredentialId(
                transactionData = authorizationRequest.transactionData,
                credentialId = matchResult.originalQuery.id,
            ),
            typeRegistry = typeRegistry,
        )

        val deviceAuth = holderCrypto2Key?.let {
            buildDeviceAuth(sessionTranscript, mdocsCredential, disclosedDeviceNamespaces, it, allowedDeviceAlgorithms)
        } ?: buildDeviceAuthWithKey(
            sessionTranscript,
            mdocsCredential,
            disclosedDeviceNamespaces,
            requireNotNull(holderKey),
            allowedDeviceAlgorithms,
            null,
        )
        log.trace { "Wallet-created device auth: $deviceAuth" }

        val selectedClaimKeys = matchResult.selectedDisclosures?.keys.orEmpty()
        val dcqlQueryClaims = matchResult.originalQuery.claims
            .orEmpty()
            .filter { it.path.joinToString(".") in selectedClaimKeys }

        //--- SD START
        val selectedIssuerSignedItems = dcqlQueryClaims
            // Group by the string content of the first path element (Namespace)
            .groupBy {
                (it.path.firstOrNull()
                    ?: throw IllegalArgumentException("Empty path in ClaimsQuery")
                        ).jsonPrimitive.content
            }
            .mapValues { (sdNamespace2, claimQueries) ->
                claimQueries.map { claimsQuery ->
                    val path = claimsQuery.path
                    require(path.size == 2 && path.all { it is JsonPrimitive && it.isString }) {
                        "An mdoc claim path must contain exactly two string elements: $path"
                    }

                    // Extract the actual Strings from the JsonElements
                    val sdNamespace: String = path[0].jsonPrimitive.content
                    val sdElementIdentifier: String = path[1].jsonPrimitive.content

                    check(sdNamespace == sdNamespace2) { "Namespace mismatch: $sdNamespace != $sdNamespace2" }

                    val issuerSignedNamespaces: Map<String, IssuerSignedList>? = issuerSigned.namespaces
                    requireNotNull(issuerSignedNamespaces) { "No issuer-signed namespaces to choose from for DCQL query claims!" }

                    val selectedNamespace: IssuerSignedList = issuerSignedNamespaces[sdNamespace]
                        ?: throw IllegalArgumentException("Namespace does not exist in issuer-signed namespaces for DCQL query claim: $sdNamespace")

                    val matchedIssuerSignedItem =
                        selectedNamespace.entries.find { it.value.elementIdentifier == sdElementIdentifier }
                            ?: throw IllegalArgumentException("Could not find item for DCQL query: namespace = $sdNamespace, element = $sdElementIdentifier")

                    log.trace { "Mapped sd claim $claimsQuery to ${matchedIssuerSignedItem.value.elementIdentifier} of namespace $sdNamespace" }

                    matchedIssuerSignedItem
                }.distinctBy { it.value.elementIdentifier } // 3. Prevent duplicate disclosures if multiple deep paths hit the same element
            }
        log.trace { "Selected disclosures: ${matchResult.selectedDisclosures}" }

        val issuerSignedWithSelectedNamespaceItems =
            IssuerSigned.fromIssuerSignedLists(
                namespaces = selectedIssuerSignedItems.mapValues { IssuerSignedList(it.value) },
                issuerAuth = issuerSigned.issuerAuth
            )
        //--- SD END

        // 5. Assemble the final DeviceResponse
        val deviceResponse = DeviceResponse(
            version = "1.0",
            documents = arrayOf(
                Document(
                    docType = mdocsCredential.docType,
                    issuerSigned = issuerSignedWithSelectedNamespaceItems,
                    //issuerSigned = issuerSigned,
                    deviceSigned = DeviceSigned(ByteStringWrapper(disclosedDeviceNamespaces), deviceAuth)
                )
            ),
            status = 0u
        )

        // 6. CBOR-encode and base64url-encode the response string
        val deviceResponseBytes = coseCompliantCbor.encodeToByteArray(deviceResponse)
        return JsonPrimitive(deviceResponseBytes.encodeToBase64Url())
    }

    private fun buildTransactionDataNamespaces(
        mdocsCredential: MdocsCredential,
        transactionData: List<String>,
        typeRegistry: TransactionDataTypeRegistry,
    ): DeviceNameSpaces {
        if (transactionData.isEmpty()) return DeviceNameSpaces(emptyMap())

        val decoded = decodeList(transactionData)
        val hashAlgorithm = resolveHashAlgorithm(decoded) ?: DEFAULT_HASH_ALGORITHM

        val namespaces = buildMap {
            decoded.forEach {
                val type = it.transactionData.type
                typeRegistry.requireKnown(type)
                require(type !in this) { "mdoc transaction_data supports one item per response namespace: $type" }
                put(type, buildDeviceSignedItems(it.encoded, it.transactionData.transactionDataHashesAlg, hashAlgorithm))
            }
        }

        requireTransactionDataAuthorization(mdocsCredential, namespaces)
        return DeviceNameSpaces(namespaces)
    }

    private fun buildDeviceSignedItems(
        encoded: String,
        transactionDataHashesAlg: List<String>?,
        hashAlgorithm: String,
    ): DeviceSignedItemList {
        val hash = calculateTransactionDataHashes(listOf(encoded), hashAlgorithm).single()
        val items = buildList {
            add(DeviceSignedItem("transaction_data_hash", hash.decodeFromBase64Url()))
            if (!transactionDataHashesAlg.isNullOrEmpty()) {
                add(DeviceSignedItem("transaction_data_hash_alg", normalizeHashAlgorithm(hashAlgorithm)))
            }
        }
        return DeviceSignedItemList(items)
    }

    private fun requireTransactionDataAuthorization(
        mdocsCredential: MdocsCredential,
        namespaces: Map<String, DeviceSignedItemList>,
    ) {
        val keyAuthorizations = requireNotNull(mdocsCredential.documentMso.deviceKeyInfo.keyAuthorizations) {
            "transaction_data requires mdoc keyAuthorizations for docType ${mdocsCredential.docType}"
        }
        namespaces.forEach { (namespace, items) ->
            val elementKeys = items.entries.map { it.key }.toSet()
            val isNamespaceAuthorized = keyAuthorizations.namespaces?.contains(namespace) == true
            val authorizedElements = keyAuthorizations.dataElements?.get(namespace).orEmpty().toSet()
            require(isNamespaceAuthorized || authorizedElements.containsAll(elementKeys)) {
                "transaction_data type '$namespace' is not authorized for mdoc docType ${mdocsCredential.docType}"
            }
        }
    }
}
