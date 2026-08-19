@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import id.walt.crypto.utils.ShaUtils
import id.walt.crypto2.keys.Key
import id.walt.did.dids.Crypto2DidService
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.dids.DidJwkCreateOptions
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.handlers.WalletIssuanceSessionStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationSupport
import id.walt.wallet2.handlers.PresentCredentialRequest
import id.walt.wallet2.handlers.PresentationCredentialOption
import id.walt.wallet2.handlers.PresentationCredentialRequirement
import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.handlers.PresentationDisclosureSelection
import id.walt.wallet2.handlers.PresentationPreviewHandle
import id.walt.wallet2.handlers.PreviewPresentationRequest
import id.walt.wallet2.handlers.PreviewPresentationResult
import id.walt.wallet2.handlers.RejectPresentationRequest
import id.walt.wallet2.handlers.PreviewDcApiPresentationRequest
import id.walt.wallet2.handlers.SubmitPresentationRequest
import id.walt.wallet2.handlers.SubmitDcApiPresentationRequest
import id.walt.wallet2.handlers.WalletIssuanceAuthorizationCallback
import id.walt.wallet2.handlers.WalletIssuanceAuthorization
import id.walt.wallet2.handlers.WalletIssuanceOutcome
import id.walt.wallet2.handlers.WalletIssuanceSession
import id.walt.wallet2.handlers.WalletIssuanceSessionRequest
import id.walt.wallet2.handlers.WalletIssuanceSessionService
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.HttpWalletAttestationProvider
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.walt.openid4vp.clientidprefix.ClientIdTrustConfiguration
import id.walt.openid4vp.clientidprefix.prefixes.ClientId
import id.walt.openid4vp.clientidprefix.prefixes.DecentralizedIdentifier
import id.walt.openid4vp.clientidprefix.prefixes.OpenIdFederation
import id.walt.openid4vp.clientidprefix.prefixes.PreRegistered
import id.walt.openid4vp.clientidprefix.prefixes.RedirectUri
import id.walt.openid4vp.clientidprefix.prefixes.Unsupported
import id.walt.openid4vp.clientidprefix.prefixes.VerifierAttestation
import id.walt.openid4vp.clientidprefix.prefixes.X509Hash
import id.walt.openid4vp.clientidprefix.prefixes.X509SanDns
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vp.wallet.request.ResolvedAuthorizationRequest
import id.waltid.openid4vp.wallet.response.ResponseEncryption
import id.waltid.openid4vp.wallet.DcApiCredentialResponse
import id.waltid.openid4vp.wallet.DcApiWallet
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
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
 * @property metadataJson Optional arbitrary metadata stored alongside the credential as JSON.
 */
public data class MobileWalletCredential(
    public val id: String,
    public val format: String,
    public val issuer: String?,
    public val subject: String?,
    public val label: String?,
    public val addedAt: String?,
    public val credentialDataJson: String,
    public val metadataJson: String? = null,
)

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
    private val issuanceSessionStore: WalletIssuanceSessionStore? = null,
    private val generateAndPersistKey: suspend (MobileWalletKeyType, KeyUseAuthorizationPolicy) -> Key,
    private val runKeyUseAuthorizationPreflight: suspend (MobileWalletKeyType, KeyUseAuthorizationPolicy) -> KeyUseAuthorizationSupport =
        { _, _ -> error("This MobileWallet does not support key-use authorization preflight") },
    private val didService: Crypto2DidService = Crypto2DidService,
    private val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    private val defaultKeyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = KeyUseAuthorizationPolicy.BiometricCurrentSet,
    attestationConfig: WalletAttestationConfig? = null,
    private val preferredLocales: List<String> = emptyList(),
    private val transactionDataProfiles: List<MobileWalletTransactionDataProfile> = emptyList(),
    private val clientIdTrustConfiguration: ClientIdTrustConfiguration = ClientIdTrustConfiguration(),
    private val credentialIssuerMetadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    private val credentialRegistry: MobileWalletCredentialRegistry = UnavailableMobileWalletCredentialRegistry,
    private val readerTrustEvaluator: MobileWalletReaderTrustEvaluator = UnconfiguredMobileWalletReaderTrustEvaluator,
    private val onEvent: suspend (MobileWalletEvent) -> Unit = {},
    private val onDigitalCredentialRegistryChanged: suspend () -> Unit = {},
    private val deleteLocalPersistence: suspend () -> Unit = {},
    /** Issuance transport override. Only tests set this; production uses the configured engine. */
    issuanceHttpClient: HttpClient? = null,
) {
    private val eventStream = MobileWalletEventStream()
    /**
     * Buffered stream of recent issuance and presentation events emitted by this wallet.
     */
    public val events: Flow<MobileWalletEvent> = eventStream.events

    private val lastRegistrationResult = MutableStateFlow<MobileWalletCredentialRegistrationResult?>(null)

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
    private val annexCEngine = MobileWalletAnnexCEngine(
        wallet = wallet,
        readerTrustEvaluator = readerTrustEvaluator,
        registryRecords = ::registryRecords,
    )

    private val issuanceSessions = WalletIssuanceSessionService(
        wallet = wallet,
        attestationAssembler = attestationAssembler,
        metadataTrustResolver = credentialIssuerMetadataTrustResolver,
        onEvent = ::emitSessionEvent,
        sessionStore = issuanceSessionStore,
        httpClient = issuanceHttpClient,
    )

    /**
     * Initializes the wallet by creating or reusing wallet signing key material and a DID.
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
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy? = null,
    ): MobileWalletBootstrapResult {
        MobileDidSupport.ensureInitialized()
        val existingDids = didStore.listDids().toList()
        if (existingDids.isNotEmpty()) {
            val existingKeys = keyStore.listKeys().toList()
            require(existingKeys.isNotEmpty()) {
                "Wallet '${wallet.id}' has persisted DIDs but no persisted keys"
            }
            val existingKey = existingKeys.first()
            // Force platform key resolution before a provider extension offers a credential.
            val keyAvailable = keyStore.getCrypto2Key(existingKey.keyId) != null
            require(keyAvailable) {
                "Wallet '${wallet.id}' persisted key '${existingKey.keyId}' is unavailable"
            }
            syncDigitalCredentialRegistration()
            return MobileWalletBootstrapResult(
                keyId = existingKey.keyId,
                did = existingDids.first().did,
            )
        }

        val effectiveKeyType = keyType ?: defaultKeyType
        val effectivePolicy = keyUseAuthorizationPolicy ?: defaultKeyUseAuthorizationPolicy
        return createKeyAndDid(effectiveKeyType, didMethod, effectivePolicy)
            .also { syncDigitalCredentialRegistration() }
    }

    /** Checks whether a key-use authorization request is supported without creating or persisting a key. */
    public suspend fun keyUseAuthorizationPreflight(
        keyType: MobileWalletKeyType = defaultKeyType,
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy = defaultKeyUseAuthorizationPolicy,
    ): KeyUseAuthorizationSupport = runKeyUseAuthorizationPreflight(keyType, keyUseAuthorizationPolicy)

    private suspend fun createKeyAndDid(
        keyType: MobileWalletKeyType,
        didMethod: String,
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy,
    ): MobileWalletBootstrapResult {
        val normalizedMethod = didMethod.lowercase()
        val options = when (normalizedMethod) {
            "key" -> DidKeyCreateOptions()
            "jwk" -> DidJwkCreateOptions()
            else -> throw IllegalArgumentException("Mobile bootstrap supports only did:key and did:jwk")
        }
        val key = generateAndPersistKey(keyType, keyUseAuthorizationPolicy)
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
     * Resolves an offer and starts a bound OpenID4VCI 1.0 issuance session.
     *
     * This operation only resolves and previews the offer. For authorization-code offers,
     * [beginAuthorizationIssuance] creates the browser request after the user accepts the offer.
     */
    public suspend fun startIssuance(
        request: MobileWalletIssuanceRequest,
    ): WalletIssuanceSession = issuanceSessions.start(
        newIssuanceRequest(
            offer = request.offer,
            keyId = request.keyId,
            did = request.did,
            clientId = request.clientId,
            redirectUri = request.redirectUri.trim(),
        )
    )

    /**
     * Starts the authorization-code browser request for an accepted issuance session.
     *
     * State, PKCE, PAR, client attestation, and the callback continuation are created only by
     * this explicit acceptance transition.
     */
    public suspend fun beginAuthorizationIssuance(
        sessionId: String,
    ): WalletIssuanceAuthorization = issuanceSessions.beginAuthorization(sessionId)

    /** Continues a pre-authorized session after review and optional transaction-code collection. */
    public suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String? = null,
    ): WalletIssuanceOutcome =
        issuanceSessions.continuePreAuthorized(
            sessionId = sessionId,
            transactionCode = transactionCode?.ifBlank { null },
        ).alsoRefreshDigitalCredentialRegistration()

    /**
     * Validates and consumes a browser callback bound to an authorization-code session.
     *
     * The callback target, OAuth state, authorization code, PKCE verifier, issuer metadata, and
     * selected holder key are all taken from or checked against the authoritative session record.
     */
    public suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletIssuanceOutcome =
        issuanceSessions.continueAuthorization(
            WalletIssuanceAuthorizationCallback(
                sessionId = sessionId,
                callbackUri = callbackUri,
            )
        ).alsoRefreshDigitalCredentialRegistration()

    /** Cancels an active issuance session and discards its protocol continuation material. */
    public suspend fun cancelIssuance(sessionId: String): WalletIssuanceOutcome =
        issuanceSessions.cancel(sessionId)

    /** Polls a typed deferred credential result returned by a previous issuance continuation. */
    public suspend fun resumeDeferredIssuance(
        deferredCredentialId: String,
    ): WalletIssuanceOutcome =
        issuanceSessions.resumeDeferred(deferredCredentialId).alsoRefreshDigitalCredentialRegistration()

    /**
     * Re-registers platform credential metadata whenever a transition actually stored a credential.
     *
     * [WalletIssuanceOutcome.Deferred] and [WalletIssuanceOutcome.Failed] are both included because
     * either can report credentials that were already stored before the session stopped advancing.
     *
     * The outcome is returned unchanged: a credential that was stored has been issued, so a platform
     * registry that could not be updated afterwards must not present itself as an issuance failure.
     */
    private suspend fun WalletIssuanceOutcome.alsoRefreshDigitalCredentialRegistration(): WalletIssuanceOutcome =
        also {
            val storedCredentialIds = when (it) {
                is WalletIssuanceOutcome.Stored -> it.credentialIds
                is WalletIssuanceOutcome.Deferred -> it.storedCredentialIds
                is WalletIssuanceOutcome.Failed -> it.storedCredentialIds
                is WalletIssuanceOutcome.Cancelled -> emptyList()
            }
            if (storedCredentialIds.isNotEmpty()) syncDigitalCredentialRegistration()
        }

    private suspend fun newIssuanceRequest(
        offer: MobileWalletCredentialOffer,
        clientId: String,
        redirectUri: String,
        keyId: String? = null,
        did: String? = null,
    ): WalletIssuanceSessionRequest {
        val selectedKeyId = keyId ?: keyStore.listKeys().toList().firstOrNull()?.keyId
            ?: error("No holder key is available for credential issuance")
        val selectedDid = did ?: didStore.listDids().toList().firstOrNull()?.did
        return when (offer) {
            is MobileWalletCredentialOffer.Uri -> WalletIssuanceSessionRequest(
                offerUrl = Url(offer.value.trim()),
                offerJson = null,
                keyId = selectedKeyId,
                did = selectedDid,
                clientId = clientId,
                redirectUri = Url(redirectUri),
            )
            is MobileWalletCredentialOffer.InlineJson -> WalletIssuanceSessionRequest(
                offerUrl = null,
                offerJson = Json.parseToJsonElement(offer.value).jsonObject,
                keyId = selectedKeyId,
                did = selectedDid,
                clientId = clientId,
                redirectUri = Url(redirectUri),
            )
        }
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
                metadataJson = credential.metadata?.let { Json.encodeToString(JsonObject.serializer(), it) },
            )
        }

    /** Returns the native adapter's current runtime capability snapshot. */
    public fun digitalCredentialCapabilities(): MobileWalletDigitalCredentialCapabilities =
        credentialRegistry.capabilities

    /**
     * Outcome of the most recent platform registry synchronization, or null before the first one.
     *
     * A wallet operation that stores or removes a credential synchronizes the registry afterwards
     * and does not fail if that synchronization does not succeed, so this is where an application
     * learns that the platform projection is stale. Recover by calling
     * [refreshDigitalCredentialRegistration] again; the wallet store it projects is unaffected.
     */
    public val digitalCredentialRegistration: StateFlow<MobileWalletCredentialRegistrationResult?> =
        lastRegistrationResult.asStateFlow()

    /**
     * Synchronizes platform credential metadata to a minimal view of the current wallet state.
     *
     * Raw credentials, issuer-signed payloads, and private keys are never registered, and neither
     * are the SD-JWT VC infrastructure claims listed in [SD_JWT_INFRASTRUCTURE_CLAIMS]. Every
     * remaining decoded claim value is registered, because the platform matcher runs out of process
     * and cannot ask the wallet for a value it was not given.
     *
     * This is the retry entry point: it is safe to call at any time, and calling it again after a
     * failure re-publishes the current wallet state. It reports an adapter failure through the
     * returned result and [digitalCredentialRegistration], and only propagates an exception if
     * reading the wallet's own credentials failed.
     */
    public suspend fun refreshDigitalCredentialRegistration(): MobileWalletCredentialRegistrationResult {
        val records = registryRecords()
        val presentationResult = runCatching {
            credentialRegistry.replace(registryId = digitalCredentialRegistryId(), records = records)
        }.getOrElse { failure ->
            if (failure is CancellationException) throw failure
            MobileWalletCredentialRegistrationResult(
                available = false,
                registeredEntryCount = 0,
                reason = failure.message ?: failure::class.simpleName ?: "Credential registration failed",
            )
        }
        // Creation options advertise issuance capability and must not share the presentation
        // replace lifecycle; failures here do not roll back a successful presentation projection.
        runCatching {
            credentialRegistry.registerCreationOptions()
        }.onFailure { failure ->
            if (failure is CancellationException) throw failure
        }
        lastRegistrationResult.value = presentationResult
        return presentationResult
    }

    /**
     * Synchronizes the platform registry after the wallet store has already changed.
     *
     * The credential change is committed by the time this runs, so neither a registry nor a host
     * failure may turn a completed operation into a failed one; the outcome is reported through
     * [digitalCredentialRegistration] instead. The host is notified even when publishing failed,
     * because an earlier projection may still be pending.
     */
    private suspend fun syncDigitalCredentialRegistration() {
        runCatching { refreshDigitalCredentialRegistration() }.onFailure { failure ->
            if (failure is CancellationException) throw failure
            lastRegistrationResult.value = MobileWalletCredentialRegistrationResult(
                available = false,
                registeredEntryCount = 0,
                reason = failure.message ?: failure::class.simpleName ?: "Credential registration failed",
            )
        }
        runCatching { onDigitalCredentialRegistryChanged() }.onFailure { failure ->
            if (failure is CancellationException) throw failure
        }
    }

    /**
     * Removes one credential and re-publishes the native registry to reject stale selections.
     *
     * The removal is authoritative: the returned value reports whether the credential store removed
     * the credential, regardless of whether the platform registry could be updated afterwards.
     */
    public suspend fun deleteCredential(credentialId: String): Boolean {
        val removed = credentialStore.removeCredential(credentialId)
        syncDigitalCredentialRegistration()
        return removed
    }

    /**
     * Resolves an OS-mediated OpenID4VP request and returns consent metadata without releasing a credential.
     * The caller-provided origin must already have been asserted by the platform adapter. For an
     * unsigned request, the returned [MobileWalletDigitalCredentialPreview.verifiedOrigin] is the
     * authenticated requester identity and [MobileWalletDigitalCredentialRequestInfo.clientId]
     * remains null because the untrusted request-supplied `client_id` is ignored.
     */
    public suspend fun previewDigitalCredentialPresentation(
        request: MobileWalletDigitalCredentialRequest,
    ): MobileWalletDigitalCredentialPreview {
        require(request.protocol != MobileWalletDigitalCredentialProtocols.ISO_MDOC_ANNEX_C) {
            "ISO 18013-7 Annex C requests use the dedicated Annex C facade"
        }
        val currentRecords = registryRecords()
        val credentialIdsByRegistryId = currentRecords.associate { it.registryEntryId to it.credentialId }
        val selectedCredentialIds = request.selectedRegistryEntryIds.map { registryId ->
            credentialIdsByRegistryId[registryId] ?: throw MobileWalletStaleRegistryEntryException(registryId)
        }.toSet()

        val result = WalletPresentationHandler.previewDcApiPresentation(
            wallet = wallet,
            request = PreviewDcApiPresentationRequest(
                protocol = request.protocol,
                data = Json.parseToJsonElement(request.dataJson).jsonObject,
                origin = request.verifiedOrigin,
                // Empty means the platform supplied no credential restriction, and DCQL then matches
                // over the whole store. Platform adapters must fail closed when a selection was
                // expected but could not be resolved, because arriving here from a malformed
                // selection would silently widen the candidate set.
                eligibleCredentialIds = selectedCredentialIds.ifEmpty { null },
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            onEvent = ::emitSessionEvent,
        )
        val authorizationRequest = result.resolvedRequest.authorizationRequest
        val profilesByType = transactionDataProfiles.associateBy { it.type }

        return MobileWalletDigitalCredentialPreview(
            requestId = result.requestId,
            protocol = request.protocol,
            verifiedOrigin = result.resolvedRequest.origin,
            request = authorizationRequest.toMobileDigitalCredentialRequestInfo(
                preferredLocales = preferredLocales,
                transactionData = result.transactionData.map { item ->
                    val profile = profilesByType[item.type]
                    MobileWalletTransactionDataItem(
                        type = item.type,
                        displayName = profile?.displayName ?: item.type,
                        credentialQueryIds = item.credentialQueryIds,
                        supportedFields = profile?.fields.orEmpty(),
                        rawJson = item.rawJson.encodeJsonObject(),
                        detailsJson = item.details.encodeJsonObject(),
                    )
                },
            ),
            credentialOptions = result.credentialOptions.map { it.toMobileCredentialOption() },
            credentialRequirements = result.credentialRequirements.map { it.toMobileCredentialRequirement() },
            readerTrust = MobileWalletReaderTrust.NotApplicable,
        )
    }

    /**
     * Builds a response for a retained Digital Credentials preview. No network transport is performed.
     */
    public suspend fun submitDigitalCredentialPresentation(
        requestId: String,
        selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
        selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>? = null,
        did: String? = null,
    ): MobileWalletDigitalCredentialResponse {
        require(selectedCredentialOptions.isNotEmpty()) { "At least one credential must be selected after consent" }
        val response = WalletPresentationHandler.submitDcApiPresentation(
            wallet = wallet,
            request = SubmitDcApiPresentationRequest(
                requestId = requestId,
                selectedCredentialOptions = selectedCredentialOptions.map {
                    PresentationCredentialSelection(it.queryId, it.credentialId)
                },
                selectedDisclosureOptions = selectedDisclosureOptions?.map {
                    PresentationDisclosureSelection(it.queryId, it.credentialId, it.path)
                },
                did = did,
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            onEvent = ::emitSessionEvent,
        )
        return response.toMobileDigitalCredentialResponse()
    }

    /** Builds the single-field OpenID4VP DC API error object required by Appendix A. */
    public fun digitalCredentialErrorResponse(protocol: String, error: String): MobileWalletDigitalCredentialResponse =
        DcApiWallet.buildErrorResponse(
            protocol = id.waltid.openid4vp.wallet.DcApiRequestProtocol.fromValue(protocol),
            error = WalletPresentFunctionality2.OID4VPErrorCode.entries.firstOrNull { it.code == error }
                ?: throw IllegalArgumentException("Unsupported OpenID4VP error code"),
        ).toMobileDigitalCredentialResponse()

    /** Parses a raw Annex C `DeviceRequest` into the shape a consent screen can render. */
    public fun parseAnnexCDeviceRequest(deviceRequestBase64Url: String): MobileWalletAnnexCParsedRequest =
        annexCEngine.parseDeviceRequest(deviceRequestBase64Url)

    /**
     * Reads an Annex C request out of a platform-neutral Digital Credentials request.
     *
     * Platforms that hand the wallet the whole request up front - Android Credential Manager - deliver
     * the raw `deviceRequest`/`encryptionInfo` pair inside [MobileWalletDigitalCredentialRequest.dataJson].
     * That envelope shape is Annex C's, so it is decoded here rather than in each platform adapter.
     * Apple's provider extension has no such envelope and constructs
     * [MobileWalletAnnexCRequest] from its parsed request instead.
     */
    public fun annexCRequest(request: MobileWalletDigitalCredentialRequest): MobileWalletAnnexCRequest =
        annexCEngine.annexCRequest(request)

    /** Previews an ISO 18013-7 Annex C request without signing or releasing credentials. */
    public suspend fun previewAnnexCPresentation(request: MobileWalletAnnexCRequest): MobileWalletAnnexCPreview =
        annexCEngine.preview(request)

    /** Verifies the raw post-consent request, builds device authentication in KMP, and HPKE-encrypts the response. */
    public suspend fun submitAnnexCPresentation(
        submission: MobileWalletAnnexCSubmission,
    ): MobileWalletDigitalCredentialResponse = annexCEngine.submit(submission)

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
     * Active issuance continuations are invalidated before the key, credential, and DID stores receive
     * store-level remove calls. The wallet then closes and deletes the encrypted local database and deletes
     * the configured database key.
     */
    public suspend fun deleteWallet() {
        WalletPresentationHandler.clearPreviews(wallet)
        issuanceSessions.clearSessions()
        keyStore.listKeys().toList().forEach { key ->
            keyStore.removeKey(key.keyId)
        }
        credentialStore.listCredentials().toList().forEach { credential ->
            credentialStore.removeCredential(credential.id)
        }
        didStore.listDids().toList().forEach { did ->
            didStore.removeDid(did.did)
        }
        // The wallet material is already gone, so an unreachable registry must not abort the
        // deletion and leave the local database behind.
        syncDigitalCredentialRegistration()
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

    private fun DcApiCredentialResponse.toMobileDigitalCredentialResponse(): MobileWalletDigitalCredentialResponse =
        MobileWalletDigitalCredentialResponse(
            protocol = protocol,
            dataJson = Json.encodeToString(JsonObject.serializer(), data),
        )

    private fun digitalCredentialRegistryId(): String =
        "waltid-${ShaUtils.calculateSha256Base64Url(wallet.id).take(24)}"

    private suspend fun registryRecords(): List<MobileWalletCredentialRegistryRecord> =
        wallet.streamAllCredentials().toList().mapNotNull { stored ->
            val registryEntryId = "dc-${ShaUtils.calculateSha256Base64Url("${wallet.id}\u0000${stored.id}").take(32)}"
            val metadata = stored.toMetadata()
            when (val credential = stored.credential) {
                is MdocsCredential -> MobileWalletCredentialRegistryRecord(
                    registryEntryId = registryEntryId,
                    credentialId = stored.id,
                    format = MobileWalletDigitalCredentialFormat.MDOC,
                    type = credential.docType,
                    fields = credential.credentialData
                        .filterKeys { it != "docType" }
                        .flatMap { (namespace, value) ->
                            value.jsonObject.map { (element, elementValue) ->
                                MobileWalletCredentialRegistryField(
                                    path = listOf(namespace, element),
                                    valueJson = Json.encodeToString(JsonElement.serializer(), elementValue),
                                    selectivelyDisclosable = true,
                                )
                            }
                        },
                    displayName = metadata.label ?: credential.docType,
                )
                else -> if (metadata.format in setOf("vc+sd-jwt", "dc+sd-jwt", "sd-jwt-vc")) {
                    val data = credential.credentialData
                    val type = data["vct"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val selectivelyDisclosablePaths = (credential as? SelectivelyDisclosableVerifiableCredential)
                        ?.disclosures
                        .orEmpty()
                        .mapNotNull { disclosure -> disclosure.location?.toRegistryFieldPath() }
                        .toSet()
                    MobileWalletCredentialRegistryRecord(
                        registryEntryId = registryEntryId,
                        credentialId = stored.id,
                        format = MobileWalletDigitalCredentialFormat.SD_JWT_VC,
                        type = type,
                        fields = data
                            .filterKeys { it !in SD_JWT_INFRASTRUCTURE_CLAIMS }
                            .flatMap { (name, value) ->
                                value.flattenRegistryFields(
                                    path = listOf(name),
                                    selectivelyDisclosablePaths = selectivelyDisclosablePaths,
                                )
                            },
                        displayName = metadata.label ?: type,
                    )
                } else null
            }
        }

    /**
     * Maps an SD-JWT VC claim path onto the object-key path used by registry fields.
     *
     * [flattenRegistryFields] emits an array as one leaf at the path of its containing key, so a
     * disclosure addressing an array index or wildcard is truncated at the first non-key component
     * to name that same leaf. Dropping such a component instead would shorten the path and silently
     * mark a disclosable claim as non-disclosable.
     */
    private fun List<JsonElement>.toRegistryFieldPath(): List<String> =
        takeWhile { it is JsonPrimitive && it.isString }.map { it.jsonPrimitive.content }

    private fun JsonElement.flattenRegistryFields(
        path: List<String>,
        selectivelyDisclosablePaths: Set<List<String>>,
    ): List<MobileWalletCredentialRegistryField> = when (this) {
        is JsonObject -> entries.flatMap { (name, value) ->
            value.flattenRegistryFields(path + name, selectivelyDisclosablePaths)
        }
        else -> listOf(
            MobileWalletCredentialRegistryField(
                path = path,
                valueJson = Json.encodeToString(JsonElement.serializer(), this),
                selectivelyDisclosable = path in selectivelyDisclosablePaths,
            )
        )
    }

    private fun JsonObject.encodeJsonObject(): String =
        Json.encodeToString(JsonObject.serializer(), this)

    private fun JsonElement.displayValue(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull
            else -> toString()
        }

    private companion object {
        /**
         * SD-JWT VC claims that carry no matchable user data and must never reach a platform registry.
         *
         * `sub` is deliberately absent: it is a subject identifier a verifier may query. `_sd` is
         * already removed while resolving disclosures, but the sibling `_sd_alg` hash-algorithm
         * declaration is not, so it has to be excluded here.
         */
        private val SD_JWT_INFRASTRUCTURE_CLAIMS = setOf(
            "vct", "iss", "iat", "nbf", "exp", "cnf", "status", "_sd", "_sd_alg",
        )
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
    val verifiedClientId = requireNotNull(clientId) {
            "A validated presentation request must contain client_id."
        }
    return MobileWalletPresentationRequestInfo(
        clientId = verifiedClientId,
        verifierMetadata = clientMetadata?.toMobileVerifierMetadata(preferredLocales),
        requestAuthentication = resolvedAuthorizationRequest.toMobileRequestAuthentication(),
        responseUri = responseUri,
        state = state,
        nonce = requireNotNull(nonce) {
            "A validated presentation request must contain nonce."
        },
        responseEncryption = responseEncryption.toMobileResponseEncryption(),
        transactionData = transactionData,
    )
}

private fun AuthorizationRequest.toMobileDigitalCredentialRequestInfo(
    preferredLocales: List<String>,
    transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
): MobileWalletDigitalCredentialRequestInfo =
    MobileWalletDigitalCredentialRequestInfo(
        clientId = clientId,
        verifierMetadata = clientMetadata?.toMobileVerifierMetadata(preferredLocales),
        nonce = requireNotNull(nonce) {
            "A validated Digital Credentials request must contain nonce."
        },
        responseMode = responseMode?.let { mode ->
            Json.encodeToString(OpenID4VPResponseMode.serializer(), mode).trim('"')
        },
        transactionData = transactionData,
    )

internal fun AuthorizationRequest.toMobileRequestContext(
    preferredLocales: List<String>,
    resolvedAuthorizationRequest: ResolvedAuthorizationRequest,
): MobileWalletPresentationRequestContext {
    val verifiedClientId = requireNotNull(clientId) {
            "A reportable invalid presentation request must contain client_id."
        }
    return MobileWalletPresentationRequestContext(
        clientId = verifiedClientId,
        verifierMetadata = clientMetadata?.toMobileVerifierMetadata(preferredLocales),
        requestAuthentication = resolvedAuthorizationRequest.toMobileRequestAuthentication(),
        responseUri = responseUri,
        state = state,
        nonce = nonce,
        responseEncryption = null.toMobileResponseEncryption(),
    )
}

internal fun ResolvedAuthorizationRequest.toMobileRequestAuthentication(): MobileWalletRequestAuthentication =
    when (this) {
        is ResolvedAuthorizationRequest.Plain -> MobileWalletRequestAuthentication.Unauthenticated
        is ResolvedAuthorizationRequest.UnsignedRequestObject -> MobileWalletRequestAuthentication.Unauthenticated
        is ResolvedAuthorizationRequest.AuthenticatedRequestObject -> MobileWalletRequestAuthentication.Authenticated(
            compactRequestObject = requestObject,
            algorithm = authentication.algorithm,
            keyId = authentication.keyId,
            clientIdScheme = authentication.clientId.toMobileClientIdScheme(),
        )
    }

private fun ClientId.toMobileClientIdScheme(): MobileWalletClientIdScheme = when (this) {
    is PreRegistered -> MobileWalletClientIdScheme.PRE_REGISTERED
    is RedirectUri -> MobileWalletClientIdScheme.REDIRECT_URI
    is X509SanDns -> MobileWalletClientIdScheme.X509_SAN_DNS
    is X509Hash -> MobileWalletClientIdScheme.X509_HASH
    is DecentralizedIdentifier -> MobileWalletClientIdScheme.DECENTRALIZED_IDENTIFIER
    is VerifierAttestation -> MobileWalletClientIdScheme.VERIFIER_ATTESTATION
    is OpenIdFederation -> MobileWalletClientIdScheme.OPENID_FEDERATION
    is Unsupported -> error("Unsupported client identifier cannot be authenticated: $prefix")
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
