package id.walt.walletdemo.compose.logic.walletapi2

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
    onWalletIdChanged: (String) -> Unit = {},
): DemoWallet = WalletApi2DemoWallet(
    client = WalletApi2Client(baseUrl = baseUrl, token = token),
    walletId = walletId,
    redirectUri = redirectUri,
    onWalletIdChanged = onWalletIdChanged,
)

private class WalletApi2DemoWallet(
    private val client: WalletApi2Client,
    private var walletId: String,
    private val redirectUri: String,
    private val onWalletIdChanged: (String) -> Unit,
) : DemoWallet {
    private val issuanceSessions = mutableMapOf<String, Api2IssuanceSession>()
    private val presentationSessions = mutableMapOf<String, Api2PresentationSession>()
    private var keyId: String? = null
    private var did: String? = null

    override suspend fun bootstrap(signingProtection: WalletDemoSigningProtection): WalletDemoBootstrapResult {
        val identity = ensureIdentity()
        keyId = identity.keyId
        did = identity.did
        return WalletDemoBootstrapResult(
            keyId = identity.keyId,
            did = identity.did,
            publicJwk = identity.publicJwk,
            signingProtection = WalletDemoSigningProtection.None,
        )
    }

    override suspend fun signingProtectionAvailability(
        signingProtection: WalletDemoSigningProtection,
    ): WalletDemoSigningProtectionAvailability = WalletDemoSigningProtectionAvailability.Available

    override suspend fun listCredentials(): List<WalletDemoCredential> =
        client.listCredentialMetadata(walletId).map { metadata ->
            runCatching { client.getCredential(walletId, metadata.id).toDemoCredential(metadata) }
                .getOrDefault(metadata.toDemoCredential())
        }

    override suspend fun startIssuance(
        offerUrl: String,
        redirectUri: String,
        did: String?,
    ): WalletDemoIssuanceSession {
        val resolved = client.resolveOffer(walletId, offerUrl)
        val session = Api2IssuanceSession(
            id = newSessionId(),
            offerUrl = offerUrl,
            redirectUri = redirectUri.ifBlank { this.redirectUri },
            did = did ?: this.did,
            grant = resolved.toDemoGrant(),
            preview = resolved.toDemoPreview(),
            credentialIssuer = resolved.credentialIssuer,
            credentialEndpoint = resolved.credentialEndpoint,
            nonceEndpoint = resolved.nonceEndpoint,
        )
        issuanceSessions[session.id] = session
        return WalletDemoIssuanceSession(
            id = session.id,
            grant = session.grant,
            preview = session.preview,
        )
    }

    override suspend fun beginAuthorizationIssuance(sessionId: String, credentials: List<WalletDemoCredentialSelection>): WalletDemoIssuanceAuthorization {
        val session = requireIssuance(sessionId)
        val configurationIds = credentials.map { it.credentialConfigurationId }
        val result = client.authorizationUrl(walletId, session.offerUrl, session.redirectUri, configurationIds)
        check(result.credentialConfigurationIds.toSet() == configurationIds.toSet()) { "Authorization changed the selected configurations" }
        val updated = session.copy(
            codeVerifier = result.codeVerifier,
            authorizationState = result.state,
            credentials = credentials.toSelectionDtos(),
            credentialIssuer = result.credentialIssuerBaseUrl,
            nonceEndpoint = result.nonceEndpoint,
        )
        issuanceSessions[sessionId] = updated
        WalletApi2BrowserSessionStore.savePendingIssuance(updated.toPersisted())
        return WalletDemoIssuanceAuthorization(url = result.authorizationUrl)
    }

    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
        credentials: List<WalletDemoCredentialSelection>,
    ): WalletDemoIssuanceOutcome {
        val session = requireIssuance(sessionId)
        val result = client.receivePreAuthorized(
            walletId = walletId,
            offerUrl = session.offerUrl,
            txCode = transactionCode,
            did = session.did,
            redirectUri = session.redirectUri,
            credentials = credentials.toSelectionDtos(),
        )
        issuanceSessions.remove(sessionId)
        WalletApi2BrowserSessionStore.clearPendingIssuance()
        return result.toOutcome()
    }

    override suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome {
        val session = requireIssuance(sessionId)
        val parsed = parseWalletApi2AuthorizationCallback(
            callbackUri = callbackUri,
            expectedState = session.authorizationState,
            expectedRedirectUri = session.redirectUri,
        )
        val code = when (parsed) {
            WalletApi2AuthorizationCallback.Denied -> {
                issuanceSessions.remove(sessionId)
                WalletApi2BrowserSessionStore.clearPendingIssuance()
                return WalletDemoIssuanceOutcome.Cancelled
            }
            is WalletApi2AuthorizationCallback.Invalid -> {
                return WalletDemoIssuanceOutcome.Failed(parsed.message)
            }
            is WalletApi2AuthorizationCallback.Code -> parsed.code
        }
        val credentials = session.credentials
            ?: return WalletDemoIssuanceOutcome.Failed("Authorization session is missing credential selections")
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
            ),
        )
        issuanceSessions.remove(sessionId)
        WalletApi2BrowserSessionStore.clearPendingIssuance()
        return result.toOutcome()
    }

    override suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome {
        issuanceSessions.remove(sessionId)
        WalletApi2BrowserSessionStore.clearPendingIssuance()
        return WalletDemoIssuanceOutcome.Cancelled
    }

    override suspend fun listDeferredIssuance(): List<WalletDemoDeferredCredential> =
        client.listDeferred(walletId).map { it.toDemoDeferred() }

    override suspend fun createIssuanceHolderKeys(count: Int): List<WalletDemoHolderBinding> {
        require(count > 0)
        val created = mutableListOf<WalletDemoHolderBinding>()
        try {
            repeat(count) {
                currentCoroutineContext().ensureActive()
                val key = client.generateKey(walletId)
                created += WalletDemoHolderBinding(key.keyId)
                currentCoroutineContext().ensureActive()
                val holderDid = client.createDid(walletId, key.keyId)
                created[created.lastIndex] = WalletDemoHolderBinding(key.keyId, holderDid.did)
            }
            currentCoroutineContext().ensureActive()
            return created
        } catch (cause: Throwable) {
            withContext(NonCancellable) {
                for (holder in created) {
                    try { holder.did?.let { client.deleteDid(walletId, it) } }
                    catch (cleanup: Throwable) { cause.addSuppressed(cleanup) }
                    try { client.deleteKey(walletId, holder.keyId) }
                    catch (cleanup: Throwable) { cause.addSuppressed(cleanup) }
                }
            }
            throw cause
        }
    }

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletDemoIssuanceOutcome =
        client.resumeDeferred(walletId, deferredCredentialId).toOutcome()

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        client.present(walletId, requestUrl, did ?: this.did).toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationSent,
            failureMessage = WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation,
        )

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult {
        val preview = client.previewPresentation(walletId, requestUrl, keyId)
        val mapped = preview.toDemoPreview(requestUrl)
        presentationSessions[requestUrl] = Api2PresentationSession(
            requestUrl = requestUrl,
            keyId = preview.keyId ?: keyId,
        )
        return mapped
    }

    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
        paymentConsentRevision: String?,
    ): WalletDemoOperationResult {
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
        return result.toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationSent,
            failureMessage = WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation,
        )
    }

    override suspend fun rejectPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
    ): WalletDemoOperationResult {
        val result = client.rejectPresentation(walletId, previewHandle.value)
        presentationSessions.remove(previewHandle.value)
        return result.toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationRejected,
            failureMessage = WalletDisplayText.RejectionFinishedWithoutVerifierConfirmation,
        )
    }

    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) {
        presentationSessions.remove(previewHandle.value)
    }

    override suspend fun deleteCredential(credentialId: String): Boolean =
        client.deleteCredential(walletId, credentialId)

    override suspend fun deleteWallet() {
        val created = replaceWalletAfterSuccessfulDelete(
            deleteCurrent = { client.deleteWallet(walletId) },
            createReplacement = { client.createWallet() },
        )
        walletId = created
        keyId = null
        did = null
        issuanceSessions.clear()
        presentationSessions.clear()
        onWalletIdChanged(created)
        WalletApi2BrowserSessionStore.clearPendingIssuance()
        ensureIdentity()
    }

    override fun pendingAuthorizationIssuance(): WalletDemoIssuanceSession? {
        val persisted = WalletApi2BrowserSessionStore.loadPendingIssuance() ?: return null
        val session = persisted.toApi2Session()
        issuanceSessions[session.id] = session
        return WalletDemoIssuanceSession(
            id = session.id,
            grant = WalletDemoIssuanceGrant.AuthorizationCode,
            preview = emptyOfferPreview(session.credentialIssuer),
        )
    }

    private suspend fun ensureIdentity(): WalletIdentity {
        val info = runCatching { client.walletInfo(walletId) }.getOrNull()
        val existingKeyId = info?.defaultKeyId ?: client.listKeys(walletId).firstOrNull()?.keyId
        val resolvedKeyId = existingKeyId ?: client.generateKey(walletId).keyId
        val dids = client.listDids(walletId)
        val existingDid = info?.defaultDidId?.let { defaultDid -> dids.firstOrNull { it.did == defaultDid } }
            ?: dids.firstOrNull()
        val resolvedDid = existingDid ?: client.createDid(walletId, resolvedKeyId)
        runCatching { client.setDefaultKey(walletId, resolvedKeyId) }
        runCatching { client.setDefaultDid(walletId, resolvedDid.did) }
        keyId = resolvedKeyId
        did = resolvedDid.did
        return WalletIdentity(
            keyId = resolvedKeyId,
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
