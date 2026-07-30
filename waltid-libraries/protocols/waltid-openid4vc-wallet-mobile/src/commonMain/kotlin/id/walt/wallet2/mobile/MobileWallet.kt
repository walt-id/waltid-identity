@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.crypto2.keys.Key
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.handlers.PresentCredentialRequest
import id.walt.wallet2.handlers.PresentationCredentialOption
import id.walt.wallet2.handlers.PresentationCredentialRequirement
import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.handlers.PresentationDisclosureSelection
import id.walt.wallet2.handlers.PresentationPreviewHandle
import id.walt.wallet2.handlers.PreviewPresentationRequest
import id.walt.wallet2.handlers.PreviewPresentationResult
import id.walt.wallet2.handlers.RejectPresentationRequest
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import id.walt.wallet2.handlers.ReceiveCredentialFromPreviewRequest
import id.walt.wallet2.handlers.ResolveOfferRequest
import id.walt.wallet2.handlers.SubmitPresentationRequest
import id.walt.wallet2.handlers.WalletIssuanceHandler
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.HttpWalletAttestationProvider
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import id.walt.crypto.utils.JwsUtils.decodeJws
import id.walt.openid4vci.tokens.jwt.JwtHeaderParams
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import io.ktor.http.Url
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

private object MobileDidSupport {
    private val initializationMutex = Mutex()
    private var initialized = false

    suspend fun ensureInitialized() = initializationMutex.withLock {
        if (!initialized) {
            DidService.minimalInit()
            initialized = true
        }
    }
}

/**
 * Result returned after a mobile wallet has been initialized with signing material and a DID.
 *
 * @property keyId Identifier of the persisted signing key used by the wallet.
 * @property did Decentralized identifier registered for the persisted key.
 */
public data class MobileWalletBootstrapResult(
    public val keyId: String,
    public val did: String,
)

/**
 * Credential entry suitable for mobile UI lists and detail display.
 *
 * The credential content is exposed as a JSON string so Kotlin, Swift, and other
 * consumers can decode it with native platform tools without depending on Kotlinx
 * JSON value types in the public mobile API.
 *
 * @property id Wallet-local credential identifier.
 * @property format Credential format, such as `jwt_vc_json`, `vc+sd-jwt`, or `mso_mdoc`.
 * @property issuer Issuer identifier extracted from the credential when available.
 * @property subject Subject identifier extracted from the credential when available.
 * @property label Optional display label stored with the credential.
 * @property addedAt ISO-8601 timestamp string for when the credential was added, when known.
 * @property credentialDataJson Parsed credential data encoded as JSON for app-side display.
 */
public data class MobileWalletCredential(
    public val id: String,
    public val format: String,
    public val issuer: String?,
    public val subject: String?,
    public val label: String?,
    public val addedAt: String?,
    public val credentialDataJson: String,
)

/**
 * Opaque issuance preview handle. It is valid only for the wallet that created it.
 *
 * @property value Opaque identifier returned by [MobileWallet.resolveOffer].
 */
public data class MobileWalletIssuancePreviewHandle(public val value: String) {
    init {
        require(value.isNotBlank()) { "Issuance preview handle must not be blank" }
    }

    /** Returns a redacted representation that does not reveal [value]. */
    public override fun toString(): String = "MobileWalletIssuancePreviewHandle(<redacted>)"
}

/**
 * Result of answering an OpenID4VP presentation request.
 *
 * Each subtype represents the next action required from the host app. This keeps
 * mutually exclusive response artifacts out of the same result instance.
 */
public sealed interface MobileWalletPresentationResult {
    /** The protocol response still requires a host-app delivery action. */
    public sealed interface Prepared : MobileWalletPresentationResult {
        /** The host app must open [url] to deliver the protocol response. */
        public data class OpenUrl(
            /** URL the host app must open to deliver the protocol response. */
            public val url: String,
        ) : Prepared

        /** The host app must render [html] so its self-submitting form can deliver the protocol response. */
        public data class SubmitForm(
            /** HTML document the host app must render to deliver the protocol response. */
            public val html: String,
        ) : Prepared
    }

    /** The protocol response was transmitted and the verifier returned a JSON response. */
    public sealed interface Transmitted : MobileWalletPresentationResult {
        /** Raw verifier response body encoded as JSON. */
        public val verifierResponseJson: String

        /** The verifier accepted the protocol response. */
        public data class Succeeded(
            override val verifierResponseJson: String,
            /** Optional post-response redirect for the host app to open. */
            public val redirectUrl: String? = null,
        ) : Transmitted

        /** The verifier rejected or could not process the protocol response. */
        public data class Failed(
            override val verifierResponseJson: String,
        ) : Transmitted
    }
}

/**
 * OAuth 2.0 client-attestation configuration used during mobile issuance.
 *
 * The wallet uses this configuration to request a client attestation JWT from the
 * enterprise client-attester service and attach the resulting proof to token requests.
 *
 * @property baseUrl Base URL of the deployment that hosts the attester service.
 * @property attesterPath Path to the attester endpoint, relative to [baseUrl].
 * @property bearerToken Optional bearer token for protected attester endpoints.
 * @property hostHeader Optional `Host` header override for tunneled local tests.
 */
public data class WalletAttestationConfig(
    public val baseUrl: String,
    public val attesterPath: String,
    public val bearerToken: String = "",
    public val hostHeader: String = "",
)

/**
 * Android and iOS facade for the walt.id wallet SDK.
 *
 * Use [MobileWalletFactory] to create instances with the correct platform storage, key provider,
 * and SQLDelight driver. The facade keeps the public mobile API intentionally small while delegating
 * protocol work to the core wallet handlers.
 */
public class MobileWallet internal constructor(
    walletId: String,
    private val keyStore: WalletKeyStore,
    private val didStore: WalletDidStore,
    private val credentialStore: WalletCredentialStore,
    private val generateAndPersistKey: suspend (MobileWalletKeyType) -> Key,
    private val didService: Crypto2DidService = Crypto2DidService,
    private val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    attestationConfig: WalletAttestationConfig? = null,
    private val preferredLocales: List<String> = emptyList(),
    private val transactionDataProfiles: List<MobileWalletTransactionDataProfile> = emptyList(),
    private val clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
    private val credentialIssuerMetadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    private val onEvent: suspend (MobileWalletEvent) -> Unit = {},
    private val deleteLocalPersistence: suspend () -> Unit = {},
) {
    private val eventStream = MobileWalletEventStream()

    /**
     * Buffered stream of recent issuance and presentation events emitted by this wallet.
     */
    public val events: Flow<MobileWalletEvent> = eventStream.events

    private val attestationAssembler: ClientAttestationAssembler? = attestationConfig?.let { config ->
        ClientAttestationAssembler(
            HttpWalletAttestationProvider(
                baseUrl = config.baseUrl,
                attesterPath = config.attesterPath,
                bearerToken = config.bearerToken,
                hostHeader = config.hostHeader,
            )
        )
    }

    private val wallet = Wallet(
        id = walletId,
        keyStores = listOf(keyStore),
        didStore = didStore,
        credentialStores = listOf(credentialStore),
    )

    /**
     * Initializes the wallet by creating or reusing platform-backed key material and a DID.
     *
     * If the wallet already contains persisted DIDs, the first persisted DID and key are reused.
     *
     * @param keyType Optional key type override. When omitted, [MobileWalletConfig.defaultKeyType] is used.
     * @param didMethod DID method used for registering a new DID. The default `key` method is handled locally.
     * @return The key identifier and DID used by this wallet.
     * @throws IllegalArgumentException When persisted DID state exists without a persisted key.
     */
    public suspend fun bootstrap(
        keyType: MobileWalletKeyType? = null,
        didMethod: String = "key",
    ): MobileWalletBootstrapResult {
        MobileDidSupport.ensureInitialized()
        val existingDids = didStore.listDids().toList()
        if (existingDids.isNotEmpty()) {
            val existingKeys = keyStore.listKeys().toList()
            require(existingKeys.isNotEmpty()) {
                "Wallet '${wallet.id}' has persisted DIDs but no persisted keys"
            }
            val existingKey = existingKeys.first()
            val keyAvailable = keyStore.getCrypto2Key(existingKey.keyId) != null
            require(keyAvailable) {
                "Wallet '${wallet.id}' persisted key '${existingKey.keyId}' is unavailable"
            }
            return MobileWalletBootstrapResult(
                keyId = existingKey.keyId,
                did = existingDids.first().did,
            )
        }

        val effectiveKeyType = keyType ?: defaultKeyType
        return createKeyAndDid(effectiveKeyType, didMethod)
    }

    private suspend fun createKeyAndDid(
        keyType: MobileWalletKeyType,
        didMethod: String,
    ): MobileWalletBootstrapResult {
        val normalizedMethod = didMethod.lowercase()
        val options = when (normalizedMethod) {
            "key" -> DidKeyCreateOptions()
            "jwk" -> DidJwkCreateOptions()
            else -> throw IllegalArgumentException("Mobile bootstrap supports only did:key and did:jwk")
        }
        val key = generateAndPersistKey(keyType)
        try {
            val didResult = didService.registerByKey(normalizedMethod, key, options)
            didStore.addDid(
                WalletDidEntry(
                    did = didResult.did,
                    document = didResult.didDocument.toJsonObject(),
                )
            )
            return MobileWalletBootstrapResult(keyId = key.id.value, did = didResult.did)
        } catch (cause: Throwable) {
            try {
                withContext(NonCancellable) {
                    check(keyStore.removeKey(key.id.value)) { "Failed to remove signing key after DID bootstrap failure" }
                }
            } catch (cleanupFailure: Throwable) {
                cause.addSuppressed(cleanupFailure)
            }
            throw cause
        }
    }

    /**
     * Resolves a credential offer and reports any transaction code the app must collect.
     *
     * Apps use [MobileWalletOfferResolution.previewHandle] with the reviewed [receive] overload.
     * The handle retains this exact resolution while the app collects any required code.
     *
     * @param offerUrl Credential offer URL, including `openid-credential-offer://` URLs.
     * @return Issuer, offered credential, and transaction-code metadata for app-side review.
     */
    public suspend fun resolveOffer(offerUrl: String): MobileWalletOfferResolution =
        WalletIssuanceHandler.previewOffer(
            wallet = wallet,
            request = ResolveOfferRequest(offerUrl = Url(offerUrl.trim())),
            metadataTrustResolver = credentialIssuerMetadataTrustResolver,
        ).toMobileOfferResolution(preferredLocales)

    /**
     * Receives credentials from an OpenID4VCI credential offer.
     *
     * This immediate path resolves the offer as part of the call. Review UIs should use
     * [resolveOffer] followed by the handle-based [receive] overload.
     *
     * @param offerUrl Credential offer URL, including `openid-credential-offer://` URLs.
     * @param txCode Optional transaction code for pre-authorized offers.
     * @param clientId Client identifier sent to the issuer.
     * @return Wallet-local identifiers of the stored credentials.
     */
    public suspend fun receive(
        offerUrl: String,
        txCode: String? = null,
        clientId: String = "wallet-client",
    ): List<String> = WalletIssuanceHandler.receiveCredential(
        wallet = wallet,
        request = ReceiveCredentialRequest(
            offerUrl = Url(offerUrl.trim()),
            txCode = txCode?.ifBlank { null },
            clientId = clientId,
        ),
        attestationAssembler = attestationAssembler,
        onEvent = ::emitSessionEvent,
        metadataTrustResolver = credentialIssuerMetadataTrustResolver,
    ).credentialIds

    /** Receives credentials using exactly one reviewed offer preview. */
    public suspend fun receive(
        previewHandle: MobileWalletIssuancePreviewHandle,
        txCode: String? = null,
        clientId: String = "wallet-client",
    ): List<String> =
        WalletIssuanceHandler.receiveCredential(
            wallet = wallet,
            request = ReceiveCredentialFromPreviewRequest(
                previewHandle = id.walt.wallet2.handlers.IssuancePreviewHandle(previewHandle.value),
                txCode = txCode?.ifBlank { null },
                clientId = clientId,
            ),
            attestationAssembler = attestationAssembler,
            onEvent = ::emitSessionEvent,
        ).credentialIds

    /** Discards a reviewed issuance preview after local dismissal. */
    public suspend fun discardIssuancePreview(previewHandle: MobileWalletIssuancePreviewHandle) {
        WalletIssuanceHandler.discardPreview(
            wallet = wallet,
            handle = id.walt.wallet2.handlers.IssuancePreviewHandle(previewHandle.value),
        )
    }

    /**
     * Lists all credentials currently stored in the mobile wallet.
     *
     * @return Credential entries, including display JSON, ordered by the underlying credential store.
     */
    public suspend fun credentials(): List<MobileWalletCredential> =
        wallet.streamAllCredentials().toList().map { credential ->
            val meta = credential.toMetadata()
            MobileWalletCredential(
                id = meta.id,
                format = meta.format,
                issuer = meta.issuer,
                subject = meta.subject,
                label = meta.label,
                addedAt = meta.addedAt?.toString(),
                credentialDataJson = credential.credential.credentialData.encodeJsonObject(),
            )
        }

    /**
     * Presents matching wallet credentials to an OpenID4VP verifier request.
     *
     * This immediate submission API is intended for callers that already handled
     * request review and user consent. Apps that need to display verifier details,
     * credential choices, selective disclosures, or transaction data should use
     * [previewPresentation] followed by [submitPresentation].
     *
     * @param requestUrl Authorization request URL received from the verifier.
     * @param did Optional DID override for selecting the wallet DID used in the presentation.
     * @param runPolicies Optional override for verifier policy execution in the core presentation handler.
     * @return The prepared host action or transmitted verifier outcome.
     */
    public suspend fun present(
        requestUrl: String,
        did: String? = null,
        runPolicies: Boolean? = null,
    ): MobileWalletPresentationResult {
        val result = WalletPresentationHandler.presentCredentialWithTrust(
            wallet = wallet,
            request = PresentCredentialRequest(
                requestUrl = Url(requestUrl.trim()),
                did = did,
                runPolicies = runPolicies,
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            onEvent = ::emitSessionEvent,
        )

        return result.toMobilePresentationResult()
    }

    /**
     * Resolves and previews an OpenID4VP presentation request without submitting credentials.
     * Protocol failures with a validated response destination are returned as [MobileWalletPresentationPreviewResult.Invalid].
     * Resolution or validation failures that cannot be answered safely remain local exceptions.
     */
    public suspend fun previewPresentation(requestUrl: String): MobileWalletPresentationPreviewResult {
        val result = WalletPresentationHandler.previewPresentationWithTrust(
            wallet = wallet,
            request = PreviewPresentationRequest(
                requestUrl = Url(requestUrl.trim()),
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            clientIdTrustConfiguration = clientIdTrustConfiguration,
            onEvent = ::emitSessionEvent,
        )

        return when (result) {
            is PreviewPresentationResult.Invalid ->
                MobileWalletPresentationPreviewResult.Invalid(
                    previewHandle = MobileWalletPresentationPreviewHandle(result.handle.value),
                    request = result.authorizationRequest.toMobileRequestContext(
                        preferredLocales = preferredLocales,
                        resolvedAuthorizationRequest = result.resolvedAuthorizationRequest,
                    ),
                    errorCode = result.error.code.toMobileErrorCode(),
                    message = result.error.message,
                )

            is PreviewPresentationResult.Ready -> {
                val profilesByType = transactionDataProfiles.associateBy { it.type }
                val transactionData = result.transactionData.map { item ->
                    val profile = profilesByType[item.type]
                    MobileWalletTransactionDataItem(
                        type = item.type,
                        displayName = profile?.displayName ?: item.type,
                        credentialQueryIds = item.credentialQueryIds,
                        supportedFields = profile?.fields.orEmpty(),
                        rawJson = item.rawJson.encodeJsonObject(),
                        detailsJson = item.details.encodeJsonObject(),
                    )
                }
                MobileWalletPresentationPreviewResult.Ready(
                    MobileWalletPresentationPreview(
                        previewHandle = MobileWalletPresentationPreviewHandle(result.handle.value),
                        request = result.authorizationRequest.toMobileRequestInfo(
                            preferredLocales = preferredLocales,
                            resolvedAuthorizationRequest = result.resolvedAuthorizationRequest,
                            responseEncryption = result.responseEncryption,
                            transactionData = transactionData,
                        ),
                        credentialOptions = result.credentialOptions.map { it.toMobileCredentialOption() },
                        credentialRequirements = result.credentialRequirements.map { it.toMobileCredentialRequirement() },
                    )
                )
            }
        }
    }

    /**
     * Submits a presentation using the credential options selected by the user from [previewPresentation].
     */
    public suspend fun submitPresentation(
        previewHandle: MobileWalletPresentationPreviewHandle,
        selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
        selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>? = null,
        did: String? = null,
        runPolicies: Boolean? = null,
    ): MobileWalletPresentationResult =
        WalletPresentationHandler.submitPresentation(
            wallet = wallet,
            request = SubmitPresentationRequest(
                previewHandle = PresentationPreviewHandle(previewHandle.value),
                selectedCredentialOptions = selectedCredentialOptions.map {
                    PresentationCredentialSelection(
                        queryId = it.queryId,
                        credentialId = it.credentialId,
                    )
                },
                selectedDisclosureOptions = selectedDisclosureOptions?.map {
                    PresentationDisclosureSelection(
                        queryId = it.queryId,
                        credentialId = it.credentialId,
                        path = it.path,
                    )
                },
                did = did,
                runPolicies = runPolicies,
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            onEvent = ::emitSessionEvent,
        ).toMobilePresentationResult()

    /** Discards a reviewed presentation after local dismissal. */
    public suspend fun discardPresentationPreview(previewHandle: MobileWalletPresentationPreviewHandle) {
        WalletPresentationHandler.discardPreview(
            wallet = wallet,
            handle = PresentationPreviewHandle(previewHandle.value),
        )
    }

    /** Rejects a reviewed presentation request and consumes its preview handle. */
    public suspend fun rejectPresentation(
        previewHandle: MobileWalletPresentationPreviewHandle,
        errorCode: MobileWalletPresentationErrorCode? = null,
        errorDescription: String? = null,
    ): MobileWalletPresentationResult = WalletPresentationHandler.rejectPresentation(
        wallet = wallet,
        request = RejectPresentationRequest(
            previewHandle = PresentationPreviewHandle(previewHandle.value),
            errorCode = errorCode?.errorCode,
            errorDescription = errorDescription,
        ),
        onEvent = ::emitSessionEvent,
    ).toMobilePresentationResult()

    /**
     * Deletes local wallet material owned by this mobile wallet instance.
     *
     * The active key, credential, and DID stores receive store-level remove calls. The wallet then closes
     * and deletes the encrypted local database and deletes the configured database key.
     */
    public suspend fun deleteWallet() {
        WalletIssuanceHandler.clearPreviews(wallet)
        WalletPresentationHandler.clearPreviews(wallet)
        keyStore.listKeys().toList().forEach { key ->
            keyStore.removeKey(key.keyId)
        }
        credentialStore.listCredentials().toList().forEach { credential ->
            credentialStore.removeCredential(credential.id)
        }
        didStore.listDids().toList().forEach { did ->
            didStore.removeDid(did.did)
        }
        deleteLocalPersistence()
    }

    private suspend fun emitSessionEvent(event: WalletSessionEvent) {
        val mobileEvent = event.toMobileWalletEvent()
        eventStream.tryEmit(mobileEvent)
        onEvent(mobileEvent)
    }

    private fun PresentationCredentialOption.toMobileCredentialOption(): MobileWalletPresentationCredentialOption =
        MobileWalletPresentationCredentialOption(
            queryId = queryId,
            credentialId = credentialId,
            multiple = multiple,
            format = format,
            issuer = issuer,
            subject = subject,
            label = label,
            credentialDataJson = credentialData.encodeJsonObject(),
            disclosures = disclosures.map { disclosure ->
                MobileWalletPresentationDisclosure(
                    path = disclosure.path,
                    name = disclosure.name,
                    valueJson = Json.encodeToString(JsonElement.serializer(), disclosure.value),
                    displayValue = disclosure.value.displayValue(),
                    selectivelyDisclosable = disclosure.selectivelyDisclosable,
                    required = disclosure.required,
                    selectable = disclosure.selectable,
                )
            },
        )

    private fun PresentationCredentialRequirement.toMobileCredentialRequirement(): MobileWalletPresentationCredentialRequirement =
        MobileWalletPresentationCredentialRequirement(options = options)

    private fun JsonObject.encodeJsonObject(): String =
        Json.encodeToString(JsonObject.serializer(), this)

    private fun JsonElement.displayValue(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull
            else -> toString()
        }
}

internal fun WalletPresentResult.toMobilePresentationResult(): MobileWalletPresentationResult =
    verifierResponse?.let { Json.encodeToString(JsonElement.serializer(), it) }.let { responseJson ->
        val responseUrl = getUrl
        val formHtml = formPostHtml
        when {
            responseUrl != null -> {
                require(
                    transmissionSuccess == null &&
                        formHtml == null &&
                        responseJson == null &&
                        redirectTo == null
                ) {
                    "Prepared URL result contains incompatible protocol fields"
                }
                MobileWalletPresentationResult.Prepared.OpenUrl(responseUrl)
            }

            formHtml != null -> {
                require(transmissionSuccess == null && responseJson == null && redirectTo == null) {
                    "Prepared form result contains incompatible protocol fields"
                }
                MobileWalletPresentationResult.Prepared.SubmitForm(formHtml)
            }

            transmissionSuccess == true -> MobileWalletPresentationResult.Transmitted.Succeeded(
                verifierResponseJson = requireNotNull(responseJson) {
                    "Transmitted presentation result is missing the verifier response"
                },
                redirectUrl = redirectTo,
            )

            transmissionSuccess == false -> MobileWalletPresentationResult.Transmitted.Failed(
                verifierResponseJson = requireNotNull(responseJson) {
                    "Failed presentation transmission is missing the verifier response"
                },
            )

            else -> error("Presentation result has no protocol outcome")
        }
    }

internal fun AuthorizationRequest.toMobileRequestInfo(
    preferredLocales: List<String>,
    resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
    responseEncryption: ResponseEncryption.Metadata? = null,
    transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
): MobileWalletPresentationRequestInfo {
    return MobileWalletPresentationRequestInfo(
        clientId = requireNotNull(clientId) {
            "A validated presentation request must contain client_id."
        },
        verifierMetadata = clientMetadata?.toMobileVerifierMetadata(preferredLocales),
        verifierMetadataProvenance = resolvedAuthorizationRequest.toMobileVerifierMetadataProvenance(clientId),
        responseUri = responseUri,
        state = state,
        nonce = requireNotNull(nonce) {
            "A validated presentation request must contain nonce."
        },
        responseEncryption = responseEncryption.toMobileResponseEncryption(),
        transactionData = transactionData,
    )
}

private fun AuthorizationRequest.toMobileRequestContext(
    preferredLocales: List<String>,
    resolvedAuthorizationRequest: ResolvedAuthorizationRequest? = null,
): MobileWalletPresentationRequestContext =
    MobileWalletPresentationRequestContext(
        clientId = requireNotNull(clientId) {
            "A reportable invalid presentation request must contain client_id."
        },
        verifierMetadata = clientMetadata?.toMobileVerifierMetadata(preferredLocales),
        verifierMetadataProvenance = resolvedAuthorizationRequest.toMobileVerifierMetadataProvenance(clientId),
        responseUri = responseUri,
        state = state,
        nonce = nonce,
        responseEncryption = null.toMobileResponseEncryption(),
    )

internal fun ResolvedAuthorizationRequest.toMobileVerifierMetadataProvenance(
    clientId: String?,
): MobileWalletVerifierMetadataProvenance = when (this) {
    is ResolvedAuthorizationRequest.WithRequestObject -> {
        val header = requestObject.decodeJws().header
        MobileWalletVerifierMetadataProvenance.SignedRequest(
            compactRequestObject = requestObject,
            algorithm = requireNotNull(header[JwtHeaderParams.ALGORITHM]?.jsonPrimitive?.contentOrNull),
            keyId = header[JwtHeaderParams.KEY_ID]?.jsonPrimitive?.contentOrNull,
            clientIdPrefix = requireNotNull(clientId).substringBefore(':'),
        )
    }
    is ResolvedAuthorizationRequest.Plain -> MobileWalletVerifierMetadataProvenance.UnsignedRequest
}

private fun WalletPresentFunctionality2.OID4VPErrorCode.toMobileErrorCode(): MobileWalletPresentationErrorCode = when (this) {
    WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED -> MobileWalletPresentationErrorCode.accessDenied
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST -> MobileWalletPresentationErrorCode.invalidRequest
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_CLIENT -> MobileWalletPresentationErrorCode.invalidClient
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_SCOPE -> MobileWalletPresentationErrorCode.invalidScope
    WalletPresentFunctionality2.OID4VPErrorCode.UNAUTHORIZED_CLIENT -> MobileWalletPresentationErrorCode.unauthorizedClient
    WalletPresentFunctionality2.OID4VPErrorCode.UNSUPPORTED_RESPONSE_TYPE -> MobileWalletPresentationErrorCode.unsupportedResponseType
    WalletPresentFunctionality2.OID4VPErrorCode.SERVER_ERROR -> MobileWalletPresentationErrorCode.serverError
    WalletPresentFunctionality2.OID4VPErrorCode.TEMPORARILY_UNAVAILABLE -> MobileWalletPresentationErrorCode.temporarilyUnavailable
    WalletPresentFunctionality2.OID4VPErrorCode.VP_FORMATS_NOT_SUPPORTED -> MobileWalletPresentationErrorCode.vpFormatsNotSupported
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST_URI_METHOD -> MobileWalletPresentationErrorCode.invalidRequestUriMethod
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA -> MobileWalletPresentationErrorCode.invalidTransactionData
    WalletPresentFunctionality2.OID4VPErrorCode.WALLET_UNAVAILABLE -> MobileWalletPresentationErrorCode.walletUnavailable
}
