package id.walt.walletdemo.compose.logic.walletapi2

import id.walt.walletdemo.compose.logic.WalletDemoCredentialHolders
import id.walt.walletdemo.compose.logic.WalletDemoCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoHolderBinding
import id.walt.walletdemo.compose.logic.WalletDemoDeferredCredential
import id.walt.walletdemo.compose.logic.DemoWallet
import id.walt.walletdemo.compose.logic.WalletDemoBootstrapResult
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceAuthorization
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceGrant
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceOutcome
import id.walt.walletdemo.compose.logic.WalletDemoIssuanceSession
import id.walt.walletdemo.compose.logic.WalletDemoIssuerMetadata
import id.walt.walletdemo.compose.logic.WalletDemoOfferPreview
import id.walt.walletdemo.compose.logic.WalletDemoOperationResult
import id.walt.walletdemo.compose.logic.WalletDemoPresentationCredentialSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationDisclosureSelection
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreviewHandle
import id.walt.walletdemo.compose.logic.WalletDemoPresentationPreviewResult
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtection
import id.walt.walletdemo.compose.logic.WalletDemoSigningProtectionAvailability
import id.walt.walletdemo.compose.logic.WalletDisplayText
import kotlin.random.Random
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

fun createWalletApi2DemoWallet(
    baseUrl: String,
    token: String,
    walletId: String,
    redirectUri: String,
    kind: WalletApiKind = WalletApiKind.OpenSource,
    onWalletIdChanged: (String) -> Unit = {},
): DemoWallet = WalletApi2DemoWallet(
    client = WalletApi2Client(baseUrl = baseUrl, token = token, kind = kind),
    kind = kind,
    walletId = walletId,
    redirectUri = redirectUri,
    onWalletIdChanged = onWalletIdChanged,
)

internal class WalletApi2DemoWallet(
    private val client: WalletApi2Client,
    private val kind: WalletApiKind,
    private var walletId: String,
    private val redirectUri: String,
    private val onWalletIdChanged: (String) -> Unit,
) : DemoWallet {
    private val issuanceSessions = mutableMapOf<String, Api2IssuanceSession>()
    private val processingIssuanceSessions = mutableSetOf<String>()
    private val presentationSessions = mutableMapOf<String, Api2PresentationSession>()
    private var activeOperations = 0
    private var resetStage: WalletResetStage? = null
    private var resetInProgress = false
    private var keyId: String? = null
    private var did: String? = null

    override suspend fun bootstrap(signingProtection: WalletDemoSigningProtection): WalletDemoBootstrapResult = useWallet {
        val identity = ensureIdentity()
        keyId = identity.keyId
        did = identity.did
        WalletDemoBootstrapResult(
            keyId = identity.keyId,
            did = identity.did,
            publicJwk = identity.publicJwk,
            signingProtection = WalletDemoSigningProtection.None,
        )
    }

    override suspend fun signingProtectionAvailability(
        signingProtection: WalletDemoSigningProtection,
    ): WalletDemoSigningProtectionAvailability = WalletDemoSigningProtectionAvailability.Available

    override suspend fun listCredentials(): List<WalletDemoCredential> = useWallet {
        client.listCredentialMetadata(walletId).map { metadata ->
            runCatching { client.getCredential(walletId, metadata.id).toDemoCredential(metadata) }
                .getOrDefault(metadata.toDemoCredential())
        }
    }

    override suspend fun startIssuance(
        offerUrl: String,
        redirectUri: String,
        did: String?,
    ): WalletDemoIssuanceSession = useWallet {
        val batchOffer = client.resolveOffer(walletId, offerUrl)
        val resolved = batchOffer.offer
        val session = Api2IssuanceSession(
            id = newSessionId(),
            offerUrl = offerUrl,
            redirectUri = redirectUri.ifBlank { this.redirectUri },
            did = did ?: this.did,
            grant = resolved.toDemoGrant(),
            preview = resolved.toDemoPreview(batchOffer.batchSize),
            credentialIssuer = resolved.credentialIssuer,
            credentialEndpoint = resolved.credentialEndpoint,
            nonceEndpoint = resolved.nonceEndpoint,
        )
        issuanceSessions[session.id] = session
        WalletDemoIssuanceSession(
            id = session.id,
            grant = session.grant,
            preview = session.preview,
        )
    }

    override suspend fun beginAuthorizationIssuance(
        sessionId: String,
        credentials: List<WalletDemoCredentialSelection>,
    ): WalletDemoIssuanceAuthorization = withIssuance(sessionId) { session ->
        val selections = prepareSelections(sessionId, credentials)
        val configurationIds = selections.map { it.credentialConfigurationId }
        val result = client.authorizationUrl(walletId, session.offerUrl, session.redirectUri, configurationIds)
        check(result.credentialConfigurationIds.toSet() == configurationIds.toSet()) { "Authorization changed the selected configurations" }
        checkIssuanceActive(sessionId)
        val updated = session.copy(
            codeVerifier = result.codeVerifier,
            authorizationState = result.state,
            credentials = selections,
            credentialIssuer = result.credentialIssuerBaseUrl,
            nonceEndpoint = result.nonceEndpoint,
        )
        issuanceSessions[sessionId] = updated
        WalletApi2BrowserSessionStore.savePendingIssuance(updated.toPersisted())
        WalletDemoIssuanceAuthorization(url = result.authorizationUrl)
    }

    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
        credentials: List<WalletDemoCredentialSelection>,
    ): WalletDemoIssuanceOutcome = withIssuance(sessionId) { session ->
        val selections = prepareSelections(sessionId, credentials)
        val result = client.receivePreAuthorized(
            walletId = walletId,
            offerUrl = session.offerUrl,
            txCode = transactionCode,
            did = session.did,
            redirectUri = session.redirectUri,
            credentials = selections,
            keyId = keyId,
        )
        finishIssuance(sessionId)
        result.toOutcome()
    }

    override suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome = withIssuance(sessionId) { session ->
        val parsed = parseWalletApi2AuthorizationCallback(
            callbackUri = callbackUri,
            expectedState = session.authorizationState,
            expectedRedirectUri = session.redirectUri,
        )
        val code = when (parsed) {
            WalletApi2AuthorizationCallback.Denied -> {
                issuanceSessions.remove(sessionId)
                WalletApi2BrowserSessionStore.clearPendingIssuance(sessionId)
                return@withIssuance WalletDemoIssuanceOutcome.Cancelled
            }
            is WalletApi2AuthorizationCallback.Invalid -> {
                return@withIssuance WalletDemoIssuanceOutcome.Failed(parsed.message)
            }
            is WalletApi2AuthorizationCallback.Code -> parsed.code
        }
        val credentials = session.credentials
            ?: return@withIssuance WalletDemoIssuanceOutcome.Failed("Authorization session is missing credential selections")
        val result = client.receiveAuthorized(
            walletId,
            ReceiveAuthorizedCredentialRequestDto(
                code = code,
                codeVerifier = session.codeVerifier,
                credentialIssuer = session.credentialIssuer,
                credentialEndpoint = session.credentialEndpoint,
                credentials = credentials,
                nonceEndpoint = session.nonceEndpoint,
                redirectUri = session.redirectUri,
                did = session.did,
                keyId = keyId,
            ),
        )
        finishIssuance(sessionId)
        result.toOutcome()
    }

    override suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome {
        issuanceSessions.remove(sessionId)
        WalletApi2BrowserSessionStore.clearPendingIssuance(sessionId)
        return WalletDemoIssuanceOutcome.Cancelled
    }

    private suspend fun <T> withIssuance(sessionId: String, block: suspend (Api2IssuanceSession) -> T): T = useWallet {
        val session = requireIssuance(sessionId)
        check(processingIssuanceSessions.add(sessionId)) { "Issuance session is already processing" }
        try {
            block(session)
        } finally {
            processingIssuanceSessions.remove(sessionId)
        }
    }

    private suspend fun checkIssuanceActive(sessionId: String) {
        currentCoroutineContext().ensureActive()
        check(issuanceSessions.containsKey(sessionId)) { "Issuance session was cancelled" }
    }

    private suspend fun finishIssuance(sessionId: String) {
        checkIssuanceActive(sessionId)
        issuanceSessions.remove(sessionId)
        WalletApi2BrowserSessionStore.clearPendingIssuance(sessionId)
    }

    override suspend fun listDeferredIssuance(): List<WalletDemoDeferredCredential> = useWallet {
        client.listDeferred(walletId).map { it.toDemoDeferred() }
    }

    private suspend fun prepareSelections(sessionId: String, requested: List<WalletDemoCredentialSelection>): List<IssuanceCredentialSelectionDto> {
        val session = requireIssuance(sessionId)
        require(requested.isNotEmpty()) { "Select at least one credential" }
        for (selection in requested) {
            require(session.preview.offeredCredentials.any { it.configurationId == selection.credentialConfigurationId }) { "Credential configuration was not offered" }
            val count = when (val holders = selection.holders) {
                is WalletDemoCredentialHolders.Existing -> holders.bindings.size
                is WalletDemoCredentialHolders.NewKeys -> holders.count
            }
            require(count in 1..(session.preview.batchSize ?: 1)) { "Requested copies exceed the issuer's batch limit" }
        }
        session.credentials?.let { previous ->
            require(previous.size == requested.size && previous.zip(requested).all { (accepted, selection) ->
                accepted.credentialConfigurationId == selection.credentialConfigurationId && when (val holders = selection.holders) {
                    is WalletDemoCredentialHolders.Existing -> accepted.holderBindings == holders.bindings.map { HolderBindingDto(it.keyId, it.did) }
                    is WalletDemoCredentialHolders.NewKeys -> accepted.holderBindings.size == holders.count
                }
            }) { "Cannot change holder selections after acceptance; start a new issuance session" }
            return previous
        }
        val generated = mutableListOf<WalletDemoHolderBinding>()
        try {
            val resolved = requested.map { selection ->
                val bindings = when (val holders = selection.holders) {
                    is WalletDemoCredentialHolders.Existing -> holders.bindings
                    is WalletDemoCredentialHolders.NewKeys -> buildList {
                        repeat(holders.count) {
                            currentCoroutineContext().ensureActive()
                            val key = client.generateKey(walletId)
                            generated += WalletDemoHolderBinding(key.keyId)
                            currentCoroutineContext().ensureActive()
                            val did = client.createDid(walletId, key.keyId)
                            val binding = WalletDemoHolderBinding(key.keyId, did.did)
                            generated[generated.lastIndex] = binding
                            add(binding)
                        }
                    }
                }
                IssuanceCredentialSelectionDto(selection.credentialConfigurationId, bindings.map { HolderBindingDto(it.keyId, it.did) })
            }
            currentCoroutineContext().ensureActive()
            check(issuanceSessions[sessionId] === session) { "Issuance session changed during key preparation" }
            issuanceSessions[sessionId] = session.copy(credentials = resolved)
            return resolved
        } catch (cause: Throwable) {
            withContext(NonCancellable) {
                for (holder in generated) {
                    try { holder.did?.let { client.deleteDid(walletId, it) } }
                    catch (cleanup: Throwable) { cause.addSuppressed(cleanup) }
                    try { client.deleteKey(walletId, holder.keyId) }
                    catch (cleanup: Throwable) { cause.addSuppressed(cleanup) }
                }
            }
            throw cause
        }
    }


    override suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletDemoIssuanceOutcome = useWallet {
        client.resumeDeferred(walletId, deferredCredentialId).toOutcome()
    }

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult = useWallet {
        client.present(walletId, requestUrl, did ?: this.did, keyId).toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationSent,
            failureMessage = WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation,
        )
    }

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult = useWallet {
        val preview = client.previewPresentation(walletId, requestUrl, keyId)
        val mapped = preview.toDemoPreview(requestUrl)
        presentationSessions[requestUrl] = Api2PresentationSession(
            requestUrl = requestUrl,
            keyId = preview.keyId ?: keyId,
        )
        mapped
    }

    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
        paymentConsentRevision: String?,
    ): WalletDemoOperationResult = useWallet {
        val session = presentationSessions[previewHandle.value]
        val built = client.buildVpToken(
            walletId,
            BuildVpTokenRequestDto(
                requestUrl = previewHandle.value,
                selectedCredentialOptions = selectedCredentialOptions.map {
                    CredentialSelectionDto(queryId = it.queryId, credentialId = it.credentialId)
                },
                selectedDisclosureOptions = selectedDisclosureOptions.toDisclosureSelectionDtos(),
                keyId = session?.keyId ?: keyId,
                did = did ?: this.did,
            ),
        )
        val result = client.sendPresentationResponse(
            walletId,
            SendAuthorizationResponseRequestDto(
                requestUrl = previewHandle.value,
                vpToken = built.vpToken,
                idToken = built.idToken,
            ),
        )
        presentationSessions.remove(previewHandle.value)
        result.toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationSent,
            failureMessage = WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation,
        )
    }

    override suspend fun rejectPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
    ): WalletDemoOperationResult = useWallet {
        val result = client.rejectPresentation(walletId, previewHandle.value)
        presentationSessions.remove(previewHandle.value)
        result.toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationRejected,
            failureMessage = WalletDisplayText.RejectionFinishedWithoutVerifierConfirmation,
        )
    }

    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) {
        presentationSessions.remove(previewHandle.value)
    }

    override suspend fun deleteCredential(credentialId: String): Boolean = useWallet {
        client.deleteCredential(walletId, credentialId)
    }

    // Admission is synchronous on the browser event loop; HTTP calls may suspend.
    private suspend inline fun <T> useWallet(block: () -> T): T {
        currentCoroutineContext().ensureActive()
        check(resetStage == null) { "Wallet reset must finish before using the wallet" }
        activeOperations++
        try {
            return block()
        } finally {
            activeOperations--
        }
    }

    // Keep acknowledged steps for explicit retry; a lost server acknowledgement remains uncertain.
    override suspend fun deleteWallet() {
        require(kind.canManageWallet) { "This API does not reset wallets" }
        currentCoroutineContext().ensureActive()
        check(!resetInProgress && activeOperations == 0) { "Cannot reset the wallet while an operation is in progress" }
        resetInProgress = true
        if (resetStage == null) resetStage = WalletResetStage.DeleteCurrent
        try {
            keyId = null
            did = null
            issuanceSessions.clear()
            presentationSessions.clear()
            WalletApi2BrowserSessionStore.clearPendingIssuance()
            if (resetStage == WalletResetStage.DeleteCurrent) {
                client.deleteWallet(walletId)
                resetStage = WalletResetStage.CreateReplacement
            }
            if (resetStage == WalletResetStage.CreateReplacement) {
                walletId = client.createWallet()
                resetStage = WalletResetStage.PublishReplacement
            }
            onWalletIdChanged(walletId)
            resetStage = null
        } finally {
            resetInProgress = false
        }
    }

    override fun pendingAuthorizationIssuance(): WalletDemoIssuanceSession? {
        if (resetStage != null) return null
        val persisted = WalletApi2BrowserSessionStore.loadPendingIssuance() ?: return null
        val session = issuanceSessions.getOrPut(persisted.id) { persisted.toApi2Session() }
        return WalletDemoIssuanceSession(
            id = session.id,
            grant = WalletDemoIssuanceGrant.AuthorizationCode,
            preview = emptyOfferPreview(session.credentialIssuer),
        )
    }

    private suspend fun ensureIdentity(): WalletIdentity {
        if (!kind.canGenerateIdentity) return useExistingIdentity()
        val info = runCatching { client.walletInfo(walletId) }.getOrNull()
        val existingKeyId = info?.defaultKeyId ?: client.listKeys(walletId).firstOrNull()?.keyId
        val resolvedKeyId = existingKeyId ?: client.generateKey(walletId).keyId
        val dids = client.listDids(walletId)
        val existingDid = info?.defaultDidId?.let { defaultDid -> dids.firstOrNull { it.did == defaultDid } }
            ?: dids.firstOrNull()
        val resolvedDid = existingDid ?: client.createDid(walletId, resolvedKeyId)
        runCatching { client.setDefaultKey(walletId, resolvedKeyId) }
        runCatching { client.setDefaultDid(walletId, resolvedDid.did) }
        return WalletIdentity(
            keyId = resolvedKeyId,
            did = resolvedDid.did,
            publicJwk = publicJwkFromDidDocument(resolvedDid.document),
        )
    }

    private suspend fun useExistingIdentity(): WalletIdentity {
        val resolvedKey = client.listKeys(walletId).firstOrNull()
            ?: error("This wallet has no keys in its linked key store")
        val resolvedDid = client.listDids(walletId).firstOrNull()
            ?: error("This wallet has no DIDs in its linked DID store")
        keyId = resolvedKey.keyId
        did = resolvedDid.did
        return WalletIdentity(
            keyId = resolvedKey.keyId,
            did = resolvedDid.did,
            publicJwk = publicJwkFromDidDocument(resolvedDid.document),
        )
    }

    private fun requireIssuance(sessionId: String): Api2IssuanceSession =
        issuanceSessions[sessionId]
            ?: WalletApi2BrowserSessionStore.loadPendingIssuance()
                ?.takeIf { it.id == sessionId }
                ?.toApi2Session()
                ?.also { issuanceSessions[it.id] = it }
            ?: error("Issuance session is missing")


}

private enum class WalletResetStage { DeleteCurrent, CreateReplacement, PublishReplacement }

private fun newSessionId(): String = buildString {
    repeat(16) { append("0123456789abcdef"[Random.nextInt(16)]) }
}

private data class WalletIdentity(
    val keyId: String,
    val did: String,
    val publicJwk: String,
)

private data class Api2IssuanceSession(
    val id: String,
    val offerUrl: String,
    val redirectUri: String,
    val did: String?,
    val grant: id.walt.walletdemo.compose.logic.WalletDemoIssuanceGrant,
    val preview: id.walt.walletdemo.compose.logic.WalletDemoOfferPreview,
    val credentialIssuer: String,
    val credentialEndpoint: String,
    val nonceEndpoint: String?,
    val codeVerifier: String? = null,
    val authorizationState: String? = null,
    val credentials: List<IssuanceCredentialSelectionDto>? = null,
)

private data class Api2PresentationSession(
    val requestUrl: String,
    val keyId: String?,
)

private fun Api2IssuanceSession.toPersisted() = PersistedAuthorizationIssuance(
    id = id,
    offerUrl = offerUrl,
    redirectUri = redirectUri,
    did = did,
    credentialIssuer = credentialIssuer,
    credentialEndpoint = credentialEndpoint,
    nonceEndpoint = nonceEndpoint,
    codeVerifier = codeVerifier,
    authorizationState = authorizationState,
    credentials = requireNotNull(credentials),
)

private fun PersistedAuthorizationIssuance.toApi2Session() = Api2IssuanceSession(
    id = id,
    offerUrl = offerUrl,
    redirectUri = redirectUri,
    did = did,
    grant = WalletDemoIssuanceGrant.AuthorizationCode,
    preview = emptyOfferPreview(credentialIssuer),
    credentialIssuer = credentialIssuer,
    credentialEndpoint = credentialEndpoint,
    nonceEndpoint = nonceEndpoint,
    codeVerifier = codeVerifier,
    authorizationState = authorizationState,
    credentials = credentials,
)

private fun emptyOfferPreview(credentialIssuer: String) = WalletDemoOfferPreview(
    issuer = WalletDemoIssuerMetadata(credentialIssuer = credentialIssuer, display = null),
    offeredCredentials = emptyList(),
    transactionCode = null,
    requiresIssuerAuthentication = true,
)
