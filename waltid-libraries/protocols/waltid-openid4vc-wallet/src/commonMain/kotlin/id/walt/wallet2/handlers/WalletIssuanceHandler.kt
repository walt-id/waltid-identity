@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.handlers

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.crypto2.keys.Key as Crypto2Key
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.did.dids.DidService
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.issuer.KeyAttestationsRequired
import id.walt.openid4vci.metadata.issuer.ProofTypeMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.proofs.Proofs
import id.walt.openid4vci.requests.notification.NotificationEvent
import id.walt.wallet2.data.*
import id.walt.wallet2.handlers.WalletIssuanceHandler.exchangeCode
import id.walt.wallet2.handlers.WalletIssuanceHandler.pollDeferredFlow
import id.walt.wallet2.handlers.WalletIssuanceHandler.resolveOffer
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationChallengeContext
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.authorization.AuthorizationRequestBuilder
import id.waltid.openid4vci.wallet.authorization.PushedAuthorizationRequestExecutor
import id.waltid.openid4vci.wallet.clientauth.ClientAssertionBuilder
import id.waltid.openid4vci.wallet.credential.CredentialEncryption
import id.waltid.openid4vci.wallet.credential.CredentialRequestBuilder
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.waltid.openid4vci.wallet.metadata.OfferedCredentialResolver
import id.waltid.openid4vci.wallet.metadata.ResolvedAuthorizationServerMetadata
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.offer.CredentialOfferParser
import id.waltid.openid4vci.wallet.offer.CredentialOfferResolver
import id.waltid.openid4vci.wallet.proof.JwtProofBuilder
import id.waltid.openid4vci.wallet.proof.ProofKeyBinding
import id.waltid.openid4vci.wallet.token.ClientAssertionFactory
import id.waltid.openid4vci.wallet.token.ClientAttestationHeadersFactory
import id.waltid.openid4vci.wallet.token.DPoPProofFactory
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import id.waltid.openid4vci.wallet.token.TokenResponseHeadersHandler
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.*

private val log = KotlinLogging.logger {}

const val Wallet2DefaultClientId = "eudiw-abca"
private const val DEFAULT_CLIENT_ID = Wallet2DefaultClientId

// ---------------------------------------------------------------------------
// Shared offer-source contract
// ---------------------------------------------------------------------------

/** Validates the mutual exclusivity of [offerUrl] and [offerJson]. Call from `init {}` blocks. */
fun CredentialOfferSource.checkOfferSource() {
    check(offerUrl != null || offerJson != null) { "Either offerUrl or offerJson must be provided" }
    check(offerUrl == null || offerJson == null) { "Only one of offerUrl or offerJson may be provided, not both" }
}

internal fun attestationAssemblerForTokenMethods(
    methods: Set<String>?,
    attestationAssembler: ClientAttestationAssembler?,
    anonymousPreAuthorizedAccessSupported: Boolean = false,
): ClientAttestationAssembler? {
    if (methods?.contains(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH) != true) return null
    if (attestationAssembler != null) return attestationAssembler
    if (
        !anonymousPreAuthorizedAccessSupported &&
        methods.all { it == ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH }
    ) {
        throw WalletInstanceAttestationRequiredException()
    }
    return null
}

/**
 * OpenID4VCI 1.0 credential issuance logic.
 *
 * Orchestrates the wallet-side steps using waltid-openid4vci-wallet primitives.
 * Returns a [Flow] of [StoredCredential] for the full flow so callers can
 * react to each credential as it arrives (useful for streaming UIs).
 */
object WalletIssuanceHandler {
    private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())
    private val lenientJson = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val previewedOffers = PreviewSessionStore<ResolvedIssuanceOffer>(sessionName = "Issuance")

    /**
     * Shared [HttpClient] for all issuance step functions. Lazily initialized on first use.
     *
     * The client is created and configured by [WebDataFetcher] (default Native engine - Java on
     * JVM, with TLS 1.3 - plus centrally-managed request/logging configuration, including lenient
     * JSON content negotiation) rather than constructed directly with the platform default engine.
     *
     * Using a shared lazy instance avoids creating a new connection pool on every isolated-step
     * call (resolveOffer, requestToken, etc.), which would be wasteful.
     *
     * The full receive flow and auth-code flow accept an httpClient parameter so tests and the
     * Enterprise can inject a custom client; they fall back to this shared instance by default.
     */
    private val httpClient: HttpClient by lazy {
        WebDataFetcher(WebDataFetcherId.WALLET2_ISSUANCE_HANDLER).httpClient
    }

    /**
     * Resolves a credential offer from any [CredentialOfferSource], handling both inline JSON
     * and URL (openid-credential-offer://...) forms. Extracted to eliminate three identical
     * if/else blocks across [receiveCredentialFlow], [resolveOffer], and [generateAuthorizationUrl].
     */
    private suspend fun resolveOffer(source: CredentialOfferSource, httpClient: HttpClient) =
        if (source.offerJson != null) {
            val inlineOffer = lenientJson.decodeFromString<CredentialOffer>(source.getEffectiveOfferString())
            CredentialOfferResolver(httpClient).resolveCredentialOffer(credentialOffer = inlineOffer, credentialOfferUri = null)
        } else {
            val req = CredentialOfferParser.parseCredentialOfferUrl(source.getEffectiveOfferString())
            CredentialOfferResolver(httpClient).resolveCredentialOffer(
                credentialOffer = req.credentialOffer,
                credentialOfferUri = req.credentialOfferUri
            )
        }

    /** Immediate, non-previewed pre-authorized-code issuance flow. */
    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /**
         * Called whenever the issuer defers a credential.
         * [credentialConfigurationId] identifies which credential was deferred;
         * [transactionId] should be stored and passed to [pollDeferredFlow] later.
         */
        onDeferredTransactionId: suspend (credentialConfigurationId: String, transactionId: String) -> Unit = { _, _ -> },
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): Flow<StoredCredential> = receiveCredentialFlow(
        wallet = wallet,
        request = request,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        onDeferredTarget = { target -> onDeferredTransactionId(target.credentialConfigurationId, target.transactionId) },
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
        metadataTrustResolver = metadataTrustResolver,
    )

    /** Streams issuance with the complete target and holder context for each deferred request. */
    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /**
         * Called whenever the issuer defers a credential.
         * Each deferred target identifies its configuration and optional dataset;
         * [DeferredCredentialTransaction.transactionId] should be stored and passed to [pollDeferredFlow] later.
         */
        onDeferredTarget: suspend (DeferredCredentialTransaction) -> Unit,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): Flow<StoredCredential> = receiveCredentialFlowInternal(
        wallet = wallet,
        request = request,
        resolvedOffer = null,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        onDeferredCredential = onDeferredTarget,
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
        metadataTrustResolver = metadataTrustResolver,
    )

    /**
     * Reviewed pre-authorized-code issuance flow.
     *
     * Failed attempts retain the selected preview for retry. Successful completion consumes it.
     */
    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialFromPreviewRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onDeferredTransactionId: suspend (credentialConfigurationId: String, transactionId: String) -> Unit = { _, _ -> },
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): Flow<StoredCredential> = receiveCredentialFlow(
        wallet = wallet,
        request = request,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        onDeferredTarget = { target -> onDeferredTransactionId(target.credentialConfigurationId, target.transactionId) },
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
    )

    /** Streams issuance with the complete target and holder context for each deferred request. */
    fun receiveCredentialFlow(
        wallet: Wallet,
        request: ReceiveCredentialFromPreviewRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onDeferredTarget: suspend (DeferredCredentialTransaction) -> Unit,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): Flow<StoredCredential> = channelFlow {
        previewedOffers.useRetainingOnFailure(
            walletId = wallet.id,
            id = request.previewHandle.value,
        ) { resolvedOffer ->
            try {
                receiveCredentialFlowInternal(
                    wallet = wallet,
                    request = request.toReceiveCredentialRequest(resolvedOffer.source),
                    resolvedOffer = resolvedOffer,
                    attestationAssembler = attestationAssembler,
                    onEvent = onEvent,
                    httpClient = httpClient,
                    onDeferredCredential = onDeferredTarget,
                    beforeCredentialsStored = beforeCredentialsStored,
                    onCredentialStored = onCredentialStored,
                    metadataTrustResolver = null,
                ).collect(::send)
            } catch (error: CredentialIssuanceException) {
                previewedOffers.discard(wallet.id, request.previewHandle.value)
                throw error
            }
        }
    }

    private fun receiveCredentialFlowInternal(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        resolvedOffer: ResolvedIssuanceOffer?,
        attestationAssembler: ClientAttestationAssembler?,
        onEvent: suspend (WalletSessionEvent) -> Unit,
        httpClient: HttpClient,
        onDeferredCredential: suspend (DeferredCredentialTransaction) -> Unit,
        beforeCredentialsStored: suspend (Int) -> Unit,
        onCredentialStored: suspend (StoredCredential) -> Unit,
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver?,
    ): Flow<StoredCredential> = channelFlow {
        val manualAttestation = parseManualClientAttestationHeaders(request.tokenRequestHeaders)
        wallet.issuanceSessions(httpClient).ensureOpen()
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            // The previous wording claimed the wallet had no key stores, which misreports the common
            // case of a store that exists but is empty - resolveKeyMaterial also returns null when a
            // named key is absent, or when the keys present do not permit signing.
            ?: error(
                request.keyId?.let { "Wallet '${wallet.id}' has no key '$it' usable for signing" }
                    ?: "Wallet '${wallet.id}' holds no key usable for signing: none of its " +
                    "${wallet.keyStores.size} key store(s) contained a key permitting KeyUsage.SIGN, " +
                    "there is no static key, and neither an inline key nor a keyId was supplied"
            )
        val did = request.did ?: wallet.defaultDid()
        val requestMetadata = request.metadata

        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
        val tokenBuilder = TokenRequestBuilder(clientConfig, httpClient)

        // 1. Resolve the offer source, or use the exact resolution selected by its preview handle.
        val effectiveResolvedOffer = resolvedOffer ?: resolveIssuanceOffer(
            request.toResolveOfferRequest(),
            httpClient,
            metadataTrustResolver,
        )

        // 2. Reuse issuer metadata and offered configurations from that resolution.
        val preAuthGrant = requireNotNull(effectiveResolvedOffer.preAuthorizedGrant) {
            "This offer requires browser authorization"
        }
        val offer = effectiveResolvedOffer.offer
        val issuerMetadata = effectiveResolvedOffer.resolvedIssuerMetadata.metadata
        val offeredCredentials = effectiveResolvedOffer.offeredCredentials
        val asMetadata = effectiveResolvedOffer.authorizationServerMetadata
        log.trace { "Resolved offer: issuer=${offer.credentialIssuer}, configIds=${offer.credentialConfigurationIds}" }
        onEvent.emitSafely(WalletSessionEvent.issuance_offer_resolved)

        val selections = wallet.resolveCredentialSelections(request.credentials, offer.credentialConfigurationIds,
            issuerMetadata, keyMaterial, did)
        log.debug { "Offer contains ${offeredCredentials.size} credential(s)" }

        // 4. Request token
        val tokenEndpoint = asMetadata.tokenEndpoint
            ?: error("Authorization server metadata contains no token_endpoint")
        log.trace { "Requesting token from $tokenEndpoint" }

        val tokenAttestation = if (manualAttestation == null) clientAttestationForRequest(
            wallet = wallet,
            asMetadata = asMetadata,
            discoveryHeaders = effectiveResolvedOffer.authorizationServerHeaders,
            restoredDiscovery = resolvedOffer != null,
            clientId = request.clientId,
            attestationAssembler = attestationAssembler,
            resolveInstanceKey = { keyMaterial.crypto2AttestationKey() },
            onAttestationObtained = { onEvent.emitSafely(WalletSessionEvent.issuance_attestation_obtained) },
            httpClient = httpClient,
            anonymousPreAuthorizedAccessSupported = asMetadata.preAuthorizedGrantAnonymousAccessSupported == true,
        ) else null

        // private_key_jwt (RFC 7523). Engaged from authorization server metadata for the same reason
        // attestation is: the wallet cannot know out of band which method a given issuer requires.
        // Attestation wins when both are advertised, because it additionally attests the wallet
        // instance rather than only proving key control.
        val clientAssertionFactory = clientAssertionFactory(
            asMetadata = asMetadata,
            clientId = request.clientId,
            keyMaterial = keyMaterial,
        ).takeIf { tokenAttestation == null && manualAttestation == null }

        val anonymousPreAuthorizedCode =
            asMetadata.preAuthorizedGrantAnonymousAccessSupported == true &&
                    request.tokenRequestHeaders.isEmpty() &&
                    tokenAttestation == null &&
                    clientAssertionFactory == null

        // Sender constraining (RFC 9449): used when the authorization server advertises DPoP *and*
        // the wallet key can sign one of the advertised algorithms. Otherwise a plain Bearer token
        // is requested - DPoP is optional for the wallet, so an unusable key must not fail issuance.
        val dpopAlgorithms = usableDpopAlgorithms(asMetadata, keyMaterial)

        // OpenID4VCI 1.0 §6.3: only forward a tx_code when the offer's grant requested one;
        // issuers now reject an unsolicited tx_code instead of ignoring it.
        val effectiveTxCode = request.txCode?.takeIf { preAuthGrant.txCode != null }

        val tokenResponse = tokenBuilder.exchangePreAuthorizedCode(
            tokenEndpoint = tokenEndpoint,
            preAuthorizedCode = preAuthGrant.preAuthorizedCode,
            txCode = effectiveTxCode,
            additionalParameters = CredentialRequestBuilder.preAuthorizedTokenParameters(
                issuerMetadata, selections.map { it.selection.credentialConfigurationId }, asMetadata,
            ),
            additionalHeaders = request.tokenRequestHeaders,
            anonymous = anonymousPreAuthorizedCode,
            dpopProofFactory = dpopAlgorithms?.let { algorithms ->
                { endpoint: String, nonce: String? ->
                    buildDpopProof(keyMaterial, algorithms, endpoint, nonce = nonce ?: tokenAttestation?.context?.dpopNonce)
                }
            },
            clientAssertionFactory = clientAssertionFactory,
            onResponseHeaders = tokenAttestation?.onResponseHeaders ?: {},
            attestationHeadersFactory = tokenAttestation?.factory,
        )
        log.trace { "Token obtained" }
        onEvent.emitSafely(WalletSessionEvent.issuance_token_obtained)

        executeCredentialTargets(
            wallet, httpClient, issuerMetadata,
            grantedCredentialSelections(issuerMetadata, selections, tokenResponse.authorization_details, tokenResponse.scope),
            CredentialIssuanceAccess(tokenResponse.access_token, keyMaterial,
                dpopAlgorithmsForToken(tokenResponse.token_type, dpopAlgorithms),
                request.clientId.takeUnless { anonymousPreAuthorizedCode },
                walletClientId = request.clientId),
            sessions = wallet.issuanceSessions(httpClient), requestMetadata = requestMetadata, onEvent = onEvent,
            beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = { entry ->
                onCredentialStored(entry)
                onEvent.emitSafely(WalletSessionEvent.issuance_credential_stored)
                send(entry)
            },
            onDeferredCredential = onDeferredCredential,
        )

        onEvent.emitSafely(WalletSessionEvent.issuance_completed)
    }

    /**
     * Retains the released transaction-map result. Use [receiveCredentials] for detailed progress.
     * Partial failures or repeated deferred configurations throw [CredentialReceiveException] with that progress.
     */
    suspend fun receiveCredential(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): ReceiveCredentialResult = receiveCredentials(
        wallet = wallet,
        request = request,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
        metadataTrustResolver = metadataTrustResolver,
    ).let(::ReceiveCredentialResult)

    suspend fun receiveCredential(
        wallet: Wallet,
        request: ReceiveCredentialFromPreviewRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): ReceiveCredentialResult = receiveCredentials(
        wallet = wallet,
        request = request,
        attestationAssembler = attestationAssembler,
        onEvent = onEvent,
        httpClient = httpClient,
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
    ).let(::ReceiveCredentialResult)

    /** Collects stored IDs, every deferred target and any stopped-target/storage progress. */
    suspend fun receiveCredentials(
        wallet: Wallet,
        request: ReceiveCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): ReceiveCredentialsResult {
        val progress = IssuanceResultCollector(onCredentialStored)
        return progress.collect(receiveCredentialFlow(
            wallet = wallet,
            request = request,
            attestationAssembler = attestationAssembler,
            onEvent = onEvent,
            httpClient = httpClient,
            onDeferredTarget = progress::deferred,
            beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = progress::stored,
            metadataTrustResolver = metadataTrustResolver,
        ))
    }

    /** Receives credentials using exactly the offer resolution selected by [request]. */
    suspend fun receiveCredentials(
        wallet: Wallet,
        request: ReceiveCredentialFromPreviewRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): ReceiveCredentialsResult {
        val progress = IssuanceResultCollector(onCredentialStored)
        return progress.collect(receiveCredentialFlow(
            wallet = wallet,
            request = request,
            attestationAssembler = attestationAssembler,
            onEvent = onEvent,
            httpClient = httpClient,
            onDeferredTarget = progress::deferred,
            beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = progress::stored,
        ))
    }

    /**
     * Resolves an offer for review and retains the complete resolution for [receiveCredential].
     *
     * The returned opaque handle selects this exact parsed offer, issuer metadata, authorization
     * server metadata, and offered configuration set for a later reviewed receive action.
     *
     * @param wallet Wallet that will receive the reviewed offer.
     * @param request Credential offer URL or inline offer JSON to resolve.
     * @param httpClient HTTP client used for offer and metadata resolution.
     * @return Review metadata and the opaque handle required to act on it.
     */
    suspend fun previewOffer(
        wallet: Wallet,
        request: ResolveOfferRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): WalletOfferPreviewResult {
        val resolvedOffer = resolveIssuanceOffer(request, httpClient, metadataTrustResolver)
        val previewHandle = IssuancePreviewHandle(
            previewedOffers.create(walletId = wallet.id, value = resolvedOffer)
        )
        return WalletOfferPreviewResult(
            previewHandle = previewHandle,
            resolvedIssuerMetadata = resolvedOffer.resolvedIssuerMetadata,
            offeredCredentials = resolvedOffer.offeredCredentials,
            transactionCode = resolvedOffer.preAuthorizedGrant?.txCode,
        )
    }

    /** Explicitly discards a reviewed issuance preview without contacting the issuer. */
    suspend fun discardPreview(wallet: Wallet, handle: IssuancePreviewHandle) {
        previewedOffers.discard(walletId = wallet.id, id = handle.value)
    }

    /** Clears every issuance preview and tombstone owned by [wallet] during wallet deletion. */
    suspend fun clearPreviews(wallet: Wallet) {
        previewedOffers.clearWallet(wallet.id)
    }

    private suspend fun resolveIssuanceOffer(
        request: ResolveOfferRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): ResolvedIssuanceOffer {
        val offer = resolveOffer(request, httpClient)
        val metadataResolver = IssuerMetadataResolver(httpClient, metadataTrustResolver)
        val resolvedIssuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(offer.credentialIssuer)
        val issuerMetadata = resolvedIssuerMetadata.metadata
        val (grant, asResolution) = metadataResolver.resolveIssuanceGrant(offer, issuerMetadata, WalletIssuanceGrant.PRE_AUTHORIZED_CODE)
        val asMetadata = asResolution.metadata
        val preAuthorized = offer.grants?.preAuthorizedCode?.takeIf { grant == WalletIssuanceGrant.PRE_AUTHORIZED_CODE }
        val offeredCredentials = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata)
        return ResolvedIssuanceOffer(
            source = request,
            grant = grant,
            summary = ResolveOfferResult(
                credentialIssuer = offer.credentialIssuer,
                credentialConfigurationIds = offer.credentialConfigurationIds,
                grantType = when (grant) {
                    WalletIssuanceGrant.AUTHORIZATION_CODE -> "authorization_code"
                    WalletIssuanceGrant.PRE_AUTHORIZED_CODE -> "pre-authorized_code"
                },
                preAuthorizedCode = preAuthorized?.preAuthorizedCode,
                txCodeRequired = preAuthorized?.txCode != null,
                tokenEndpoint = asMetadata.tokenEndpoint?.let { Url(it) },
                credentialEndpoint = Url(issuerMetadata.credentialEndpoint),
                offeredCredentials = offeredCredentials.map { it.credentialConfigurationId },
                nonceEndpoint = issuerMetadata.nonceEndpoint?.let { Url(it) },
            ),
            offer = offer,
            resolvedIssuerMetadata = resolvedIssuerMetadata,
            authorizationServerMetadata = asMetadata,
            authorizationServerHeaders = asResolution.headers,
            offeredCredentials = offeredCredentials,
        )
    }

    // ---------------------------------------------------------------------------
    // Isolated step handlers
    // ---------------------------------------------------------------------------

    /**
     * Resolves offer metadata without retaining it for a later issuance call.
     *
     * Use [previewOffer] when user review and subsequent issuance must use the same resolution.
     *
     * @param request Credential offer URL or inline offer JSON to resolve.
     * @return Resolved offer, issuer, endpoint, credential, and transaction-code metadata.
     */
    suspend fun resolveOffer(request: ResolveOfferRequest): ResolveOfferResult =
        resolveIssuanceOffer(request).summary

    /**
     * Resolves an offer and returns the summary together with the resolved issuer and offered-credential
     * metadata, without retaining a preview handle.
     *
     * Use this for stateless "resolve for display" flows that render an issuer/credential preview and then
     * complete issuance by re-sending the offer (e.g. via the pre-authorized or authorization-code endpoints).
     *
     * @param request Credential offer URL or inline offer JSON to resolve.
     * @param httpClient HTTP client used for offer and metadata resolution.
     * @param metadataTrustResolver Optional trust boundary for signed Credential Issuer Metadata. When
     * absent, only unsigned metadata is accepted.
     * @return Resolved offer summary, issuer metadata resolution, offered credentials, and transaction code.
     */
    suspend fun resolveOfferDetailed(
        request: ResolveOfferRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    ): WalletOfferResolution {
        val resolved = resolveIssuanceOffer(request, httpClient, metadataTrustResolver)
        return WalletOfferResolution(
            summary = resolved.summary,
            resolvedIssuerMetadata = resolved.resolvedIssuerMetadata,
            offeredCredentials = resolved.offeredCredentials,
            transactionCode = resolved.preAuthorizedGrant?.txCode,
        )
    }

    suspend fun requestToken(request: RequestTokenRequest): RequestTokenResult =
        requestTokenDetailed(request).toReleasedResult()

    suspend fun requestToken(
        wallet: Wallet,
        request: RequestTokenRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onAttestationObtained: suspend () -> Unit = {},
    ): RequestTokenResult = requestTokenDetailed(wallet, request, attestationAssembler, httpClient, onAttestationObtained).toReleasedResult()

    suspend fun requestTokenDetailed(request: RequestTokenRequest): RequestTokenDetailedResult {
        parseManualClientAttestationHeaders(request.tokenRequestHeaders)
        return requestTokenDetailed(
            request = request,
            tokenAttestation = null,
            anonymousPreAuthorizedCode = request.anonymousPreAuthorizedCode,
        )
    }

    suspend fun requestTokenDetailed(
        wallet: Wallet,
        request: RequestTokenRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onAttestationObtained: suspend () -> Unit = {},
    ): RequestTokenDetailedResult {
        val manualAttestation = parseManualClientAttestationHeaders(request.tokenRequestHeaders)
        val credentialIssuer = request.credentialIssuer?.takeIf { it.isNotBlank() }
        val asResolution = credentialIssuer?.let {
            val metadataResolver = IssuerMetadataResolver(httpClient)
            val issuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(it).metadata
            issuerMetadata to metadataResolver.resolveTokenServer(issuerMetadata, request.tokenEndpoint.toString())
        }
        val asMetadata = asResolution?.second?.metadata
        val tokenAttestation = asMetadata?.takeIf { manualAttestation == null }?.let {
            clientAttestationForRequest(
                wallet = wallet,
                asMetadata = it,
                discoveryHeaders = requireNotNull(asResolution).second.headers,
                clientId = request.clientId,
                attestationAssembler = attestationAssembler,
                resolveInstanceKey = {
                    wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))?.crypto2AttestationKey()
                },
                onAttestationObtained = onAttestationObtained,
                httpClient = httpClient,
                anonymousPreAuthorizedAccessSupported = it.preAuthorizedGrantAnonymousAccessSupported == true,
            )
        }
        val anonymousPreAuthorizedCode =
            request.anonymousPreAuthorizedCode ||
                    (asMetadata?.preAuthorizedGrantAnonymousAccessSupported == true &&
                            request.tokenRequestHeaders.isEmpty() &&
                            tokenAttestation == null)

        return requestTokenDetailed(
            request = request,
            tokenAttestation = tokenAttestation,
            anonymousPreAuthorizedCode = anonymousPreAuthorizedCode,
            httpClient = httpClient,
            resolution = asResolution,
        )
    }

    private suspend fun requestTokenDetailed(
        request: RequestTokenRequest,
        tokenAttestation: RequestClientAttestation?,
        anonymousPreAuthorizedCode: Boolean,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        resolution: Pair<CredentialIssuerMetadata, ResolvedAuthorizationServerMetadata>? = null,
    ): RequestTokenDetailedResult {
        parseManualClientAttestationHeaders(request.tokenRequestHeaders)
        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )
        val authorizationParameters = request.credentialConfigurationIds?.let { configurationIds ->
            val (issuer, server) = resolution ?: IssuerMetadataResolver(httpClient).let { resolver ->
                val issuer = resolver.resolveCredentialIssuerMetadata(requireNotNull(request.credentialIssuer)).metadata
                issuer to resolver.resolveTokenServer(issuer, request.tokenEndpoint.toString())
            }
            CredentialRequestBuilder.preAuthorizedTokenParameters(issuer, configurationIds, server.metadata)
        } ?: buildMap {
            request.authorizationDetails?.let { put("authorization_details", lenientJson.encodeToString(it)) }
            request.scope?.let { put("scope", it) }
        }
        val tokenResponse = TokenRequestBuilder(clientConfig, httpClient).exchangePreAuthorizedCode(
            tokenEndpoint = request.tokenEndpoint.toString(),
            preAuthorizedCode = request.preAuthorizedCode,
            txCode = request.txCode,
            additionalParameters = authorizationParameters,
            additionalHeaders = request.tokenRequestHeaders,
            anonymous = anonymousPreAuthorizedCode,
            dpopProofFactory = null,
            clientAssertionFactory = null,
            onResponseHeaders = tokenAttestation?.onResponseHeaders ?: {},
            attestationHeadersFactory = tokenAttestation?.factory,
        )
        return RequestTokenDetailedResult(
            accessToken = tokenResponse.access_token,
            expiresIn = tokenResponse.expires_in,
            tokenType = tokenResponse.token_type,
            authorizationDetails = tokenResponse.authorization_details,
            scope = tokenResponse.scope,
        )
    }

    suspend fun requestNonce(
        request: RequestNonceRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): RequestNonceResult {
        val issuerMetadata = IssuerMetadataResolver(httpClient)
            .resolveCredentialIssuerMetadata(request.credentialIssuer.toString()).metadata
        return RequestNonceResult(
            nonce = requestProofNonce(httpClient, issuerMetadata.nonceEndpoint)
        )
    }

    /** Signs one proof, preserving the released single-proof SDK contract. */
    suspend fun signProof(
        wallet: Wallet,
        request: SignProofRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): SignProofResult = SignProofResult(
        signProofs(wallet, SignProofsRequest(
            issuerUrl = request.issuerUrl, credentialConfigurationId = request.credentialConfigurationId,
            nonce = request.nonce, key = request.key, keyId = request.keyId, did = request.did, clientId = request.clientId,
        ), httpClient).proofs.jwt!!.single()
    )

    /** Signs the accepted holders through the shared proof preflight and builder. */
    suspend fun signProofs(
        wallet: Wallet,
        request: SignProofsRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): SignProofsResult {
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            ?: error("No key available for signing proof")
        val issuerMetadata = IssuerMetadataResolver(httpClient)
            .resolveCredentialIssuerMetadata(request.issuerUrl.toString()).metadata
        val configuration = issuerMetadata.credentialConfigurationsSupported[request.credentialConfigurationId]
            ?: error(
                "Unknown credential configuration '${request.credentialConfigurationId}' " +
                        "for issuer '${issuerMetadata.credentialIssuer}'"
            )
        val bindings = wallet.resolveProofHolderBindings(request.holderBindings, issuerMetadata, configuration, keyMaterial, request.did)
        return SignProofsResult(buildProofCollection(wallet, bindings, configuration, issuerMetadata.credentialIssuer, request.nonce, request.clientId))
    }

    internal suspend fun buildProofCollection(
        wallet: Wallet,
        bindings: List<ResolvedCredentialHolderBinding>,
        configuration: id.walt.openid4vci.metadata.issuer.CredentialConfiguration,
        issuer: String,
        nonce: String?,
        clientId: String?,
        providerClientId: String? = clientId,
    ): Proofs {
        val requirements = configuration.proofTypesSupported?.get("jwt")?.keyAttestationsRequired
        val provider = if (requirements != null) requireNotNull(wallet.keyAttestationProviderFor(issuer)) {
            "No key-attestation provider is configured for this credential issuer"
        } else null
        val algorithms = requireNotNull(supportedJwtProofAlgorithms(configuration.proofTypesSupported, provider != null)) {
            "Credential configuration does not support JWT proofs"
        }
        return Proofs(jwt = bindings.map { binding ->
            buildJwtProof(JwtProofBuilder(), binding.material, issuer, nonce,
                binding.did,
                algorithms,
                clientId = clientId,
                providerClientId = providerClientId,
                keyAttestationsRequired = requirements,
                keyAttestationProvider = provider,
            ).jwt!!.single()
        })
    }

    suspend fun fetchCredential(request: FetchCredentialRequest): FetchCredentialResult =
        FetchCredentialResult(fetchCredentials(request))

    /** Fetches complete progress without projecting away deferral or storage recovery. */
    suspend fun fetchCredentials(request: FetchCredentialRequest): FetchCredentialsResult =
        fetchCredentials(request, httpClient)

    internal suspend fun fetchCredentials(
        request: FetchCredentialRequest,
        httpClient: HttpClient,
        dpop: DpopRequestContext? = null,
    ): FetchCredentialsResult {
        require(request.tokenType.equals(if (dpop == null) "Bearer" else "DPoP", ignoreCase = true)) {
            "A DPoP token requires its sender-constraining key and a wallet"
        }
        val proofCount = request.effectiveProofs?.jwt?.size ?: 0
        val issuerMetadata = if (proofCount > 1 || request.credentialIssuerBaseUrl != null) {
            val issuer = requireNotNull(request.credentialIssuerBaseUrl) {
                "credentialIssuerBaseUrl is required to validate batch support"
            }
            val metadata = IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(issuer).metadata
            require(request.credentialEndpoint.toString() == metadata.credentialEndpoint) { "Credential endpoint does not match issuer metadata" }
            require(request.credentialConfigurationId in metadata.credentialConfigurationsSupported) { "Unknown credential configuration" }
            CredentialRequestBuilder.validateBatchSize(metadata, maxOf(1, proofCount))
            metadata
        } else {
            null
        }
        val exchange = requestCredential(request, httpClient, dpop, issuerMetadata)
        val credentialResponse = exchange.response
        if (credentialResponse.transactionId != null) return FetchCredentialsResult(
            deferredCredential = DeferredCredentialTransaction(request.credentialConfigurationId,
                request.credentialIdentifier, requireNotNull(credentialResponse.transactionId),
                request.holderBindings.map { it.copy(key = null, keyId = it.keyId ?: request.keyId) },
                intervalSeconds = requireNotNull(credentialResponse.interval),
                proofRequired = request.effectiveProofs != null),
            notificationId = credentialResponse.notificationId,
        )
        val rawCredentials = credentialResponse.credentials
            ?.map { it.credential.let { c -> if (c is JsonPrimitive) c.content else c.toString() } }
            ?: error("Credential response contained no credentials")
        require(rawCredentials.isNotEmpty()) { "Credential response contained no credentials" }
        request.effectiveProofs?.let { validateIsolatedCredentialKeys(rawCredentials, it) }
        return FetchCredentialsResult(
            rawCredentials = rawCredentials,
            notificationId = credentialResponse.notificationId,
        )
    }

    /**
     * Fetches credentials and applies [FetchCredentialRequest.storeInWallet] consistently for
     * every server adapter. Use the stateless overload when no wallet is available.
     *
     * When [FetchCredentialRequest.storeInWallet] is true, pass
     * [FetchCredentialRequest.credentialIssuerBaseUrl] so issuer display metadata and labels
     * are persisted like the full receive path.
     */
    suspend fun fetchCredential(
        wallet: Wallet,
        request: FetchCredentialRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): FetchCredentialResult = FetchCredentialResult(
        fetchCredentials(wallet, request, httpClient, beforeCredentialsStored, onCredentialStored)
    )

    /** Fetches and optionally stores credentials while retaining complete continuation details. */
    suspend fun fetchCredentials(
        wallet: Wallet,
        request: FetchCredentialRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): FetchCredentialsResult {
        wallet.issuanceSessions(httpClient).ensureOpen()
        val dpop = resolveIsolatedDpop(wallet, request.tokenType, request.dpopKeyId, request.credentialIssuerBaseUrl, httpClient)
        if (!request.storeInWallet) return fetchCredentials(request, httpClient, dpop)
        val storage = resolveCredentialStorageContext(
            credentialIssuerBaseUrl = request.credentialIssuerBaseUrl,
            credentialConfigurationId = request.credentialConfigurationId,
            requestMetadata = request.metadata,
            labelOverride = request.label,
            httpClient = httpClient,
        )
        val bindings = wallet.resolveStoredCredentialBindings(
            request.holderBindings, request.keyId, proofRequired = request.effectiveProofs != null,
        )
        request.effectiveProofs?.let { validateIsolatedStorageBindings(it, bindings) }
        val result = fetchCredentials(request, httpClient, dpop)
        if (result.deferredCredential != null) return result
        val target = IssuerNotificationTarget(
            notificationEndpoint = storage.notificationEndpoint,
            notificationId = result.notificationId,
            accessToken = request.accessToken,
            tokenType = request.tokenType,
            dpopProofFactory = dpop?.toProofFactory(request.accessToken),
        )
        return try {
            val prepared = wallet.prepareIssuedCredentials(result.rawCredentials, bindings, storage.label, storage.metadata,
                proofRequired = request.effectiveProofs != null,
                holderBindingKnown = request.keyId != null || request.holderBindings.all { it.keyId != null || it.key != null },
                expectedConfiguration = storage.configuration, typeMetadataHttpClient = httpClient, credentialIssuer = request.credentialIssuerBaseUrl)
            val outcome = wallet.issuanceSessions(httpClient).storeReceivedCredentials(
                prepared, request.credentialConfigurationId, request.credentialIdentifier,
                persistable = true,
                beforeCredentialsStored = beforeCredentialsStored, onCredentialStored = onCredentialStored,
                notificationEndpoint = storage.notificationEndpoint,
                notificationId = result.notificationId,
                accessToken = request.accessToken,
                tokenType = request.tokenType,
                dpop = dpop?.algorithms,
                keyMaterial = dpop?.keyMaterial,
            )
            result.copy(storageOutcome = outcome)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            deliverCredentialNotification(
                httpClient = httpClient,
                target = target,
                event = NotificationEvent.CREDENTIAL_FAILURE,
            )
            throw error
        }
    }

    /**
     * Posts [NotificationEvent.CREDENTIAL_DELETED] for a credential fetched with
     * [FetchCredentialRequest.storeInWallet] left false. Resolves [RejectIssuedCredentialRequest.notificationEndpoint]
     * from issuer metadata when only [RejectIssuedCredentialRequest.credentialIssuerBaseUrl] is supplied.
     * Use a Bearer token. A missing advertised `notification_endpoint` is a best-effort no-op.
     */
    suspend fun rejectIssuedCredential(
        request: RejectIssuedCredentialRequest,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ) {
        val notificationEndpoint = request.notificationEndpoint?.takeIf { it.isNotBlank() }
            ?: request.credentialIssuerBaseUrl?.takeIf { it.isNotBlank() }?.let { issuer ->
                IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(issuer)
                    .metadata.notificationEndpoint
            }
        reportCredentialDeleted(
            notificationEndpoint = notificationEndpoint ?: return,
            notificationId = request.notificationId,
            accessToken = request.accessToken,
            tokenType = "Bearer",
            eventDescription = request.eventDescription,
            httpClient = httpClient,
        )
    }

    /**
     * Reports [NotificationEvent.CREDENTIAL_DELETED] for credentials the caller rejected instead of storing.
     * [tokenType] is `Bearer` or `DPoP`. A DPoP token requires [dpopProofFactory].
     */
    suspend fun reportCredentialDeleted(
        notificationEndpoint: String,
        notificationId: String,
        accessToken: String,
        tokenType: String,
        eventDescription: String? = null,
        dpopProofFactory: DPoPProofFactory? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ) {
        deliverCredentialNotification(
            httpClient = httpClient,
            notificationEndpoint = notificationEndpoint,
            notificationId = notificationId,
            accessToken = accessToken,
            tokenType = tokenType,
            event = NotificationEvent.CREDENTIAL_DELETED,
            eventDescription = eventDescription,
            dpopProofFactory = dpopProofFactory,
        )
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Perform a pushed authorization request (RFC 9126) and return the browser URL for it.
     *
     * The resulting authorization request carries **only** `client_id` and `request_uri`. Anything
     * else is a finding: RFC 9126 Section 4 and FAPI 2.0 Security Profile Section 5.3.3.2 both
     * require it, on the grounds that duplicated parameters can leak into browser history and logs.
     *
     * [keyMaterial] is what makes this possible at all - the endpoint is client authenticated. When it
     * is absent the request is still pushed, but unauthenticated, which an authorization server
     * demanding `private_key_jwt` will reject; callers that have a wallet should use the
     * [generateAuthorizationUrl] overload that takes one.
     */
    private suspend fun pushAuthorizationRequest(
        wallet: Wallet?,
        request: GenerateBatchAuthorizationUrlRequest,
        offer: CredentialOffer?,
        issuerMetadata: CredentialIssuerMetadata,
        asMetadata: AuthorizationServerMetadata,
        discoveryHeaders: io.ktor.http.Headers,
        authorizationEndpoint: String,
        authBuilder: AuthorizationRequestBuilder,
        credentialConfigurationIds: List<String>,
        scope: String?,
        parEndpoint: String,
        keyMaterial: WalletKeyStoreEntry?,
        attestationAssembler: ClientAttestationAssembler?,
        httpClient: HttpClient,
    ): GenerateBatchAuthorizationUrlResult {
        // Binds the eventual access token to this wallet's key at authorization time (RFC 9449
        // Section 10). Only offered when the key can actually sign an advertised algorithm.
        val dpopJkt = keyMaterial
            ?.takeIf { usableDpopAlgorithms(asMetadata, it) != null }
            ?.jwkThumbprint()

        val pushed = authBuilder.buildPushedAuthorizationRequestStateForCredentialConfigurations(
            credentialConfigurationIds = credentialConfigurationIds,
            issuerState = offer?.grants?.authorizationCode?.issuerState,
            usePKCE = request.usePkce,
            metadata = asMetadata,
            redirectUri = request.redirectUri.toString(),
            dpopJkt = dpopJkt,
            credentialIssuerLocations = issuerMetadata.authorizationDetailLocations(),
            scope = scope,
        )

        // A pushed authorization request authenticates the client exactly as the token request does
        // (RFC 9126 Section 2), so it needs the same credential. Under HAIP that is the only one on
        // offer: the authorization server advertises attest_jwt_client_auth alone and requires PAR, so
        // without this the flow cannot authenticate at all and the suite answers 401.
        val parAttestation = clientAttestationForRequest(
            flowId = attestationFlowId(pushed.pkceData?.codeVerifier),
            wallet = wallet,
            asMetadata = asMetadata,
            discoveryHeaders = discoveryHeaders,
            clientId = request.clientId,
            attestationAssembler = attestationAssembler,
            resolveInstanceKey = { keyMaterial?.crypto2AttestationKey() },
            httpClient = httpClient,
        )

        val response = PushedAuthorizationRequestExecutor.execute(
            httpClient = httpClient,
            parEndpoint = parEndpoint,
            parameters = pushed.parameters,
            // Mutually exclusive with the attestation headers: presenting two client credentials is
            // what "token_endpoint_auth_methods_supported: [attest_jwt_client_auth]" excludes.
            clientAssertionFactory = keyMaterial
                ?.takeIf { parAttestation == null }
                ?.let { material ->
                    clientAssertionFactory(
                        asMetadata = asMetadata,
                        clientId = request.clientId,
                        keyMaterial = material,
                    )
                },
            attestationHeadersFactory = parAttestation?.factory,
            onResponseHeaders = parAttestation?.onResponseHeaders ?: {},
        )

        return GenerateBatchAuthorizationUrlResult(
            authorizationUrl = Url(
                URLBuilder(authorizationEndpoint).apply {
                    parameters.append("client_id", request.clientId)
                    parameters.append("request_uri", response.requestUri)
                }.buildString()
            ),
            state = pushed.state,
            codeVerifier = pushed.pkceData?.codeVerifier,
            credentialConfigurationIds = credentialConfigurationIds,
            credentialIssuerBaseUrl = issuerMetadata.credentialIssuer,
            nonceEndpoint = issuerMetadata.nonceEndpoint?.let { Url(it) },
        )
    }

    private fun clientConfig(clientId: String, redirectUri: Url) =
        ClientConfiguration(clientId = clientId, redirectUris = listOf(redirectUri.toString()))

    private data class CredentialStorageContext(
        val configuration: CredentialConfiguration?,
        val label: String?,
        val metadata: JsonObject?,
        val notificationEndpoint: String? = null,
    )

    /** Resolve configuration and display metadata before an isolated fetch or poll. */
    private suspend fun resolveCredentialStorageContext(
        credentialIssuerBaseUrl: String?,
        credentialConfigurationId: String?,
        requestMetadata: JsonObject?,
        labelOverride: String?,
        httpClient: HttpClient,
    ): CredentialStorageContext {
        val issuer = credentialIssuerBaseUrl?.let {
            IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(it).metadata
        }
        val configuration = credentialConfigurationId?.let { id ->
            issuer?.let { requireNotNull(it.credentialConfigurationsSupported[id]) { "Unknown credential configuration '$id'" } }
        }
        return CredentialStorageContext(
            configuration = configuration,
            label = labelOverride ?: configuration?.credentialMetadata?.display?.firstOrNull()?.name,
            metadata = issuer?.let { storedCredentialDisplayMetadata(it, credentialConfigurationId, requestMetadata) }
                ?: requestMetadata,
            notificationEndpoint = issuer?.notificationEndpoint,
        )
    }

    /**
     * Builds a `private_key_jwt` client-assertion factory when the authorization server advertises
     * that method, or null otherwise.
     *
     * The wallet's own signing key is used as the client credential: a wallet acting as its own
     * OAuth client has no separate registered secret, and the authorization server holds the public
     * half of exactly this key from registration.
     *
     * `aud` is the authorization server's issuer identifier, which FAPI 2.0 §5.3.3.1 requires;
     * plain RFC 7523 §3 would also allow the token endpoint, but the issuer satisfies both.
     *
     * The returned factory signs a new assertion on every call so each carries a fresh `jti`.
     */
    private fun clientAssertionFactory(
        asMetadata: AuthorizationServerMetadata,
        clientId: String,
        keyMaterial: WalletKeyStoreEntry,
    ): ClientAssertionFactory? {
        val supported = asMetadata.tokenEndpointAuthMethodsSupported
            ?.contains(ClientAuthenticationMethods.PRIVATE_KEY_JWT) == true
        if (!supported) return null
        return {
            ClientAssertionBuilder().buildAssertion(
                key = keyMaterial.requireCrypto2SigningKey(),
                clientId = clientId,
                audience = asMetadata.issuer,
                supportedAlgorithms = asMetadata.tokenEndpointAuthSigningAlgValuesSupported,
            )
        }
    }

    // Correlate split PAR/token calls without persisting the PKCE verifier itself.
    private fun attestationFlowId(verifier: String?): String? = verifier?.let {
        "pkce:" + org.kotlincrypto.hash.sha2.SHA256().digest(it.encodeToByteArray()).joinToString("") { byte ->
            (byte.toInt() and 255).toString(16).padStart(2, '0')
        }
    }

    /**
     * PAR/token client attestation that can be rebuilt per HTTP attempt.
     *
     * The Wallet Attestation JWT is obtained once; each factory invocation mints a fresh PoP `jti`
     * (and honors `OAuth-Client-Attestation-Challenge` from the previous response). Static headers
     * reused the same PoP across a DPoP `use_dpop_nonce` retry, which HAIP conformance rejects.
     */
    private class RequestClientAttestation(
        val factory: ClientAttestationHeadersFactory,
        val onResponseHeaders: TokenResponseHeadersHandler,
        val context: ClientAttestationChallengeContext,
    )

    private suspend fun clientAttestationForRequest(
        wallet: Wallet?,
        asMetadata: AuthorizationServerMetadata,
        clientId: String,
        attestationAssembler: ClientAttestationAssembler?,
        resolveInstanceKey: suspend () -> Crypto2Key?,
        onAttestationObtained: suspend () -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        discoveryHeaders: io.ktor.http.Headers = io.ktor.http.Headers.Empty,
        restoredDiscovery: Boolean = false,
        flowId: String? = null,
        anonymousPreAuthorizedAccessSupported: Boolean = false,
    ): RequestClientAttestation? {
        val assembler = configuredAttestationAssembler(
            asMetadata,
            attestationAssembler,
            anonymousPreAuthorizedAccessSupported,
        ) ?: return null

        log.debug { "Issuer supports attestation-based client auth, building attestation headers" }
        val key = resolveInstanceKey()
            ?: error("No key available for client attestation")
        val context = flowId?.let { wallet?.clientAttestationChallenges(asMetadata.issuer, clientId, key, it) }
            ?: ClientAttestationChallengeContext()
        if (restoredDiscovery) context.restore(
            discoveryHeaders[CLIENT_ATTESTATION_CHALLENGE], discoveryHeaders[DPOP_NONCE_HEADER])
        else context.observe(discoveryHeaders)
        val attestationJwt = assembler.obtainAttestationJwt(key, clientId)
        onAttestationObtained()
        return RequestClientAttestation(
            factory = {
                context.prepare(httpClient, asMetadata.challengeEndpoint)
                ClientAttestationHeaders(
                    attestationJwt = attestationJwt,
                    popJwt = assembler.buildPopJwt(key, clientId, asMetadata.issuer, context.challenge),
                )
            },
            onResponseHeaders = context::observe,
            context = context,
        )
    }

    private suspend fun WalletKeyStoreEntry.crypto2AttestationKey(): Crypto2Key? =
        crypto2Key ?: legacyKey?.let { migrateLocalJwk(it) }?.let { crypto2Runtime.restore(it) }

    private fun AuthorizationServerMetadata.supportsAttestationBasedClientAuthentication(): Boolean =
        tokenEndpointAuthMethodsSupported?.contains(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH) == true

    private fun configuredAttestationAssembler(
        asMetadata: AuthorizationServerMetadata,
        attestationAssembler: ClientAttestationAssembler?,
        anonymousPreAuthorizedAccessSupported: Boolean = false,
    ): ClientAttestationAssembler? = attestationAssemblerForTokenMethods(
        asMetadata.tokenEndpointAuthMethodsSupported,
        attestationAssembler,
        anonymousPreAuthorizedAccessSupported,
    )

    private suspend fun resolveAuthorizationCodeAuthorizationServerMetadata(
        credentialIssuerBaseUrl: String,
        httpClient: HttpClient,
    ): ResolvedAuthorizationServerMetadata {
        val metadataResolver = IssuerMetadataResolver(httpClient)
        val issuerMetadata = metadataResolver.resolveCredentialIssuerMetadata(credentialIssuerBaseUrl).metadata
        return metadataResolver.resolveIssuanceGrant(null, issuerMetadata).server
    }

    // ---------------------------------------------------------------------------
    // Authorization-code grant isolated steps
    // ---------------------------------------------------------------------------

    /**
     * Step 1 of auth-code grant: resolve the offer and generate the authorization URL.
     * The caller (mobile app / browser) must then redirect to [GenerateAuthorizationUrlResult.authorizationUrl]
     * and capture the `code` from the redirect callback before calling [exchangeCode].
     */
    /**
     * Step 1 of the auth-code grant, with the wallet available so the request can be client
     * authenticated.
     *
     * Prefer this over the [wallet]-less overload: only this one can perform a pushed authorization
     * request (RFC 9126), because PAR needs a key to authenticate the client and to bind the DPoP
     * proof (`dpop_jkt`). An authorization server that advertises
     * `require_pushed_authorization_requests` rejects a plain authorization request outright.
     */
    suspend fun generateAuthorizationUrl(
        wallet: Wallet,
        request: GenerateAuthorizationUrlRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): GenerateAuthorizationUrlResult = generateAuthorizationUrl(
        wallet = wallet,
        request = request,
        keyMaterial = wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN)),
        attestationAssembler = attestationAssembler,
        httpClient = httpClient,
    )

    suspend fun generateAuthorizationUrl(request: GenerateAuthorizationUrlRequest): GenerateAuthorizationUrlResult =
        generateAuthorizationUrl(request = request, keyMaterial = null)

    /** Authorizes selected configurations, negotiating scopes or authorization details from metadata. */
    suspend fun generateBatchAuthorizationUrl(
        wallet: Wallet,
        request: GenerateBatchAuthorizationUrlRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): GenerateBatchAuthorizationUrlResult = generateAuthorizationUrl(
        wallet = wallet,
        request = request,
        keyMaterial = wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN)),
        attestationAssembler = attestationAssembler,
        httpClient = httpClient,
    )

    suspend fun generateBatchAuthorizationUrl(request: GenerateBatchAuthorizationUrlRequest): GenerateBatchAuthorizationUrlResult =
        generateAuthorizationUrl(request = request, keyMaterial = null)

    private suspend fun generateAuthorizationUrl(
        request: GenerateAuthorizationUrlRequest,
        keyMaterial: WalletKeyStoreEntry?,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        wallet: Wallet? = null,
    ): GenerateAuthorizationUrlResult {
        val result = generateAuthorizationUrl(
            wallet = wallet,
            request = GenerateBatchAuthorizationUrlRequest(offerUrl = request.offerUrl, offerJson = request.offerJson,
                clientId = request.clientId, redirectUri = request.redirectUri, usePkce = request.usePkce),
            keyMaterial = keyMaterial, attestationAssembler = attestationAssembler, httpClient = httpClient,
            selection = AuthorizationSelection.ReleasedSingle(request.useScope),
        )
        return GenerateAuthorizationUrlResult(result.authorizationUrl, result.state, result.codeVerifier,
            result.credentialConfigurationIds.single(), result.credentialIssuerBaseUrl, result.nonceEndpoint)
    }

    private sealed interface AuthorizationSelection {
        data object Negotiated : AuthorizationSelection
        data class ReleasedSingle(val useScope: Boolean) : AuthorizationSelection
    }

    private suspend fun generateAuthorizationUrl(
        request: GenerateBatchAuthorizationUrlRequest,
        keyMaterial: WalletKeyStoreEntry?,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        selection: AuthorizationSelection = AuthorizationSelection.Negotiated,
        wallet: Wallet? = null,
    ): GenerateBatchAuthorizationUrlResult {
        val offer = if (request.credentialIssuer == null) resolveOffer(request, httpClient) else null
        val resolver = IssuerMetadataResolver(httpClient)
        val issuerMetadata = resolver.resolveCredentialIssuerMetadata(request.credentialIssuer ?: requireNotNull(offer).credentialIssuer).metadata
        val asResolution = resolver.resolveIssuanceGrant(offer, issuerMetadata, required = WalletIssuanceGrant.AUTHORIZATION_CODE).server
        if (offer?.grants?.authorizationCode?.authorizationServer != null) {
            require(asResolution.metadata.issuer == resolver.resolveIssuanceGrant(null, issuerMetadata).server.metadata.issuer) {
                "Use a retained issuance session for an offer-specific browser authorization server"
            }
        }
        val asMetadata = asResolution.metadata

        val authorizationEndpoint = asMetadata.authorizationEndpoint
            ?: error("Authorization server has no authorization_endpoint")

        val clientConfig = clientConfig(request.clientId, request.redirectUri)
        val authBuilder = AuthorizationRequestBuilder(clientConfig)
        val credentialConfigurationIds = when (selection) {
            is AuthorizationSelection.ReleasedSingle -> listOf(requireNotNull(offer).credentialConfigurationIds.first())
            AuthorizationSelection.Negotiated -> request.credentialConfigurationIds ?: requireNotNull(offer).credentialConfigurationIds
        }
        require(credentialConfigurationIds.isNotEmpty() && credentialConfigurationIds.distinct().size == credentialConfigurationIds.size)
        credentialConfigurationIds.forEach {
            require(it in issuerMetadata.credentialConfigurationsSupported && (offer == null || it in offer.credentialConfigurationIds)) {
                "Unknown or unoffered credential configuration '$it'"
            }
        }

        val scope = when (selection) {
            is AuthorizationSelection.ReleasedSingle -> if (selection.useScope) {
                issuerMetadata.requireCredentialScope(credentialConfigurationIds.single())
            } else null
            AuthorizationSelection.Negotiated -> CredentialRequestBuilder.authorizationScope(
                issuerMetadata, credentialConfigurationIds, asMetadata)
        }

        // Engaged only when the authorization server *requires* PAR, not merely advertises an
        // endpoint. RFC 9126 makes PAR optional for the client, and pushing to an endpoint that does
        // not expect this client's authentication fails the whole flow - an issuer that advertises the
        // endpoint but does not require it answered our pushed request with HTTP 401 and broke
        // authorization-code issuance that previously worked. Same failure mode as engaging DPoP on
        // advertisement alone.
        val parEndpoint = asMetadata.pushedAuthorizationRequestEndpoint
            ?.takeIf { asMetadata.requirePushedAuthorizationRequests == true }
        require(asMetadata.requirePushedAuthorizationRequests != true || parEndpoint != null) {
            "Authorization server requires PAR but advertises no pushed_authorization_request_endpoint"
        }
        if (parEndpoint != null) {
            return pushAuthorizationRequest(
                wallet = wallet,
                request = request,
                offer = offer,
                issuerMetadata = issuerMetadata,
                asMetadata = asMetadata,
                discoveryHeaders = asResolution.headers,
                authorizationEndpoint = authorizationEndpoint,
                authBuilder = authBuilder,
                credentialConfigurationIds = credentialConfigurationIds,
                scope = scope,
                parEndpoint = parEndpoint,
                keyMaterial = keyMaterial,
                attestationAssembler = attestationAssembler,
                httpClient = httpClient,
            )
        }

        val authRequest = authBuilder.buildAuthorizationRequestForCredentialConfigurations(
            authorizationEndpoint = authorizationEndpoint,
            credentialConfigurationIds = credentialConfigurationIds,
            issuerState = offer?.grants?.authorizationCode?.issuerState,
            credentialIssuerLocations = issuerMetadata.authorizationDetailLocations(),
            usePKCE = request.usePkce,
            metadata = asMetadata,
            scope = scope,
        )
        return GenerateBatchAuthorizationUrlResult(
            authorizationUrl = Url(authRequest.url),
            state = authRequest.state,
            codeVerifier = authRequest.pkceData?.codeVerifier,
            credentialConfigurationIds = credentialConfigurationIds,
            credentialIssuerBaseUrl = issuerMetadata.credentialIssuer,
            nonceEndpoint = issuerMetadata.nonceEndpoint?.let { Url(it) },
        )
    }

    /**
     * Step 2 of auth-code grant: exchange the authorization code for a token.
     * Wraps [TokenRequestBuilder.exchangeAuthorizationCode].
     */
    suspend fun exchangeCode(request: ExchangeCodeRequest): RequestTokenResult =
        exchangeCodeDetailed(request).toReleasedResult()

    suspend fun exchangeCode(
        wallet: Wallet,
        request: ExchangeCodeRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onAttestationObtained: suspend () -> Unit = {},
        keyMaterial: WalletKeyStoreEntry? = null,
        useDpop: Boolean = false,
    ): RequestTokenResult = exchangeCodeDetailed(
        wallet, request, attestationAssembler, httpClient, onAttestationObtained, keyMaterial, useDpop,
    ).toReleasedResult()

    suspend fun exchangeCodeDetailed(request: ExchangeCodeRequest): RequestTokenDetailedResult {
        parseManualClientAttestationHeaders(request.tokenRequestHeaders)
        val httpClient = httpClient
        val credentialIssuerBaseUrl = request.credentialIssuerBaseUrl.takeIf { it.isNotBlank() }
            ?: error("credentialIssuerBaseUrl must be provided")
        val asMetadata = resolveAuthorizationCodeAuthorizationServerMetadata(credentialIssuerBaseUrl, httpClient).metadata
        return exchangeCodeDetailed(
            request = request,
            tokenEndpoint = asMetadata.tokenEndpoint
                ?: error("Authorization server metadata contains no token_endpoint"),
            tokenAttestation = null,
            httpClient = httpClient,
        )
    }

    suspend fun exchangeCodeDetailed(
        wallet: Wallet,
        request: ExchangeCodeRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        onAttestationObtained: suspend () -> Unit = {},
        /**
         * Key to client authenticate and sender constrain this exchange with, when the caller already
         * resolved one.
         *
         * Reuse this key for subsequent DPoP proofs: the access token is bound to its `jkt`
         * (RFC 9449 Section 6). Credential holder keys are selected independently.
         */
        keyMaterial: WalletKeyStoreEntry? = null,
        /** See [ReceiveAuthorizedCredentialRequest.useDpop]. Client authentication is unaffected. */
        useDpop: Boolean = false,
    ): RequestTokenDetailedResult {
        val manualAttestation = parseManualClientAttestationHeaders(request.tokenRequestHeaders)
        val credentialIssuerBaseUrl = request.credentialIssuerBaseUrl.takeIf { it.isNotBlank() }
            ?: error("credentialIssuerBaseUrl must be provided")
        val asResolution = resolveAuthorizationCodeAuthorizationServerMetadata(credentialIssuerBaseUrl, httpClient)
        val asMetadata = asResolution.metadata
        val tokenEndpoint = asMetadata.tokenEndpoint
            ?: error("Authorization server metadata contains no token_endpoint")
        val resolvedKeyMaterial = keyMaterial ?: if (manualAttestation == null || useDpop) {
            wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))
        } else null
        val tokenAttestation = if (manualAttestation == null) clientAttestationForRequest(
            flowId = attestationFlowId(request.codeVerifier),
            wallet = wallet,
            asMetadata = asMetadata,
            discoveryHeaders = asResolution.headers,
            clientId = request.clientId,
            attestationAssembler = attestationAssembler,
            resolveInstanceKey = {
                resolvedKeyMaterial?.crypto2AttestationKey()
                    ?: wallet.resolveKeyMaterial(null, setOf(KeyUsage.SIGN))?.crypto2AttestationKey()
            },
            onAttestationObtained = onAttestationObtained,
            httpClient = httpClient,
        ) else null
        val tokenResult = exchangeCodeDetailed(
            request = request,
            tokenEndpoint = tokenEndpoint,
            tokenAttestation = tokenAttestation,
            httpClient = httpClient,
            asMetadata = asMetadata,
            keyMaterial = resolvedKeyMaterial,
            useDpop = useDpop,
        )
        try {
            attestationFlowId(request.codeVerifier)?.let { wallet.removeClientAttestationFlow(it) }
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Exception) {
            log.warn { "Could not remove completed attestation flow; expiry will clean it up" }
        }
        return tokenResult
    }

    private suspend fun exchangeCodeDetailed(
        request: ExchangeCodeRequest,
        tokenEndpoint: String,
        tokenAttestation: RequestClientAttestation?,
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /**
         * Authorization server metadata and wallet key, needed to client authenticate and sender
         * constrain the token request. Both optional so the key-less overload still works, but a
         * caller with a wallet should always supply them - see [exchangeCode].
         */
        asMetadata: AuthorizationServerMetadata? = null,
        keyMaterial: WalletKeyStoreEntry? = null,
        useDpop: Boolean = false,
    ): RequestTokenDetailedResult {
        val manualAttestation = parseManualClientAttestationHeaders(request.tokenRequestHeaders)
        val clientConfig = ClientConfiguration(
            clientId = request.clientId,
            redirectUris = listOf(request.redirectUri.toString())
        )

        // Mirrors the pre-authorized-code exchange. Attestation wins over private_key_jwt when both
        // are advertised, because it additionally attests the wallet instance rather than only proving
        // key control.
        val clientAssertionFactory = if (manualAttestation == null && tokenAttestation == null && asMetadata != null && keyMaterial != null) {
            clientAssertionFactory(asMetadata = asMetadata, clientId = request.clientId, keyMaterial = keyMaterial)
        } else {
            null
        }

        // Sender constraining (RFC 9449), engaged only when the key can sign an advertised algorithm;
        // DPoP is optional for the wallet, so an unusable key must fall back to Bearer, not fail.
        val senderConstraining = if (useDpop && asMetadata != null && keyMaterial != null) {
            usableDpopAlgorithms(asMetadata, keyMaterial)?.let { algorithms -> keyMaterial to algorithms }
        } else {
            null
        }

        val tokenResponse = TokenRequestBuilder(clientConfig, httpClient).exchangeAuthorizationCode(
            tokenEndpoint = tokenEndpoint,
            code = request.code,
            codeVerifier = request.codeVerifier,
            additionalHeaders = request.tokenRequestHeaders,
            dpopProofFactory = senderConstraining?.let { (key, algorithms) ->
                { endpoint: String, nonce: String? ->
                    buildDpopProof(key, algorithms, endpoint, nonce = nonce ?: tokenAttestation?.context?.dpopNonce)
                }
            },
            clientAssertionFactory = clientAssertionFactory,
            onResponseHeaders = tokenAttestation?.onResponseHeaders ?: {},
            attestationHeadersFactory = tokenAttestation?.factory,
        )
        return RequestTokenDetailedResult(
            accessToken = tokenResponse.access_token,
            expiresIn = tokenResponse.expires_in,
            tokenType = tokenResponse.token_type,
            authorizationDetails = tokenResponse.authorization_details,
            scope = tokenResponse.scope,
        )
    }

    // ---------------------------------------------------------------------------
    // Deferred issuance polling
    // ---------------------------------------------------------------------------

    /**
     * Polls the deferred credential endpoint for a previously deferred credential.
     *
     * Per OpenID4VCI §9, the wallet sends a Deferred Credential Request containing the
     * transaction_id. Encryption is negotiated for that request from current issuer metadata:
     * when the wallet profile is advertised, the request is an encrypted JWE and carries its own
     * `credential_response_encryption`. The issuer responds with the credential when ready,
     * or HTTP 202 while pending. Draft-15 `issuance_pending` responses remain JSON.
     *
     * On success the credential is stored in the wallet's credential store.
     */
    fun pollDeferredFlow(
        wallet: Wallet,
        request: PollDeferredRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): Flow<StoredCredential> = pollDeferredFlow(
        wallet = wallet,
        request = request,
        onEvent = onEvent,
        httpClient = httpClient,
        beforeCredentialsStored = beforeCredentialsStored,
        onCredentialStored = onCredentialStored,
        onPending = {},
    )

    fun pollDeferredFlow(
        wallet: Wallet,
        request: PollDeferredRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        onPending: suspend (DeferredCredentialPending) -> Unit,
    ): Flow<StoredCredential> = channelFlow {
        wallet.issuanceSessions(httpClient).ensureOpen()
        val bindings = wallet.resolveStoredCredentialBindings(
            request.holderBindings, request.keyId, proofRequired = request.proofRequired || request.holderBindings.size > 1,
        )
        val dpop = resolveIsolatedDpop(wallet, request.tokenType, request.dpopKeyId,
            request.credentialIssuerBaseUrl, httpClient)
        val storage = resolveCredentialStorageContext(
            credentialIssuerBaseUrl = request.credentialIssuerBaseUrl,
            credentialConfigurationId = request.credentialConfigurationId,
            requestMetadata = request.metadata,
            labelOverride = request.label,
            httpClient = httpClient,
        )
        val deferredRequest = buildJsonObject { put("transaction_id", request.transactionId) }
        val issuerMetadata = request.credentialIssuerBaseUrl?.let {
            IssuerMetadataResolver(httpClient).resolveCredentialIssuerMetadata(it).metadata
        }
        val preparedRequest = CredentialEncryption.prepare(deferredRequest, issuerMetadata)
        val credentialResponse = httpClient.requestCredentialResponse(
            endpoint = request.deferredCredentialEndpoint.toString(),
            accessToken = request.accessToken,
            prepared = preparedRequest,
            dpop = dpop,
            expectedTransactionId = request.transactionId,
        )
        if (credentialResponse.transactionId != null) {
            onPending(DeferredCredentialPending(request.transactionId, requireNotNull(credentialResponse.interval)))
            onEvent.emitSafely(WalletSessionEvent.issuance_deferred)
            return@channelFlow
        }
        val rawCredentials = credentialResponse.credentials
            ?: error("Deferred credential response contained no credentials")

        val notificationTarget = IssuerNotificationTarget(
            notificationEndpoint = storage.notificationEndpoint,
            notificationId = credentialResponse.notificationId,
            accessToken = request.accessToken,
            tokenType = request.tokenType,
            dpopProofFactory = dpop?.toProofFactory(request.accessToken),
        )
        val prepared = try {
            wallet.prepareIssuedCredentials(rawCredentials.map {
                val value = it.credential
                if (value is JsonPrimitive) value.content else value.toString()
            }, bindings, storage.label, storage.metadata, proofRequired = request.proofRequired || request.holderBindings.size > 1,
                holderBindingKnown = request.keyId != null || request.holderBindings.all { it.keyId != null || it.key != null },
                expectedConfiguration = storage.configuration, typeMetadataHttpClient = httpClient, credentialIssuer = request.credentialIssuerBaseUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            deliverCredentialNotification(
                httpClient = httpClient,
                target = notificationTarget,
                event = NotificationEvent.CREDENTIAL_FAILURE,
            )
            throw error
        }
        val outcome = wallet.issuanceSessions(httpClient).storeReceivedCredentials(
            prepared, request.credentialConfigurationId, credentialIdentifier = request.credentialIdentifier,
            persistable = true,
            beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = { entry ->
                onCredentialStored(entry)
                onEvent.emitSafely(WalletSessionEvent.issuance_credential_stored)
                send(entry)
            },
            notificationEndpoint = storage.notificationEndpoint,
            notificationId = credentialResponse.notificationId,
            accessToken = request.accessToken,
            tokenType = request.tokenType,
            dpop = dpop?.algorithms,
            keyMaterial = dpop?.keyMaterial,
        )
        if (outcome is WalletIssuanceOutcome.Failed) throw CredentialStorageException(outcome)
        onEvent.emitSafely(WalletSessionEvent.issuance_completed)
    }

    suspend fun pollDeferred(
        wallet: Wallet,
        request: PollDeferredRequest,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): PollDeferredResult {
        val ids = mutableListOf<String>()
        var pending: DeferredCredentialPending? = null
        return try {
            pollDeferredFlow(wallet, request, onEvent, httpClient, beforeCredentialsStored,
                onCredentialStored = { ids += it.id; onCredentialStored(it) },
                onPending = { pending = it }).collect { }
            PollDeferredResult(ids, pending)
        } catch (error: CredentialStorageException) {
            PollDeferredResult(error.outcome.storedCredentialIds, storageOutcome = error.outcome)
        }
    }

    private suspend fun resolveIsolatedDpop(
        wallet: Wallet,
        tokenType: String,
        keyId: String?,
        credentialIssuer: String?,
        httpClient: HttpClient,
    ): DpopRequestContext? {
        if (tokenType.equals("Bearer", ignoreCase = true)) return null
        require(tokenType.equals("DPoP", ignoreCase = true)) { "Unsupported access token type" }
        val material = requireNotNull(wallet.resolveKeyMaterial(requireNotNull(keyId) {
            "dpopKeyId is required for a DPoP access token"
        }, setOf(KeyUsage.SIGN))) { "Sender-constraining key is unavailable" }
        val resolver = IssuerMetadataResolver(httpClient)
        val issuer = resolver.resolveCredentialIssuerMetadata(requireNotNull(credentialIssuer) {
            "credentialIssuerBaseUrl is required for a DPoP access token"
        }).metadata
        val metadata = resolver.resolveDeclaredServer(issuer.authorizationServerIssuers().singleOrNull()
            ?: error("Use a retained issuance session for a DPoP token with multiple authorization servers"))
        return DpopRequestContext(requireNotNull(dpopAlgorithmsForToken(tokenType,
            usableDpopAlgorithms(metadata.metadata, material))), material)
    }

    // ---------------------------------------------------------------------------
    // Auth-code grant full flow
    // ---------------------------------------------------------------------------

    /**
     * Full authorization-code grant issuance flow.
     *
     * This flow requires user interaction (browser redirect) between steps 2 and 3,
     * so it cannot be a single blocking call. Instead it is split into:
     *   1. [generateAuthorizationUrl] — get the URL to redirect the user to
     *   2. (caller handles browser redirect and captures the `code` callback)
     *   3. [receiveCredentialAuthCodeFlow] - exchange code + issue credentials
     *
     * This function handles step 3 only, continuing from an authorization code.
     *
     * Key selection: [key] (explicit override) else [keyReference] via
     * `wallet.resolveKeyMaterial(keyReference, SIGN)`, else the wallet default signing key.
     * Pass store-backed key ids (e.g. Enterprise `keyReference.path`) as [keyReference] so
     * crypto2-only backends remain usable; do not convert referenced keys into [DirectSerializedKey].
     */
    fun receiveCredentialAuthCodeFlow(
        wallet: Wallet,
        code: String,
        codeVerifier: String?,
        credentialIssuerBaseUrl: String,
        credentialEndpoint: Url,
        credentialConfigurationId: String,
        nonceEndpoint: String? = null,
        clientId: String = DEFAULT_CLIENT_ID,
        redirectUri: Url = Url("openid://"),
        /** Inline key for proof-of-possession; takes precedence over [keyReference] and wallet stores. */
        key: DirectSerializedKey? = null,
        /** Store key id for proof-of-possession; ignored when [key] is provided. */
        keyReference: String? = null,
        /** Inline DID for holder binding; defaults to the wallet's default DID. */
        did: String? = null,
        /** Optional sidecar metadata merged with resolved issuer display when storing. */
        metadata: JsonObject? = null,
        /**
         * Sender constrain the token and credential requests with DPoP (RFC 9449); see
         * [ReceiveAuthorizedCredentialRequest.useDpop] for why this is opt-in rather than derived from
         * the authorization server's advertised algorithms.
         */
        useDpop: Boolean = false,
        /** Optional credential label override; otherwise derived from credential configuration display. */
        label: String? = null,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        /** Called with the exact response batch size before any credential of that batch is persisted. */
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): Flow<StoredCredential> = receiveCredentialsAuthCodeFlow(
        wallet = wallet,
        request = ReceiveAuthorizedCredentialsRequest(
            credentials = listOf(WalletCredentialSelection(credentialConfigurationId)), code = code,
            codeVerifier = codeVerifier, credentialIssuer = credentialIssuerBaseUrl, credentialEndpoint = credentialEndpoint,
            nonceEndpoint = nonceEndpoint?.let(::Url), clientId = clientId, redirectUri = redirectUri, useDpop = useDpop,
            key = key, keyId = keyReference, did = did, metadata = metadata, label = label,
        ),
        attestationAssembler = attestationAssembler, onEvent = onEvent, httpClient = httpClient,
        beforeCredentialsStored = beforeCredentialsStored, onCredentialStored = onCredentialStored,
    )

    /** Streams stored credentials for explicit accepted targets through the shared issuance executor. */
    fun receiveCredentialsAuthCodeFlow(
        wallet: Wallet,
        request: ReceiveAuthorizedCredentialsRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
        onDeferredCredential: suspend (DeferredCredentialTransaction) -> Unit = {},
    ): Flow<StoredCredential> = channelFlow {
        wallet.issuanceSessions(httpClient).ensureOpen()
        val keyMaterial = request.key?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(request.keyId, setOf(KeyUsage.SIGN))
            ?: error("No key available for proof-of-possession")
        val holderDid = request.did ?: wallet.defaultDid()

        val issuerMetadata = IssuerMetadataResolver(httpClient)
            .resolveCredentialIssuerMetadata(request.credentialIssuer).metadata
        require(request.credentialEndpoint.toString() == issuerMetadata.credentialEndpoint) { "Credential endpoint does not match issuer metadata" }
        request.nonceEndpoint?.toString()?.let { require(it == issuerMetadata.nonceEndpoint) { "Nonce endpoint does not match issuer metadata" } }
        val selections = wallet.resolveCredentialSelections(
            request.credentials,
            issuerMetadata.credentialConfigurationsSupported.keys.toList(), issuerMetadata, keyMaterial, holderDid)

        // Exchange code for token
        val exchangeRequest = ExchangeCodeRequest(
            code = request.code,
            codeVerifier = request.codeVerifier,
            clientId = request.clientId,
            redirectUri = request.redirectUri,
            credentialIssuerBaseUrl = request.credentialIssuer,
        )
        val tokenResult = exchangeCodeDetailed(
            wallet = wallet,
            request = exchangeRequest,
            attestationAssembler = attestationAssembler,
            httpClient = httpClient,
            onAttestationObtained = { onEvent.emitSafely(WalletSessionEvent.issuance_attestation_obtained) },
            keyMaterial = keyMaterial,
            useDpop = request.useDpop,
        )
        onEvent.emitSafely(WalletSessionEvent.issuance_token_obtained)

        val dpop = if (!request.useDpop) null else dpopAlgorithmsForToken(
            tokenResult.tokenType ?: "Bearer",
            usableDpopAlgorithms(resolveAuthorizationCodeAuthorizationServerMetadata(request.credentialIssuer, httpClient).metadata, keyMaterial),
        )?.let { DpopRequestContext(it, keyMaterial) }
        executeCredentialTargets(
            wallet, httpClient, issuerMetadata,
            grantedCredentialSelections(issuerMetadata, selections, tokenResult.authorizationDetails, tokenResult.scope),
            CredentialIssuanceAccess(tokenResult.accessToken, keyMaterial, dpop?.algorithms, request.clientId),
            sessions = wallet.issuanceSessions(httpClient), requestMetadata = request.metadata,
            labelFor = { request.label ?: issuerMetadata.credentialConfigurationsSupported.getValue(it).credentialMetadata?.display?.firstOrNull()?.name },
            onEvent = onEvent, beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = { entry ->
                onCredentialStored(entry)
                onEvent.emitSafely(WalletSessionEvent.issuance_credential_stored)
                send(entry)
            },
            onDeferredCredential = onDeferredCredential,
        )
        onEvent.emitSafely(WalletSessionEvent.issuance_completed)
    }

    /**
     * Retains the released authorization-code result. Use [receiveCredentialsAuthCode] for detailed progress.
     * Partial failures or repeated deferred configurations throw [CredentialReceiveException] with that progress.
     */
    suspend fun receiveCredentialAuthCode(
        wallet: Wallet,
        request: ReceiveAuthorizedCredentialRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
    ): ReceiveCredentialResult = receiveCredentialsAuthCode(
        wallet = wallet,
        request = ReceiveAuthorizedCredentialsRequest(
            credentials = listOf(WalletCredentialSelection(request.credentialConfigurationId)), code = request.code,
            codeVerifier = request.codeVerifier, credentialIssuer = request.credentialIssuer,
            credentialEndpoint = request.credentialEndpoint, nonceEndpoint = request.nonceEndpoint,
            clientId = request.clientId, redirectUri = request.redirectUri, useDpop = request.useDpop,
            key = request.key, keyId = request.keyId, did = request.did, metadata = request.metadata, label = request.label,
        ),
        attestationAssembler = attestationAssembler, onEvent = onEvent, httpClient = httpClient,
    ).let(::ReceiveCredentialResult)

    suspend fun receiveCredentialsAuthCode(
        wallet: Wallet,
        request: ReceiveAuthorizedCredentialsRequest,
        attestationAssembler: ClientAttestationAssembler? = null,
        onEvent: suspend (WalletSessionEvent) -> Unit = {},
        httpClient: HttpClient = WalletIssuanceHandler.httpClient,
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): ReceiveCredentialsResult {
        val progress = IssuanceResultCollector(onCredentialStored)
        return progress.collect(receiveCredentialsAuthCodeFlow(
            wallet = wallet, request = request, attestationAssembler = attestationAssembler,
            onEvent = onEvent, httpClient = httpClient, beforeCredentialsStored = beforeCredentialsStored,
            onCredentialStored = progress::stored, onDeferredCredential = progress::deferred,
        ))
    }

    /**
     * Builds a JWT proof through the proof-builder contracts - no proof assembly happens here.
     *
     * [nonce] is null whenever the Credential Issuer advertises no Nonce Endpoint; the builder then
     * omits the `nonce` claim. The legacy branch is only reached for keys that cannot be represented
     * in crypto2 (remote v1 KMS keys, secp256k1) and goes away with the legacy key API.
     */
    internal suspend fun buildJwtProof(
        proofBuilder: JwtProofBuilder,
        keyMaterial: WalletKeyStoreEntry,
        audience: String,
        nonce: String?,
        did: String?,
        acceptedAlgorithms: Set<String>? = null,
        clientId: String? = null,
        providerClientId: String? = clientId,
        keyAttestationsRequired: KeyAttestationsRequired? = null,
        keyAttestationProvider: KeyAttestationProvider? = null,
    ): Proofs {
        val binding = did
            ?.let { ProofKeyBinding.KeyId(if ('#' in it) it else DidService.resolveAuthenticationMethodId(it, keyMaterial.keyId)) }
            ?: ProofKeyBinding.Jwk
        val effectiveCrypto2Key = keyMaterial.crypto2Key
            ?: keyMaterial.legacyKey?.let { migrateLocalJwk(it) }?.let { crypto2Runtime.restore(it) }
        return effectiveCrypto2Key?.let {
            val keyAttestation = keyAttestationForProof(
                keyAttestationProvider, keyAttestationsRequired, it, audience, nonce, acceptedAlgorithms,
                providerClientId ?: clientId,
            )
            proofBuilder.buildProof(
                key = it,
                algorithm = it.selectJwsAlgorithm(acceptedAlgorithms),
                audience = audience,
                nonce = nonce,
                binding = binding,
                clientId = clientId,
                keyAttestation = keyAttestation,
            )
        } ?: run {
            require(keyAttestationsRequired == null) {
                "A key-attested JWT proof requires a crypto2 signing key"
            }
            val legacyKey = requireNotNull(keyMaterial.legacyKey) {
                "Key '${keyMaterial.keyId}' has no usable signing representation"
            }
            acceptedAlgorithms?.let {
                require(legacyKey.keyType.jwsAlg in it) {
                    "Issuer does not support proof algorithm ${legacyKey.keyType.jwsAlg}"
                }
            }
            proofBuilder.buildProof(
                key = legacyKey,
                audience = audience,
                nonce = nonce,
                binding = binding,
                clientId = clientId,
            )
        }
    }
}

internal fun supportedJwtProofAlgorithms(
    proofTypes: Map<String, ProofTypeMetadata>?,
    keyAttestationProviderAvailable: Boolean = false,
): Set<String>? {
    if (proofTypes.isNullOrEmpty()) return null
    val jwt = requireNotNull(proofTypes["jwt"]) {
        "Issuer requires an unsupported proof type: ${proofTypes.keys}"
    }
    require(jwt.keyAttestationsRequired == null || keyAttestationProviderAvailable) {
        "Issuer requires a key-attestation JWT; the configured proof path cannot supply one"
    }
    return jwt.proofSigningAlgValuesSupported
}

private class IssuanceResultCollector(private val onStored: suspend (StoredCredential) -> Unit) {
    private val ids = mutableListOf<String>()
    private val pending = mutableListOf<DeferredCredentialTransaction>()

    suspend fun stored(credential: StoredCredential) {
        ids += credential.id
        onStored(credential)
    }

    fun deferred(target: DeferredCredentialTransaction) { pending += target }

    suspend fun collect(flow: Flow<StoredCredential>): ReceiveCredentialsResult {
        val failure = try {
            flow.collect { }
            null
        } catch (error: CredentialIssuanceException) {
            error
        }
        return ReceiveCredentialsResult(ids.toList(), pending.toList(), failure?.failure, failure?.storageOutcome)
    }
}

/** Lifecycle events are notifications; cancellation still belongs to the caller. */
internal suspend fun (suspend (WalletSessionEvent) -> Unit).emitSafely(event: WalletSessionEvent) {
    try {
        this(event)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // The retained engine applies the same observer isolation policy.
    }
}
