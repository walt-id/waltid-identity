package id.walt.wallet2.handlers

import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto2.CryptoRuntime
import id.walt.crypto2.jose.CompactJws
import id.walt.crypto2.jose.Jwk
import id.walt.crypto2.jose.selectJwsAlgorithm
import id.walt.crypto2.keys.KeyUsage
import id.walt.crypto2.keys.toPublicJwk
import id.walt.crypto2.providers.cryptography.defaultSoftwareKeyProviders
import id.walt.openid4vci.clientauth.attestation.ClientAttestationHeaders.CLIENT_ATTESTATION_CHALLENGE
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.clientauth.attestation.ClientAttestationSigningAlgorithms
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.wallet2.data.*
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.attestation.WalletAttestationChallengeRequestBuilder
import id.waltid.openid4vci.wallet.attestation.WalletAttestationChallengeRequestError
import id.waltid.openid4vci.wallet.attestation.WalletAttestationChallengeRequestException
import id.waltid.openid4vci.wallet.authorization.AuthorizationRequestBuilder
import id.waltid.openid4vci.wallet.authorization.PushedAuthorizationRequestExecutor
import id.waltid.openid4vci.wallet.authorization.RetryablePushedAuthorizationRequestException
import id.waltid.openid4vci.wallet.authorization.AuthorizationResponseParser
import id.waltid.openid4vci.wallet.dpop.DPOP_HEADER
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_ATTEMPTS
import id.waltid.openid4vci.wallet.dpop.DPOP_NONCE_HEADER
import id.waltid.openid4vci.wallet.dpop.USE_DPOP_NONCE
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.waltid.openid4vci.wallet.metadata.CredentialIssuerMetadataTrustResolver
import id.waltid.openid4vci.wallet.metadata.LocalizedMetadata
import id.waltid.openid4vci.wallet.metadata.MetadataSignerTrustType
import id.waltid.openid4vci.wallet.metadata.OfferedCredentialResolver
import id.waltid.openid4vci.wallet.metadata.ResolvedCredentialIssuerMetadata
import id.waltid.openid4vci.wallet.nonce.NonceRequestBuilder
import id.waltid.openid4vci.wallet.nonce.NonceRequestError
import id.waltid.openid4vci.wallet.nonce.NonceRequestException
import id.waltid.openid4vci.wallet.oauth.ClientConfiguration
import id.waltid.openid4vci.wallet.oauth.PKCEManager
import id.waltid.openid4vci.wallet.offer.CredentialOfferParser
import id.waltid.openid4vci.wallet.offer.CredentialOfferResolver
import id.waltid.openid4vci.wallet.proof.JwtProofBuilder
import id.waltid.openid4vci.wallet.token.DPoPProofFactory
import id.waltid.openid4vci.wallet.token.TokenRequestBuilder
import id.waltid.openid4vci.wallet.token.TokenRequestException
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import id.waltid.openid4vci.wallet.credential.validateCredentialResponse
import id.waltid.openid4vci.wallet.credential.CredentialRequestBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}
private val crypto2Runtime = CryptoRuntime(defaultSoftwareKeyProviders())

/** Grant selected for a resolved issuance session. */
@Serializable
enum class WalletIssuanceGrant {
    PRE_AUTHORIZED_CODE,
    AUTHORIZATION_CODE,
}

/** Typed transaction-code requirement from a pre-authorized offer. */
@Serializable
data class WalletIssuanceTransactionCode(
    val inputMode: String?,
    val length: Int?,
    val descriptionText: String?,
)

/** Issuer display information safe for an app review screen. */
@Serializable
data class WalletIssuanceIssuerPreview(
    val identifier: String,
    val name: String?,
    val locale: String?,
    val logoUri: String?,
    val logoAltText: String?,
    /** Whether the issuer metadata was unsigned or verified signed metadata. */
    val metadataProvenance: WalletIssuanceMetadataProvenance,
)

/** Verification provenance of issuer metadata retained by an issuance session. */
@Serializable
sealed interface WalletIssuanceMetadataProvenance {
    /** Metadata was received as an unsigned JSON document. */
    @Serializable
    data object Unsigned : WalletIssuanceMetadataProvenance

    /** Metadata was received in a JWS verified by the configured trust resolver. */
    @Serializable
    data class Signed(
        /** Exact compact JWS returned by the issuer. */
        val compactJwt: String,
        /** JWS algorithm verified by the configured trust resolver. */
        val algorithm: String,
        /** Identifier of the trusted verification key, as reported by the trust resolver. */
        val keyId: String?,
        /** Authority relationship established by the trust resolver. */
        val trustType: MetadataSignerTrustType,
    ) : WalletIssuanceMetadataProvenance
}

/** Offered credential information safe for an app review screen. */
@Serializable
data class WalletIssuanceCredentialPreview(
    val configurationId: String,
    val format: String,
    val name: String?,
    val descriptionText: String?,
    val logoUri: String?,
    val logoAltText: String? = null,
    val backgroundColor: String? = null,
    val backgroundImageUri: String? = null,
    val textColor: String? = null,
    val vct: String? = null,
    val doctype: String? = null,
)

/** Typed offer preview retained by the issuance session. */
@Serializable
data class WalletIssuanceOfferPreview(
    val batchSize: Int? = null,
    val grant: WalletIssuanceGrant,
    val issuer: WalletIssuanceIssuerPreview,
    val credentials: List<WalletIssuanceCredentialPreview>,
    val transactionCode: WalletIssuanceTransactionCode?,
)

/** Non-secret PKCE metadata bound to an authorization-code session. */
@Serializable
data class WalletIssuancePkceState(
    val codeChallenge: String,
    val codeChallengeMethod: String,
)

/** Browser request and callback binding for an authorization-code session. */
@Serializable
data class WalletIssuanceAuthorization(
    val url: String,
    val state: String,
    val redirectUri: String,
    val pkce: WalletIssuancePkceState,
    val pushedAuthorizationRequestUsed: Boolean,
    val requestUriExpiresAtEpochMilliseconds: Long? = null,
)

/** Public handle returned by [WalletIssuanceSessionService.start]. */
@Serializable
data class WalletIssuanceSession(
    val id: String,
    val offer: WalletIssuanceOfferPreview,
)

/** Input used to start either supported grant from one offer. */
@Serializable
data class WalletIssuanceSessionRequest(
    val credentialIssuer: String? = null,
    val credentialConfigurationIds: List<String>? = null,
    override val offerUrl: Url? = null,
    override val offerJson: JsonObject? = null,
    val key: DirectSerializedKey? = null,
    val keyId: String? = null,
    val did: String? = null,
    val clientId: String = "eudiw-abca",
    val redirectUri: Url = Url("openid://"),
    val tokenRequestHeaders: Map<String, String> = emptyMap(),
) : CredentialOfferSource {
    init {
        if (credentialIssuer == null) checkOfferSource() else {
            require(offerUrl == null && offerJson == null)
            require(credentialIssuer.isNotBlank() && !credentialConfigurationIds.isNullOrEmpty())
        }
        require(clientId.isNotBlank()) { "clientId cannot be blank" }
    }

}

/** Callback continuation supplied after the browser returns to the wallet. */
@Serializable
data class WalletIssuanceAuthorizationCallback(
    val sessionId: String,
    val callbackUri: String,
)

/** Wallet-local retention policy for uncompleted issuance sessions. */
@Serializable
data class WalletIssuanceSessionPolicy(
    val reviewTtl: Duration = 10.minutes,
    val authorizationCallbackTtl: Duration = 10.minutes,
)

/** Public handle for pending issuer processing or an already received batch awaiting local storage. */
@Serializable
data class WalletDeferredCredential(
    val credentialIdentifier: String? = null,
    val id: String,
    /** Unknown only for local storage recovery from an isolated request that omitted it. */
    val credentialConfigurationId: String? = null,
    val intervalSeconds: Long?,
)

/** Stable failure categories returned without protocol secrets or response bodies. */
@Serializable
enum class WalletIssuanceErrorCode {
    INVALID_SESSION,
    INVALID_CALLBACK,
    INVALID_INPUT,
    AUTHORIZATION_FAILED,
    ISSUER_METADATA,
    ISSUER_RESPONSE,
    NETWORK,
    REMOTE_OUTCOME_UNCERTAIN,
    STORAGE_OUTCOME_UNCERTAIN,
    CRYPTO,
    STORAGE,
    PROTOCOL,
}

@Serializable
data class WalletIssuanceError(
    val code: WalletIssuanceErrorCode,
    val message: String,
)

/** Terminal or pending result of an issuance-session transition. */
@Serializable
sealed interface WalletIssuanceOutcome {
    val sessionId: String

    @Serializable
    @kotlinx.serialization.SerialName("stored")
    data class Stored(
        override val sessionId: String,
        val credentialIds: List<String>,
    ) : WalletIssuanceOutcome

    @Serializable
    @kotlinx.serialization.SerialName("deferred")
    data class Deferred(
        override val sessionId: String,
        val storedCredentialIds: List<String>,
        val credentials: List<WalletDeferredCredential>,
    ) : WalletIssuanceOutcome

    @Serializable
    @kotlinx.serialization.SerialName("cancelled")
    data class Cancelled(
        override val sessionId: String,
    ) : WalletIssuanceOutcome

    @Serializable
    @kotlinx.serialization.SerialName("failed")
    data class Failed(
        override val sessionId: String,
        val error: WalletIssuanceError,
        val storedCredentialIds: List<String> = emptyList(),
        val deferredCredentials: List<WalletDeferredCredential> = emptyList(),
        val failure: CredentialIssuanceFailure? = null,
    ) : WalletIssuanceOutcome
}

/**
 * Stateful OpenID4VCI 1.0 engine shared by pre-authorized and authorization-code grants.
 *
 * Protocol secrets stay behind the engine. Public handles contain review data and callback state,
 * while transitions are validated against an authoritative single-use record. A configured
 * [WalletIssuanceSessionStore] makes active and deferred continuations process-durable.
 */
class WalletIssuanceSessionService(
    private val wallet: Wallet,
    private val attestationAssembler: ClientAttestationAssembler? = null,
    private val metadataTrustResolver: CredentialIssuerMetadataTrustResolver? = null,
    private val onEvent: suspend (WalletSessionEvent) -> Unit = {},
    sessionStore: WalletIssuanceSessionStore? = null,
    httpClient: HttpClient? = null,
    private val sessionPolicy: WalletIssuanceSessionPolicy = WalletIssuanceSessionPolicy(),
    private val now: () -> Instant = { Clock.System.now() },
    runtimeState: WalletIssuanceSessionState? = null,
) {
    private val runtime = (runtimeState ?: WalletIssuanceSessionState(wallet.id, sessionStore)).also {
        require(sessionStore == null || sessionStore === it.store) { "Conflicting issuance stores" }
    }
    private val sessionStore = runtime.store

    init {
        require(runtime.walletId == wallet.id) { "Issuance state belongs to a different wallet" }
        require(sessionPolicy.reviewTtl > Duration.ZERO) { "reviewTtl must be positive" }
        require(sessionPolicy.authorizationCallbackTtl > Duration.ZERO) {
            "authorizationCallbackTtl must be positive"
        }
    }

    private val httpClient = httpClient ?: WebDataFetcher(WebDataFetcherId.WALLET2_ISSUANCE_HANDLER).httpClient
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = runtime.mutex
    private val sessions = runtime.sessions
    private val deferred = runtime.deferred
    private val pollingDeferred = runtime.pollingDeferred

    /**
     * Resolves and binds an offer using the wallet's current language preferences.
     *
     * The selected review preview, rather than the locale configuration, is retained with the
     * session so restored continuations preserve exactly what the user accepted.
     */
    suspend fun start(
        request: WalletIssuanceSessionRequest,
        preferredLocales: List<String> = emptyList(),
    ): WalletIssuanceSession {
        ensureOpen()
        val keyMaterial = resolveIssuanceKeyMaterial(request.key, request.keyId)
        val resolved = resolve(request, preferredLocales)
        val grant = resolved.grantType
            ?: error("Credential offer does not contain a supported grant")
        val sessionId = Uuid.random().toString()
        val publicSession = WalletIssuanceSession(
            id = sessionId,
            offer = resolved.toPreview(grant, preferredLocales),
        )
        val selectedKeyId = request.keyId ?: if (request.key == null) {
            keyMaterial.keyId.takeIf { it.isNotBlank() }
                ?: wallet.defaultKeyId
                ?: wallet.listAllKeys().firstOrNull()?.keyId
        } else {
            keyMaterial.keyId.takeIf { it.isNotBlank() }
        }
        val active = ActiveSession(
            public = publicSession,
            request = request,
            resolved = resolved,
            keyMaterial = keyMaterial,
            keyId = selectedKeyId,
            did = request.did ?: wallet.defaultDid(),
            authorization = null,
            state = SessionState.AWAITING_ACCEPTANCE,
            expiresAtEpochMilliseconds = nowEpochMilliseconds() +
                    sessionPolicy.reviewTtl.inWholeMilliseconds,
        )
        try {
            mutex.withLock {
                check(!runtime.closed) { "Wallet issuance is closed" }
                val evicted = if (sessions.size >= MAX_ACTIVE_SESSIONS) {
                    sessions.values.firstOrNull { it.state != SessionState.PROCESSING }
                        ?.let { sessions.remove(it.public.id) }
                } else null
                evicted?.let { removePersistedActive(it) }
                sessions[sessionId] = active
                persistActive(active)
            }
            prunePersistedActiveSessions()
        } catch (error: CancellationException) {
            invalidateActiveSessionBestEffort(sessionId)
            throw error
        } catch (error: Exception) {
            invalidateActiveSessionBestEffort(sessionId)
            throw error
        }
        try {
            emitEvent(WalletSessionEvent.issuance_offer_resolved)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock { sessions.remove(sessionId) }
                removePersistedActive(active)
            }
            throw error
        }
        return publicSession
    }

    /**
     * Starts the authorization-code browser request for an accepted session.
     *
     * PAR, state, PKCE and client-attestation material are created only at this boundary.
     */
    suspend fun beginAuthorization(sessionId: String, credentials: List<WalletCredentialSelection>? = null): WalletIssuanceAuthorization {
        check(loadActive(sessionId)?.public?.offer?.grant == WalletIssuanceGrant.AUTHORIZATION_CODE) {
            "Issuance session does not use the authorization-code grant"
        }
        val active = beginTransition(sessionId, SessionState.AWAITING_ACCEPTANCE)
            ?: throw IllegalStateException("Issuance session is not awaiting acceptance")
        try {
            acceptSelections(active, credentials)
            persistActive(active)
            val authorization = buildAuthorization(active)
            mutex.withLock {
                check(sessions[sessionId] === active && active.state == SessionState.PROCESSING) {
                    "Issuance session changed while authorization was being built"
                }
                active.authorization = authorization
                active.state = SessionState.AWAITING_CALLBACK
                active.expiresAtEpochMilliseconds = nowEpochMilliseconds() +
                        sessionPolicy.authorizationCallbackTtl.inWholeMilliseconds
                persistActive(active)
            }
            return authorization.public
        } catch (error: CancellationException) {
            invalidateActiveSessionBestEffort(sessionId)
            throw error
        } catch (error: IllegalArgumentException) {
            removeSession(sessionId)
            throw error
        } catch (error: Exception) {
            withContext(NonCancellable) {
                rollbackAuthorizationStart(active)
            }
            throw error
        }
    }

    /** Continues a pre-authorized session with the separately delivered transaction code. */
    suspend fun continuePreAuthorized(sessionId: String, transactionCode: String? = null, credentials: List<WalletCredentialSelection>? = null): WalletIssuanceOutcome {
        val active = try {
            beginTransition(sessionId, SessionState.AWAITING_ACCEPTANCE)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IssuanceStageException) {
            return failed(sessionId, error.code)
        } catch (_: Exception) {
            return failed(sessionId, WalletIssuanceErrorCode.STORAGE)
        } ?: return invalidSession(sessionId)
        val grant = active.resolved.offer?.grants?.preAuthorizedCode
            ?: return failAndRemove(sessionId, WalletIssuanceErrorCode.INVALID_SESSION)
        val requirement = grant.txCode
        if (requirement != null && transactionCode.isNullOrBlank()) {
            restoreSession(active, SessionState.AWAITING_ACCEPTANCE)
            return failed(sessionId, WalletIssuanceErrorCode.INVALID_INPUT)
        }
        // OpenID4VCI 1.0 §6.3: a tx_code may only be sent when the offer's grant requested one.
        // Issuers now reject an unsolicited tx_code, so never forward one the offer did not ask for.
        val effectiveTransactionCode = transactionCode?.takeIf { requirement != null }
        try {
            acceptSelections(active, credentials)
        } catch (error: CancellationException) {
            withContext(NonCancellable) { restoreSession(active, SessionState.AWAITING_ACCEPTANCE) }
            throw error
        } catch (_: IllegalArgumentException) {
            restoreSession(active, SessionState.AWAITING_ACCEPTANCE)
            return failed(sessionId, WalletIssuanceErrorCode.INVALID_INPUT)
        } catch (_: Exception) {
            restoreSession(active, SessionState.AWAITING_ACCEPTANCE)
            return failed(sessionId, WalletIssuanceErrorCode.CRYPTO)
        }
        try {
            persistActive(active)
        } catch (error: CancellationException) {
            withContext(NonCancellable) { restoreSession(active, SessionState.AWAITING_ACCEPTANCE) }
            throw error
        } catch (_: Exception) {
            return failAndRemove(sessionId, WalletIssuanceErrorCode.STORAGE)
        }
        return complete(active, retryTokenRejection = requirement != null) {
            tokenForPreAuthorized(active, grant.preAuthorizedCode, effectiveTransactionCode)
        }
    }

    /** Strictly validates and consumes an authorization callback before exchanging its code. */
    suspend fun continueAuthorization(callback: WalletIssuanceAuthorizationCallback): WalletIssuanceOutcome {
        val active = try {
            beginTransition(callback.sessionId, SessionState.AWAITING_CALLBACK)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IssuanceStageException) {
            return failed(callback.sessionId, error.code)
        } catch (_: Exception) {
            return failed(callback.sessionId, WalletIssuanceErrorCode.STORAGE)
        } ?: return invalidSession(callback.sessionId)
        val authorization = active.authorization
            ?: return failAndRemove(callback.sessionId, WalletIssuanceErrorCode.INVALID_SESSION)
        val parsed = try {
            AuthorizationResponseParser.parseAuthorizationResponse(
                redirectUri = callback.callbackUri,
                expectedState = authorization.public.state,
                expectedRedirectUri = active.request.redirectUri.toString(),
                expectedIssuer = active.resolved.authorizationServerMetadata.issuer,
                requireIssuer = active.resolved.authorizationServerMetadata.authorizationResponseIssParameterSupported == true,
            )
        } catch (error: AuthorizationResponseParser.AuthorizationErrorException) {
            return if (error.authError.error == "access_denied") {
                removeSession(callback.sessionId)
                WalletIssuanceOutcome.Cancelled(callback.sessionId)
            } else {
                failAndRemove(callback.sessionId, WalletIssuanceErrorCode.AUTHORIZATION_FAILED)
            }
        } catch (_: Exception) {
            return failAndRemove(callback.sessionId, WalletIssuanceErrorCode.INVALID_CALLBACK)
        }
        return complete(active) {
            tokenForAuthorizationCode(active, parsed.code, authorization.pkce.codeVerifier)
        }
    }

    /** Cancels idle work. A transition already processing a remote response must finish first. */
    suspend fun cancel(sessionId: String): WalletIssuanceOutcome {
        val active = try {
            loadActive(sessionId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IssuanceStageException) {
            return failed(sessionId, error.code)
        } catch (_: Exception) {
            return failed(sessionId, WalletIssuanceErrorCode.STORAGE)
        }
        return try {
            mutex.withLock {
                val persisted = sessionStore?.list().orEmpty().filter { it.sessionId == sessionId }
                val deferredIds = deferred.values.filter { it.sessionId == sessionId }.map { it.public.id } +
                    persisted.filter { it.kind == WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL }
                        .map { json.decodeFromString<PersistedDeferredRecord>(it.payload).public.id }
                if (active?.state == SessionState.PROCESSING || deferredIds.any { it in pollingDeferred } ||
                    persisted.any { it.kind == WalletIssuanceSessionRecordKind.ACTIVE_SESSION &&
                        json.decodeFromString<PersistedActiveSession>(it.payload).state == SessionState.PROCESSING } ||
                    persisted.any { it.kind == WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL &&
                        json.decodeFromString<PersistedDeferredRecord>(it.payload).operation != DeferredOperation.IDLE }) {
                    return@withLock invalidSession(sessionId)
                }
                for (record in persisted) {
                    if (!removeUnchangedRecord(record)) return@withLock invalidSession(sessionId)
                }
                val activeRemoved = sessions.remove(sessionId) != null
                val deferredRemoved = deferred.entries.removeAll { it.value.sessionId == sessionId }
                if (activeRemoved || deferredRemoved || persisted.isNotEmpty()) WalletIssuanceOutcome.Cancelled(sessionId)
                else invalidSession(sessionId)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            failed(sessionId, WalletIssuanceErrorCode.STORAGE)
        }
    }

    /**
     * Removes idle continuations. Rejects clearing while the shared runtime is issuing or polling so an
     * accepted remote response cannot recreate a continuation after clearing reports success.
     */
    suspend fun clearSessions() = clearSessions(close = false)

    /** Permanently stops this wallet runtime from accepting issuance before its stores are deleted. */
    suspend fun closeSessions() = clearSessions(close = true)

    internal suspend fun ensureOpen() = mutex.withLock {
        check(!runtime.closed) { "Wallet issuance is closed" }
    }

    private suspend fun clearSessions(close: Boolean) {
        withContext(NonCancellable) {
            mutex.withLock {
                if (runtime.closed) return@withLock
                check(pollingDeferred.isEmpty() && sessions.values.none { it.state == SessionState.PROCESSING }) {
                    "Cannot clear issuance sessions while issuance is in progress"
                }
                sessionStore?.let { store ->
                    val records = store.list()
                    check(records.none { it.kind == WalletIssuanceSessionRecordKind.ACTIVE_SESSION &&
                        runCatching { json.decodeFromString<PersistedActiveSession>(it.payload) }
                            .getOrNull()?.state == SessionState.PROCESSING }) {
                        "Cannot clear an issuance session with an unresolved operation"
                    }
                    check(records.none { it.kind == WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL &&
                        // Unreadable records cannot be resumed, but wallet deletion must still remove them.
                        runCatching { json.decodeFromString<PersistedDeferredRecord>(it.payload) }
                            .getOrNull()?.operation?.let { it != DeferredOperation.IDLE } == true }) {
                        "Cannot clear an issuance continuation with an unresolved operation"
                    }
                    records.forEach { record ->
                        check(removeUnchangedRecord(record)) { "Issuance continuation changed while clearing" }
                    }
                }
                sessions.clear()
                deferred.clear()
                runtime.closed = close
            }
        }
    }

    /** Lists retained work after cancellation, partial failure or process recreation without polling. */
    suspend fun listDeferredCredentials(): List<WalletDeferredCredential> {
        val results = linkedMapOf<String, WalletDeferredCredential>()
        val persistedRecords = sessionStore?.list().orEmpty().associateBy { it.id }
        persistedRecords.values
            .filter { it.kind == WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL }
            .forEach { record ->
                val persisted = json.decodeFromString<PersistedDeferredRecord>(record.payload)
                require(record.id == deferredRecordId(persisted.public.id) && record.sessionId == persisted.sessionId) {
                    "Stored deferred credential binding is invalid"
                }
                results[persisted.public.id] = persisted.public
            }
        // Unsaved progress remains valid only while its durable predecessor is still current.
        mutex.withLock {
            deferred.values.filter { record ->
                sessionStore == null || !record.persistable ||
                    persistedRecords[deferredRecordId(record.public.id)] == record.persistedSnapshot
            }.forEach { results[it.public.id] = it.public }
        }
        return results.values.toList()
    }

    /**
     * Polls one deferred result, or finishes saving an already received response.
     * Storage callbacks apply only to credentials not already committed by an earlier attempt.
     */
    suspend fun resumeDeferred(
        deferredCredentialId: String,
        beforeCredentialsStored: suspend (Int) -> Unit = {},
        onCredentialStored: suspend (StoredCredential) -> Unit = {},
    ): WalletIssuanceOutcome {
        if (!mutex.withLock { !runtime.closed && pollingDeferred.add(deferredCredentialId) }) return invalidSession(deferredCredentialId)
        try {
            return resumeDeferredClaimed(deferredCredentialId, beforeCredentialsStored, onCredentialStored).also {
                if (it is WalletIssuanceOutcome.Stored) emitEvent(WalletSessionEvent.issuance_completed)
            }
        } finally {
            withContext(NonCancellable) { mutex.withLock { pollingDeferred.remove(deferredCredentialId) } }
        }
    }

    private suspend fun resumeDeferredClaimed(
        id: String,
        beforeCredentialsStored: suspend (Int) -> Unit,
        onCredentialStored: suspend (StoredCredential) -> Unit,
    ): WalletIssuanceOutcome {
        var record = try {
            loadDeferred(id)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return failed(id, WalletIssuanceErrorCode.STORAGE)
        } ?: return invalidSession(id)
        // Observing another writer must never enter cleanup that can release its claim.
        when (record.operation) {
            DeferredOperation.POLLING -> return failed(record.sessionId,
                WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN, deferredCredentials = listOf(record.public))
            DeferredOperation.STORING -> {
                val committedIds = try {
                    requireNotNull(record.receivedCredentials).filter {
                        wallet.findCredential(it.id) != null
                    }.map { it.id }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    return failed(record.sessionId, WalletIssuanceErrorCode.STORAGE,
                        deferredCredentials = listOf(record.public))
                }
                return failed(record.sessionId, WalletIssuanceErrorCode.STORAGE_OUTCOME_UNCERTAIN,
                    committedIds, listOf(record.public))
            }
            DeferredOperation.IDLE -> Unit
        }
        val storedIds = mutableListOf<String>()
        var stage = WalletIssuanceErrorCode.NETWORK
        return try {
            if (record.receivedCredentials == null) {
                val request = requireNotNull(record.request)
                val current = nowEpochMilliseconds()
                if (current < request.nextPollAtEpochMilliseconds) {
                    val remaining = (Instant.fromEpochMilliseconds(request.nextPollAtEpochMilliseconds) -
                        Instant.fromEpochMilliseconds(current)).inWholeMilliseconds
                    val seconds = remaining / 1000 + if (remaining % 1000 == 0L) 0 else 1
                    return WalletIssuanceOutcome.Deferred(record.sessionId, emptyList(),
                        listOf(record.public.copy(intervalSeconds = seconds)))
                }
                // Claim before contacting the issuer. A lost response must never become an automatic repoll.
                val claimed = record.copy(operation = DeferredOperation.POLLING)
                retainDeferred(claimed, preserveOnFailure = false)
                record = claimed
                val response = postProtected(
                    endpoint = request.endpoint, accessToken = request.accessToken, tokenType = request.tokenType,
                    dpop = request.dpop, keyMaterial = request.keyMaterial, dpopNonce = request.dpopNonce,
                    body = buildJsonObject { put("transaction_id", request.transactionId) }.toString(),
                )
                record = record.copy(request = request.copy(dpopNonce = response.dpopNonce))
                stage = WalletIssuanceErrorCode.PROTOCOL
                if (response.response.status == HttpStatusCode.Accepted) {
                    val pending = response.response.body<CredentialResponse>()
                        .validateCredentialResponse(response.response.status.value, request.transactionId)
                    record = record.copy(
                        public = record.public.copy(intervalSeconds = pending.interval),
                        request = requireNotNull(record.request).copy(
                            nextPollAtEpochMilliseconds = nextDeferredPoll(requireNotNull(pending.interval))),
                        operation = DeferredOperation.IDLE,
                    )
                    retainDeferred(record)
                    return WalletIssuanceOutcome.Deferred(record.sessionId, emptyList(), listOf(record.public))
                }
                if (response.response.status != HttpStatusCode.OK) {
                    // An explicit terminal denial consumes the continuation; transient failures retain it.
                    val terminal = response.oauthError in setOf("invalid_transaction_id", "credential_request_denied", "invalid_token")
                    record = record.copy(operation = DeferredOperation.IDLE)
                    if (terminal) removeDeferred(record) else retainDeferred(record)
                    return failed(record.sessionId, WalletIssuanceErrorCode.ISSUER_RESPONSE,
                        deferredCredentials = if (terminal) emptyList() else listOf(record.public))
                }
                val issued = response.response.body<CredentialResponse>()
                    .validateCredentialResponse(response.response.status.value, request.transactionId)
                val credentials = requireNotNull(issued.credentials)
                val prepared = wallet.prepareIssuedCredentials(credentials.map {
                    val value = it.credential
                    if (value is JsonPrimitive) value.content else value.toString()
                }, request.bindings, request.label, request.metadata, request.proofRequired,
                    expectedConfiguration = request.configuration)
                record = record.copy(request = null, receivedCredentials = prepared, operation = DeferredOperation.IDLE,
                    public = record.public.copy(intervalSeconds = null))
            }
            stage = WalletIssuanceErrorCode.STORAGE
            // Claim local writes before touching the credential store or quota callbacks. The received
            // response and stable IDs are checkpointed with the claim, without an unowned interval.
            record = record.copy(operation = DeferredOperation.STORING)
            retainDeferred(record)
            currentCoroutineContext().ensureActive()
            val pendingWrites = mutableListOf<StoredCredential>()
            for (credential in requireNotNull(record.receivedCredentials)) {
                if (wallet.findCredential(credential.id) == null) pendingWrites += credential
                else storedIds += credential.id
            }
            if (pendingWrites.isNotEmpty()) beforeCredentialsStored(pendingWrites.size)
            for (credential in pendingWrites) {
                currentCoroutineContext().ensureActive()
                wallet.addCredential(credential)
                storedIds += credential.id
                emitEvent(WalletSessionEvent.issuance_credential_stored)
                onCredentialStored(credential)
            }
            removeDeferred(record)
            WalletIssuanceOutcome.Stored(record.sessionId, storedIds)
        } catch (error: CancellationException) {
            retainInterruptedDeferred(record)
            throw error
        } catch (error: Exception) {
            retainInterruptedDeferred(record)
            val errorCode = (error as? IssuanceStageException)?.code ?: stage
            val code = if (errorCode == WalletIssuanceErrorCode.NETWORK && record.operation == DeferredOperation.POLLING)
                WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN else errorCode
            failed(record.sessionId, code,
                storedIds, listOf(record.public))
        }
    }

    private suspend fun retainInterruptedDeferred(record: DeferredRecord) {
        // A returned/throwing local write is finished. Keep the prepared response for replay with the
        // same IDs. A lost remote response remains claimed because its issuer side effect is unknown.
        val retained = if (record.operation == DeferredOperation.STORING)
            record.copy(operation = DeferredOperation.IDLE) else record
        withContext(NonCancellable) {
            try {
                retainDeferred(retained)
            } catch (_: Exception) {
                // retainDeferred preserves the response in the owning runtime if checkpointing fails.
            }
        }
    }

    private suspend fun complete(
        active: ActiveSession,
        retryTokenRejection: Boolean = false,
        obtainToken: suspend () -> TokenRequestBuilder.TokenResponse,
    ): WalletIssuanceOutcome {
        val storedIds = mutableListOf<String>()
        val deferredResults = mutableListOf<WalletDeferredCredential>()
        return try {
            val advertisedDpop = active.dpopAlgorithms()
            val token = obtainToken()
            val dpop = dpopAlgorithmsForToken(token.token_type, advertisedDpop)
            emitEvent(WalletSessionEvent.issuance_token_obtained)

            issueCredentials(active, token, dpop, storedIds, deferredResults)
            removeSession(active.public.id)
            if (deferredResults.isNotEmpty()) {
                WalletIssuanceOutcome.Deferred(active.public.id, storedIds, deferredResults.toList())
            } else {
                emitEvent(WalletSessionEvent.issuance_completed)
                WalletIssuanceOutcome.Stored(active.public.id, storedIds)
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                removeSession(active.public.id)
            }
            throw error
        } catch (error: TokenRequestException) {
            log.warn {
                "Issuance token request failed: status=${error.statusCode}, " +
                        "oauthError=${error.oauthError}, grant=${active.public.offer.grant}, " +
                        "clientId=${active.request.clientId}, dpopAdvertised=${active.dpopAlgorithms() != null}"
            }
            if (
                retryTokenRejection &&
                storedIds.isEmpty() &&
                error.oauthError == "invalid_grant"
            ) {
                restoreSession(active, SessionState.AWAITING_ACCEPTANCE)
            } else {
                removeSession(active.public.id)
            }
            failed(
                active.public.id,
                if (error.statusCode == 0) WalletIssuanceErrorCode.NETWORK else WalletIssuanceErrorCode.ISSUER_RESPONSE,
                storedIds,
            )
        } catch (error: IssuanceStageException) {
            removeSession(active.public.id)
            failed(active.public.id, error.code, storedIds, deferredResults, error.failure)
        } catch (_: IllegalArgumentException) {
            removeSession(active.public.id)
            failed(active.public.id, WalletIssuanceErrorCode.PROTOCOL, storedIds, deferredResults)
        } catch (_: Exception) {
            removeSession(active.public.id)
            failed(active.public.id, WalletIssuanceErrorCode.ISSUER_RESPONSE, storedIds, deferredResults)
        }
    }

    private suspend fun issueCredentials(
        active: ActiveSession,
        token: TokenRequestBuilder.TokenResponse,
        dpopAlgorithms: Set<String>?,
        storedIds: MutableList<String>,
        deferredResults: MutableList<WalletDeferredCredential>,
    ) {
        val metadata = active.resolved.issuerMetadata.metadata
        val targets = grantedCredentialSelections(metadata, requireNotNull(active.selections), token.authorization_details, token.scope)
        for ((index, selection) in targets.withIndex()) {
            val (target, selected) = selection
            var stage = CredentialIssuanceStage.PROOF
            try {
                val offered = active.resolved.offeredCredentials.first { it.credentialConfigurationId == target.credentialConfigurationId }
                val proofRequired = supportedJwtProofAlgorithms(offered.configuration.proofTypesSupported, wallet.attachedKeyAttestationProvider() != null) != null
                suspend fun requestWithFreshProof(): ProtectedResponse {
                    val proofs = if (proofRequired) {
                        stage = CredentialIssuanceStage.REQUEST
                        val nonce = fetchNonce(metadata)
                        stage = CredentialIssuanceStage.PROOF
                        try {
                            WalletIssuanceHandler.buildProofCollection(wallet, selected, offered.configuration, metadata.credentialIssuer, nonce,
                                active.request.clientId.takeUnless { active.tokenRequestAnonymous })
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            throw IssuanceStageException(WalletIssuanceErrorCode.CRYPTO, error)
                        }.also { emitEvent(WalletSessionEvent.issuance_proof_signed) }
                    } else null
                    stage = CredentialIssuanceStage.REQUEST
                    return postProtected(
                        endpoint = metadata.credentialEndpoint, accessToken = token.access_token,
                        tokenType = token.token_type, dpop = dpopAlgorithms, keyMaterial = active.keyMaterial,
                        dpopNonce = null, body = CredentialRequestBuilder.build(target, proofs).toString())
                }
                var protected = requestWithFreshProof()
                if (protected.oauthError == "invalid_nonce" && proofRequired && metadata.nonceEndpoint != null) {
                    protected = requestWithFreshProof()
                }
                stage = CredentialIssuanceStage.RESPONSE
                when (protected.response.status) {
                    HttpStatusCode.OK -> {
                        val response = try {
                            protected.response.body<CredentialResponse>().validateCredentialResponse(protected.response.status.value)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL, error)
                        }
                        val credentials = response.credentials
                            ?: throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL)
                        emitEvent(WalletSessionEvent.issuance_credential_received)
                        val label = active.public.offer.credentialName(offered.credentialConfigurationId)
                        val metadata = storedCredentialDisplayMetadata(
                            issuerMetadata = active.resolved.issuerMetadata.metadata,
                            credentialConfigurationId = offered.credentialConfigurationId,
                        )
                        val prepared = wallet.prepareIssuedCredentials(credentials.map {
                            val value = it.credential
                            if (value is JsonPrimitive) value.content else value.toString()
                        }, selected.bindings, label, metadata, proofRequired, expectedConfiguration = offered.configuration)
                        stage = CredentialIssuanceStage.STORAGE
                        val outcome = storeReceivedCredentials(
                            prepared, target.credentialConfigurationId, target.credentialIdentifier,
                            persistable = active.persistable, sessionId = active.public.id,
                            beforeCredentialsStored = {}, onCredentialStored = { storedIds += it.id },
                        )
                        if (outcome is WalletIssuanceOutcome.Failed) {
                            deferredResults += outcome.deferredCredentials
                            throw IssuanceStageException(outcome.error.code)
                        }
                    }

                    HttpStatusCode.Accepted -> {
                        val response = try {
                            protected.response.body<CredentialResponse>().validateCredentialResponse(protected.response.status.value)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL, error)
                        }
                        val transactionId = response.transactionId
                            ?: throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL)
                        val deferredEndpoint = active.resolved.issuerMetadata.metadata.deferredCredentialEndpoint
                            ?: throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL)
                        val public = WalletDeferredCredential(
                            credentialIdentifier = target.credentialIdentifier,
                            id = Uuid.random().toString(),
                            credentialConfigurationId = offered.credentialConfigurationId,
                            intervalSeconds = response.interval,
                        )
                        val label = active.public.offer.credentialName(offered.credentialConfigurationId)
                        val metadata = storedCredentialDisplayMetadata(
                            issuerMetadata = active.resolved.issuerMetadata.metadata,
                            credentialConfigurationId = offered.credentialConfigurationId,
                        )
                        val record = DeferredRecord(
                            public = public,
                            sessionId = active.public.id,
                            persistable = active.persistable,
                            request = DeferredRequest(
                                configuration = offered.configuration,
                                bindings = selected.bindings,
                                proofRequired = proofRequired,
                                endpoint = deferredEndpoint,
                                transactionId = transactionId,
                                accessToken = token.access_token,
                                tokenType = token.token_type,
                                dpop = dpopAlgorithms,
                                dpopNonce = protected.dpopNonce,
                                keyMaterial = active.keyMaterial,
                                keyId = active.keyId,
                                selectedPublicJwk = active.keyMaterial.exportPublicJwkObject().toString(),
                                label = label,
                                metadata = metadata,
                                nextPollAtEpochMilliseconds = nextDeferredPoll(requireNotNull(response.interval)),
                            ),
                        )
                        // The issuer has accepted this target. Retain it before another target can fail.
                        stage = CredentialIssuanceStage.STORAGE
                        withContext(NonCancellable) {
                            deferredResults += public
                            retainDeferred(record)
                        }
                        emitEvent(WalletSessionEvent.issuance_deferred)
                    }

                    else -> {
                        log.warn {
                            "Issuance credential request failed: status=${protected.response.status.value}, " +
                                    "oauthError=${protected.oauthError}, " +
                                    "credentialConfigurationId=${offered.credentialConfigurationId}, " +
                                    "tokenType=${token.token_type}, dpopSent=${dpopAlgorithms != null}"
                        }
                        throw IssuanceStageException(WalletIssuanceErrorCode.ISSUER_RESPONSE)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val code = (error as? IssuanceStageException)?.code ?: when {
                    error is IllegalArgumentException -> WalletIssuanceErrorCode.PROTOCOL
                    stage == CredentialIssuanceStage.STORAGE -> WalletIssuanceErrorCode.STORAGE
                    else -> WalletIssuanceErrorCode.ISSUER_RESPONSE
                }
                throw IssuanceStageException(code, error,
                    CredentialIssuanceFailure(target, stage, targets.drop(index + 1).map { it.first }))
            }
        }
    }

    private suspend fun tokenForPreAuthorized(
        active: ActiveSession,
        preAuthorizedCode: String,
        transactionCode: String?,
    ): TokenRequestBuilder.TokenResponse {
        val metadata = active.resolved.authorizationServerMetadata
        val tokenEndpoint = requireNotNull(metadata.tokenEndpoint) { "Authorization server has no token endpoint" }
        val attestationJwt = obtainAttestationJwt(active)
        val anonymous = metadata.preAuthorizedGrantAnonymousAccessSupported == true &&
            active.request.tokenRequestHeaders.isEmpty() && attestationJwt == null
        active.tokenRequestAnonymous = anonymous
        val token = TokenRequestBuilder(active.clientConfiguration(), httpClient).exchangePreAuthorizedCode(
            tokenEndpoint = tokenEndpoint,
            preAuthorizedCode = preAuthorizedCode,
            txCode = transactionCode,
            additionalParameters = CredentialRequestBuilder.preAuthorizedTokenParameters(
                active.resolved.issuerMetadata.metadata,
                requireNotNull(active.selections).map { it.selection.credentialConfigurationId },
                metadata,
            ),
            additionalHeaders = active.request.tokenRequestHeaders,
            attestationHeadersFactory = attestationJwt?.let { reusableAttestationJwt ->
                { buildAttestationHeaders(active, reusableAttestationJwt) }
            },
            anonymous = anonymous,
            dpopProofFactory = active.dpopFactory(),
            onResponseHeaders = { headers ->
                rememberAttestationChallenge(
                    active,
                    headers[CLIENT_ATTESTATION_CHALLENGE],
                )
            },
        )
        return token
    }

    private suspend fun tokenForAuthorizationCode(
        active: ActiveSession,
        code: String,
        codeVerifier: String,
    ): TokenRequestBuilder.TokenResponse {
        val metadata = active.resolved.authorizationServerMetadata
        val tokenEndpoint = requireNotNull(metadata.tokenEndpoint) { "Authorization server has no token endpoint" }
        val attestationJwt = obtainAttestationJwt(active)
        active.tokenRequestAnonymous = false
        val token = TokenRequestBuilder(active.clientConfiguration(), httpClient).exchangeAuthorizationCode(
            tokenEndpoint = tokenEndpoint,
            code = code,
            codeVerifier = codeVerifier,
            additionalHeaders = active.request.tokenRequestHeaders,
            attestationHeadersFactory = attestationJwt?.let { reusableAttestationJwt ->
                { buildAttestationHeaders(active, reusableAttestationJwt) }
            },
            dpopProofFactory = active.dpopFactory(),
            onResponseHeaders = { headers ->
                rememberAttestationChallenge(
                    active,
                    headers[CLIENT_ATTESTATION_CHALLENGE],
                )
            },
        )
        return token
    }

    /**
     * Advertised DPoP algorithms, without checking that the wallet key can sign them.
     *
     * Deliberately unlike [usableDpopAlgorithms], which the server-side [WalletIssuanceHandler] uses
     * to fall back to a Bearer token. A session wallet's key is fixed instance material, so an issuer
     * demanding algorithms it cannot produce is a misconfiguration worth surfacing rather than
     * silently downgrading the security posture the wallet was built for. `dpopFactory` therefore
     * raises `WalletIssuanceErrorCode.CRYPTO` before any token request is attempted - asserted by
     * `unsupportedDpopKeyInPreAuthorizedGrantFailsAsCryptoBeforeTokenRequest`.
     */
    private fun ActiveSession.dpopAlgorithms(): Set<String>? =
        resolved.authorizationServerMetadata.dpopSigningAlgorithms()

    private fun ActiveSession.dpopFactory(): DPoPProofFactory? =
        dpopAlgorithms()?.let { algorithms ->
            { endpoint: String, nonce: String? ->
                try {
                    buildDpopProof(
                        keyMaterial = keyMaterial,
                        algorithms = algorithms,
                        endpoint = endpoint,
                        nonce = nonce,
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw IssuanceStageException(WalletIssuanceErrorCode.CRYPTO, error)
                }
            }
        }

    private suspend fun acceptSelections(active: ActiveSession, credentials: List<WalletCredentialSelection>?) {
        active.selections = wallet.resolveCredentialSelections(credentials,
            active.resolved.offeredCredentials.map { it.credentialConfigurationId },
            active.resolved.issuerMetadata.metadata, active.keyMaterial, active.did)
    }

    @Serializable
    private data class PersistedHolderBinding(val keyId: String, val did: String?, val publicJwk: String)
    @Serializable
    private data class PersistedCredentialSelection(
        val selection: WalletCredentialSelection,
        val bindings: List<PersistedHolderBinding>,
    )
    private suspend fun persistBindings(bindings: List<ResolvedCredentialHolderBinding>): List<PersistedHolderBinding> =
        bindings.map { PersistedHolderBinding(it.material.keyId, it.did, it.material.exportPublicJwkObject().toString()) }
    private suspend fun restoreBindings(bindings: List<PersistedHolderBinding>): List<ResolvedCredentialHolderBinding> =
        bindings.map { ResolvedCredentialHolderBinding(resolvePersistedKeyMaterial(it.keyId, it.publicJwk), it.did) }

    private suspend fun buildAuthorization(active: ActiveSession): AuthorizationState {
        val resolved = active.resolved
        val request = active.request
        val keyMaterial = active.keyMaterial
        val metadata = resolved.authorizationServerMetadata
        val endpoint = requireNotNull(metadata.authorizationEndpoint) {
            "Authorization server has no authorization endpoint"
        }
        val builder = AuthorizationRequestBuilder(
            ClientConfiguration(request.clientId, listOf(request.redirectUri.toString()))
        )
        val credentialConfigurationIds = requireNotNull(active.selections).map { it.selection.credentialConfigurationId }.distinct()
        val issuerState = resolved.offer?.grants?.authorizationCode?.issuerState
        val parEndpoint = metadata.pushedAuthorizationRequestEndpoint
        require(metadata.requirePushedAuthorizationRequests != true || parEndpoint != null) {
            "Authorization server requires PAR but does not advertise an endpoint"
        }
        val dpopJkt = dpopAlgorithmsForAuthorization(metadata, keyMaterial)?.let {
            keyMaterial.jwkThumbprint()
        }

        if (parEndpoint != null) {
            val pushed = builder.buildPushedAuthorizationRequestStateForCredentialConfigurations(
                credentialConfigurationIds = credentialConfigurationIds,
                credentialIssuerLocations = resolved.issuerMetadata.metadata.authorizationServers?.takeIf { it.isNotEmpty() }
                    ?.let { listOf(resolved.issuerMetadata.metadata.credentialIssuer) },
                scope = CredentialRequestBuilder.authorizationScope(resolved.issuerMetadata.metadata, credentialConfigurationIds, metadata),
                issuerState = issuerState,
                usePKCE = true,
                metadata = metadata,
                redirectUri = request.redirectUri.toString(),
                dpopJkt = dpopJkt,
            )
            val attestation = obtainAttestationJwt(active)?.let { reusableAttestationJwt ->
                buildAttestationHeaders(active, reusableAttestationJwt)
            }
            val par = PushedAuthorizationRequestExecutor.execute(
                httpClient = httpClient,
                parEndpoint = parEndpoint,
                parameters = pushed.parameters,
                attestationHeaders = attestation,
                onResponseHeaders = { headers ->
                    rememberAttestationChallenge(
                        active,
                        headers[CLIENT_ATTESTATION_CHALLENGE],
                    )
                },
            )
            val browserUrl = URLBuilder(endpoint).apply {
                parameters.append("client_id", request.clientId)
                parameters.append("request_uri", par.requestUri)
            }.buildString()
            val pkce = requireNotNull(pushed.pkceData)
            return AuthorizationState(
                public = WalletIssuanceAuthorization(
                    url = browserUrl,
                    state = pushed.state,
                    redirectUri = request.redirectUri.toString(),
                    pkce = WalletIssuancePkceState(
                        codeChallenge = pkce.codeChallenge,
                        codeChallengeMethod = pkce.codeChallengeMethod.value,
                    ),
                    pushedAuthorizationRequestUsed = true,
                    requestUriExpiresAtEpochMilliseconds = nowEpochMilliseconds() +
                            par.expiresIn * 1000L,
                ),
                pkce = pkce,
            )
        }

        val direct = builder.buildAuthorizationRequestForCredentialConfigurations(
            authorizationEndpoint = endpoint,
            credentialConfigurationIds = credentialConfigurationIds,
            credentialIssuerLocations = resolved.issuerMetadata.metadata.authorizationServers?.takeIf { it.isNotEmpty() }
                ?.let { listOf(resolved.issuerMetadata.metadata.credentialIssuer) },
            scope = CredentialRequestBuilder.authorizationScope(resolved.issuerMetadata.metadata, credentialConfigurationIds, metadata),
            issuerState = issuerState,
            usePKCE = true,
            metadata = metadata,
            redirectUri = request.redirectUri.toString(),
            dpopJkt = dpopJkt,
        )
        val pkce = requireNotNull(direct.pkceData)
        return AuthorizationState(
            public = WalletIssuanceAuthorization(
                url = direct.url,
                state = direct.state,
                redirectUri = request.redirectUri.toString(),
                pkce = WalletIssuancePkceState(
                    codeChallenge = pkce.codeChallenge,
                    codeChallengeMethod = pkce.codeChallengeMethod.value,
                ),
                pushedAuthorizationRequestUsed = false,
                requestUriExpiresAtEpochMilliseconds = null,
            ),
            pkce = pkce,
        )
    }

    private suspend fun dpopAlgorithmsForAuthorization(
        metadata: AuthorizationServerMetadata,
        keyMaterial: WalletKeyStoreEntry,
    ): Set<String>? {
        val algorithms = metadata.dpopSigningAlgValuesSupported
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        keyMaterial.requireCrypto2SigningKey().selectJwsAlgorithm(algorithms)
        return algorithms
    }

    private suspend fun resolve(
        request: WalletIssuanceSessionRequest,
        preferredLocales: List<String>,
    ): ResolvedOffer {
        val offer = if (request.credentialIssuer != null) null else if (request.offerJson != null) {
            val inline = json.decodeFromString<CredentialOffer>(request.offerJson.toString())
            CredentialOfferResolver(httpClient).resolveCredentialOffer(inline, null)
        } else {
            val parsed = CredentialOfferParser.parseCredentialOfferUrl(request.getEffectiveOfferString())
            CredentialOfferResolver(httpClient).resolveCredentialOffer(parsed.credentialOffer, parsed.credentialOfferUri)
        }
        val resolver = IssuerMetadataResolver(httpClient, metadataTrustResolver)
        // Operational metadata is always resolved from issuer-derived well-known endpoints.
        // Unrecognized offer parameters are caller-controlled and must not select network
        // endpoints for issuance.
        val issuerMetadata = resolver.resolveCredentialIssuerMetadata(
            credentialIssuerUrl = request.credentialIssuer ?: requireNotNull(offer).credentialIssuer,
            preferredLocales = preferredLocales,
        )
        require(issuerMetadata.metadata.credentialIssuer == (request.credentialIssuer ?: requireNotNull(offer).credentialIssuer)) {
            "Credential issuer metadata identifier does not match the offer"
        }
        val grantAuthorizationServer = when (offer?.getGrantType()) {
            is GrantType.AuthorizationCode -> offer?.grants?.authorizationCode?.authorizationServer
            is GrantType.PreAuthorizedCode -> offer?.grants?.preAuthorizedCode?.authorizationServer
            else -> null
        }
        val selectedAuthorizationServer = selectAuthorizationServer(issuerMetadata.metadata, grantAuthorizationServer)
        val authorizationServerMetadata = resolver.resolveAuthorizationServerMetadata(selectedAuthorizationServer)
        require(authorizationServerMetadata.issuer == selectedAuthorizationServer) {
            "Authorization server metadata issuer does not match the selected server"
        }
        val offered = if (offer != null) OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata.metadata)
            else requireNotNull(request.credentialConfigurationIds).map {
                OfferedCredentialResolver.ResolvedCredentialOffer(it, issuerMetadata.metadata.credentialConfigurationsSupported.getValue(it))
            }
        require(offered.isNotEmpty()) { "Credential offer resolved no supported credentials" }
        return ResolvedOffer(offer, issuerMetadata, authorizationServerMetadata, offered)
    }

    private fun selectAuthorizationServer(
        issuerMetadata: CredentialIssuerMetadata,
        grantAuthorizationServer: String?,
    ): String {
        val declared = issuerMetadata.authorizationServerIssuers()
        if (grantAuthorizationServer == null) return declared.first()
        require(issuerMetadata.authorizationServers?.size?.let { it > 1 } == true) {
            "Offer authorization_server is only valid when issuer metadata declares multiple servers"
        }
        require(grantAuthorizationServer in declared) {
            "Offer authorization_server is not declared by the credential issuer"
        }
        return grantAuthorizationServer
    }

    private fun ResolvedOffer.toPreview(
        grant: GrantType,
        preferredLocales: List<String>,
    ): WalletIssuanceOfferPreview {
        val issuerDisplay = LocalizedMetadata.select(issuerMetadata.metadata.display, preferredLocales) { it.locale }
        val txCode = offer?.grants?.preAuthorizedCode?.txCode
        return WalletIssuanceOfferPreview(
            grant = when (grant) {
                is GrantType.AuthorizationCode -> WalletIssuanceGrant.AUTHORIZATION_CODE
                is GrantType.PreAuthorizedCode -> WalletIssuanceGrant.PRE_AUTHORIZED_CODE
                else -> error("Unsupported credential offer grant")
            },
            issuer = WalletIssuanceIssuerPreview(
                identifier = issuerMetadata.metadata.credentialIssuer,
                name = issuerDisplay?.name,
                locale = issuerDisplay?.locale,
                logoUri = issuerDisplay?.logo?.uri,
                logoAltText = issuerDisplay?.logo?.altText,
                metadataProvenance = issuerMetadata.toPreviewProvenance(),
            ),
            credentials = offeredCredentials.map { offered ->
                val display = LocalizedMetadata.select(
                    offered.configuration.credentialMetadata?.display,
                    preferredLocales,
                ) { it.locale }
                WalletIssuanceCredentialPreview(
                    configurationId = offered.credentialConfigurationId,
                    format = offered.configuration.format.value,
                    name = display?.name,
                    descriptionText = display?.description,
                    logoUri = display?.logo?.uri,
                    logoAltText = display?.logo?.altText,
                    backgroundColor = display?.backgroundColor,
                    backgroundImageUri = display?.backgroundImage?.uri,
                    textColor = display?.textColor,
                    vct = offered.configuration.vct,
                    doctype = offered.configuration.doctype,
                )
            },
            batchSize = issuerMetadata.metadata.batchCredentialIssuance?.batchSize,
            transactionCode = txCode?.let {
                WalletIssuanceTransactionCode(it.inputMode, it.length, it.description)
            },
        )
    }

    private fun ResolvedCredentialIssuerMetadata.toPreviewProvenance(): WalletIssuanceMetadataProvenance = when (this) {
        is ResolvedCredentialIssuerMetadata.Unsigned -> WalletIssuanceMetadataProvenance.Unsigned
        is ResolvedCredentialIssuerMetadata.Signed -> WalletIssuanceMetadataProvenance.Signed(
            compactJwt = compactJwt,
            algorithm = signer.algorithm,
            keyId = signer.keyId,
            trustType = signer.trustType,
        )
    }

    private fun WalletIssuanceOfferPreview.credentialName(configurationId: String): String? =
        credentials.firstOrNull { it.configurationId == configurationId }?.name

    private fun publicJwkMatches(first: JsonObject, second: JsonObject): Boolean {
        // Compare RFC JWK public members directly. Platform-backed keys and imported JWKs may
        // calculate thumbprints through different providers even when their public keys are equal.
        val fields = when (first["kty"]?.jsonPrimitive?.contentOrNull) {
            "EC" -> listOf("kty", "crv", "x", "y")
            "RSA" -> listOf("kty", "n", "e")
            "OKP" -> listOf("kty", "crv", "x")
            else -> return false
        }
        return fields.all { field -> first[field] != null && first[field] == second[field] }
    }

    private suspend fun fetchNonce(metadata: CredentialIssuerMetadata): String? {
        val endpoint = metadata.nonceEndpoint
            ?: return null
        return try {
            NonceRequestBuilder(httpClient).requestNonce(endpoint).cNonce
        } catch (error: NonceRequestException) {
            val code = when (error.error) {
                NonceRequestError.INVALID_ENDPOINT -> WalletIssuanceErrorCode.ISSUER_METADATA
                NonceRequestError.NETWORK -> WalletIssuanceErrorCode.NETWORK
                NonceRequestError.ISSUER_RESPONSE -> WalletIssuanceErrorCode.ISSUER_RESPONSE
                NonceRequestError.INVALID_RESPONSE -> WalletIssuanceErrorCode.PROTOCOL
            }
            throw IssuanceStageException(code, error)
        }
    }

    private suspend fun postProtected(
        endpoint: String,
        accessToken: String,
        tokenType: String,
        dpop: Set<String>?,
        keyMaterial: WalletKeyStoreEntry,
        dpopNonce: String?,
        body: String,
    ): ProtectedResponse {
        var nonce = dpopNonce
        repeat(DPOP_NONCE_ATTEMPTS) { attempt ->
            val proof = try {
                dpop?.let { algorithms ->
                    buildDpopProof(
                        keyMaterial = keyMaterial,
                        algorithms = algorithms,
                        endpoint = endpoint,
                        accessToken = accessToken,
                        nonce = nonce,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw IssuanceStageException(WalletIssuanceErrorCode.CRYPTO, error)
            }
            val response = try {
                httpClient.post(endpoint) {
                    header(HttpHeaders.Authorization, "${authorizationScheme(tokenType)} $accessToken")
                    proof?.let { header(DPOP_HEADER, it) }
                    contentType(ContentType.Application.Json)
                    setBody(body)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw IssuanceStageException(WalletIssuanceErrorCode.NETWORK, error)
            }
            val oauthError = response.oauthErrorCode()
            val suppliedNonce = response.headers[DPOP_NONCE_HEADER]
            if (
                attempt == 0 &&
                proof != null &&
                oauthError == USE_DPOP_NONCE &&
                !suppliedNonce.isNullOrBlank()
            ) {
                nonce = suppliedNonce
                return@repeat
            }
            return ProtectedResponse(response, suppliedNonce ?: nonce, oauthError)
        }
        error("DPoP nonce retry exhausted")
    }

    private suspend fun obtainAttestationJwt(active: ActiveSession): String? {
        val metadata = active.resolved.authorizationServerMetadata
        val assembler = attestationAssembler ?: return null
        if (metadata.tokenEndpointAuthMethodsSupported
                ?.contains(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH) != true
        ) {
            return null
        }
        val advertisedAttestationAlgorithms = metadata.clientAttestationSigningAlgValuesSupported
        metadata.clientAttestationPopSigningAlgValuesSupported?.let { advertised ->
            require(ClientAttestationSigningAlgorithms.ES256 in advertised) {
                "Authorization server does not advertise a supported client attestation PoP signing algorithm"
            }
        }
        val crypto2Key = active.keyMaterial.requireCrypto2SigningKey()
        return assembler.obtainAttestationJwt(crypto2Key, active.request.clientId).also { attestationJwt ->
            advertisedAttestationAlgorithms?.let { advertised ->
                validateClientAttestationAlgorithm(attestationJwt, advertised)
            }
            emitEvent(WalletSessionEvent.issuance_attestation_obtained)
        }
    }

    private suspend fun buildAttestationHeaders(
        active: ActiveSession,
        attestationJwt: String,
    ): ClientAttestationHeaders {
        val assembler = requireNotNull(attestationAssembler)
        val metadata = active.resolved.authorizationServerMetadata
        val challenge = active.attestationChallenge ?: metadata.challengeEndpoint?.let { endpoint ->
            try {
                WalletAttestationChallengeRequestBuilder(httpClient).requestChallenge(endpoint).attestationChallenge
            } catch (error: CancellationException) {
                throw error
            } catch (error: WalletAttestationChallengeRequestException) {
                val code = when (error.error) {
                    WalletAttestationChallengeRequestError.INVALID_ENDPOINT -> WalletIssuanceErrorCode.ISSUER_METADATA
                    WalletAttestationChallengeRequestError.NETWORK -> WalletIssuanceErrorCode.NETWORK
                    WalletAttestationChallengeRequestError.AUTHORIZATION_SERVER_RESPONSE ->
                        WalletIssuanceErrorCode.ISSUER_RESPONSE
                    WalletAttestationChallengeRequestError.INVALID_RESPONSE -> WalletIssuanceErrorCode.PROTOCOL
                }
                throw IssuanceStageException(code, error)
            }
        }
        if (challenge != active.attestationChallenge) {
            active.attestationChallenge = challenge
            persistActive(active)
        }
        val crypto2Key = active.keyMaterial.requireCrypto2SigningKey()
        val popJwt = assembler.buildPopJwt(crypto2Key, active.request.clientId, metadata.issuer, challenge)
        return ClientAttestationHeaders(attestationJwt, popJwt)
    }

    private fun validateClientAttestationAlgorithm(jwt: String, advertised: Set<String>) {
        val algorithm = try {
            CompactJws.decodeUnverified(jwt).algorithm.identifier
        } catch (_: Exception) {
            null
        }
        require(!algorithm.isNullOrBlank()) {
            "Wallet Attestation JWT must contain a protected JOSE alg header"
        }
        require(algorithm in advertised) {
            "Authorization server does not advertise the Wallet Attestation JWT algorithm $algorithm"
        }
    }

    private suspend fun rememberAttestationChallenge(active: ActiveSession, challenge: String?) {
        val next = challenge?.takeIf { it.isNotBlank() } ?: return
        active.attestationChallenge = next
        persistActive(active)
    }

    private fun ActiveSession.clientConfiguration() =
        ClientConfiguration(request.clientId, listOf(request.redirectUri.toString()))

    private suspend fun beginTransition(sessionId: String, expected: SessionState): ActiveSession? {
        val active = loadActive(sessionId) ?: return null
        val marked = mutex.withLock {
            if (sessions[sessionId] !== active || active.state != expected) return@withLock false
            active.state = SessionState.PROCESSING
            active.expiresAtEpochMilliseconds = PROCESSING_EXPIRY
            true
        }
        if (!marked) return null
        try {
            refreshActiveKeys(active)
            persistActive(active)
        } catch (error: CancellationException) {
            invalidateActiveSessionBestEffort(sessionId)
            throw error
        } catch (error: Exception) {
            invalidateActiveSessionBestEffort(sessionId)
            if (error is IssuanceStageException) throw error
            throw IssuanceStageException(WalletIssuanceErrorCode.STORAGE, error)
        }
        return active
    }

    private suspend fun refreshActiveKeys(active: ActiveSession) {
        if (active.request.key == null) {
            active.keyMaterial = resolvePersistedKeyMaterial(active.keyId, active.keyMaterial.exportPublicJwkObject().toString())
        }
        active.selections = active.selections?.map { selected ->
            selected.copy(bindings = selected.bindings.mapIndexed { index, binding ->
                val requested = selected.selection.holderBindings[index]
                if (requested.key != null || (requested.keyId == null && active.request.key != null)) binding
                else binding.copy(material = resolvePersistedKeyMaterial(
                    binding.material.keyId, binding.material.exportPublicJwkObject().toString()))
            })
        }
    }

    private suspend fun loadActive(sessionId: String): ActiveSession? {
        if (mutex.withLock { runtime.closed }) return null
        val cached = mutex.withLock { sessions[sessionId] }
        val store = sessionStore
        val record = store?.get(activeRecordId(sessionId))
        if (cached != null && (!cached.persistable || store == null || record == cached.persistedSnapshot)) {
            if (cached.expiresAtEpochMilliseconds <= nowEpochMilliseconds()) {
                invalidateActiveSessionBestEffort(sessionId)
                return null
            }
            return cached
        }
        mutex.withLock { if (sessions[sessionId] === cached) sessions.remove(sessionId) }
        if (store == null || record == null) return null
        require(record.kind == WalletIssuanceSessionRecordKind.ACTIVE_SESSION && record.sessionId == sessionId) {
            "Stored issuance session binding is invalid"
        }
        val persisted = json.decodeFromString<PersistedActiveSession>(record.payload)
        require(persisted.public.id == sessionId) { "Stored issuance session identifier is invalid" }
        if (persisted.state == SessionState.PROCESSING) {
            // Another runtime may still own the grant, or its remote response may have been lost.
            // Keep the marker: loading it must never release another runtime's claim.
            throw IssuanceStageException(WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN)
        }
        if (persisted.expiresAtEpochMilliseconds <= nowEpochMilliseconds()) {
            mutex.withLock { removeUnchangedRecord(record) }
            return null
        }

        val offer = persisted.offer?.let { json.decodeFromString<CredentialOffer>(it) }
        // The store is an integrity-protected persistence boundary. Restore the resolution snapshot
        // established when the session started, then revalidate its protocol bindings below.
        val issuerMetadata = json.decodeFromString<ResolvedCredentialIssuerMetadata>(persisted.issuerMetadata)
        val authorizationServerMetadata =
            json.decodeFromString<AuthorizationServerMetadata>(persisted.authorizationServerMetadata)
        val offeredCredentials = if (offer != null) OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata.metadata)
            else persisted.public.offer.credentials.map { OfferedCredentialResolver.ResolvedCredentialOffer(it.configurationId,
                issuerMetadata.metadata.credentialConfigurationsSupported.getValue(it.configurationId)) }
        val resolved = ResolvedOffer(offer, issuerMetadata, authorizationServerMetadata, offeredCredentials)
        validatePersistedResolution(persisted.public, resolved)

        val request = WalletIssuanceSessionRequest(
            offerJson = persisted.offer?.let { Json.parseToJsonElement(it).jsonObject },
            credentialIssuer = issuerMetadata.metadata.credentialIssuer.takeIf { offer == null },
            credentialConfigurationIds = offeredCredentials.map { it.credentialConfigurationId }.takeIf { offer == null },
            keyId = persisted.request.keyId,
            did = persisted.request.did,
            clientId = persisted.request.clientId,
            redirectUri = Url(persisted.request.redirectUri),
            tokenRequestHeaders = persisted.request.tokenRequestHeaders,
        )
        val keyMaterial = resolvePersistedKeyMaterial(persisted.request.keyId, persisted.selectedPublicJwk)
        require((persisted.authorization != null) == (persisted.codeVerifier != null)) {
            "Stored issuance session PKCE binding is invalid"
        }
        when (persisted.state) {
            SessionState.AWAITING_ACCEPTANCE -> require(persisted.authorization == null) {
                "Stored review session already contains authorization state"
            }

            SessionState.AWAITING_CALLBACK -> require(persisted.authorization != null && persisted.selections != null) {
                "Stored callback session is missing authorization state"
            }

            SessionState.PROCESSING -> Unit
        }
        val authorization = persisted.authorization?.let { public ->
            val codeVerifier = requireNotNull(persisted.codeVerifier)
            val method = requireNotNull(PKCEManager.CodeChallengeMethod.fromString(public.pkce.codeChallengeMethod)) {
                "Stored issuance session contains an unsupported PKCE method"
            }
            require(
                PKCEManager.generateCodeChallenge(codeVerifier, method) == public.pkce.codeChallenge
            ) { "Stored issuance session PKCE binding is invalid" }
            AuthorizationState(
                public = public,
                pkce = PKCEManager.PKCEData(
                    codeVerifier,
                    public.pkce.codeChallenge,
                    method,
                ),
            )
        }
        val active = ActiveSession(
            public = persisted.public,
            request = request,
            resolved = resolved,
            keyMaterial = keyMaterial,
            keyId = persisted.request.keyId,
            did = persisted.request.did,
            authorization = authorization,
            state = persisted.state,
            expiresAtEpochMilliseconds = persisted.expiresAtEpochMilliseconds,
            attestationChallenge = persisted.attestationChallenge,
            persistedSnapshot = record,
            selections = persisted.selections?.map { ResolvedWalletCredentialSelection(it.selection, restoreBindings(it.bindings)) },
        )
        return mutex.withLock {
            if (runtime.closed) return@withLock null
            sessions[sessionId] ?: if (store.get(activeRecordId(sessionId)) == record) {
                active.also { sessions[sessionId] = it }
            } else null
        }
    }

    private fun validatePersistedResolution(
        public: WalletIssuanceSession,
        resolved: ResolvedOffer,
    ) {
        require(resolved.issuerMetadata.metadata.credentialIssuer == public.offer.issuer.identifier) {
            "Stored issuance session issuer binding is invalid"
        }
        require(resolved.offer == null || resolved.issuerMetadata.metadata.credentialIssuer == resolved.offer.credentialIssuer) {
            "Stored credential issuer metadata binding is invalid"
        }
        require(resolved.authorizationServerMetadata.issuer in resolved.issuerMetadata.metadata.authorizationServerIssuers()) {
            "Stored authorization server binding is invalid"
        }
        require(
            resolved.offeredCredentials.map { it.credentialConfigurationId } ==
                    public.offer.credentials.map { it.configurationId }
        ) { "Stored offered credential binding is invalid" }
        val resolvedGrant = when (resolved.grantType) {
            is GrantType.AuthorizationCode -> WalletIssuanceGrant.AUTHORIZATION_CODE
            is GrantType.PreAuthorizedCode -> WalletIssuanceGrant.PRE_AUTHORIZED_CODE
            else -> error("Stored issuance grant is unsupported")
        }
        require(public.offer.grant == resolvedGrant) { "Stored issuance grant binding is invalid" }
    }

    private suspend fun resolvePersistedKeyMaterial(
        keyId: String?,
        selectedPublicJwk: String,
    ): WalletKeyStoreEntry {
        val keyMaterial = resolveIssuanceKeyMaterial(inlineKey = null, keyId = keyId)
        val expected = Json.parseToJsonElement(selectedPublicJwk).jsonObject
        require(publicJwkMatches(keyMaterial.exportPublicJwkObject(), expected)) {
            "The holder key no longer matches the issuance session"
        }
        return keyMaterial
    }

    private suspend fun persistActive(active: ActiveSession) {
        val store = sessionStore ?: return
        if (!active.persistable) {
            active.persistedSnapshot?.let { expected ->
                if (!store.compareAndSet(expected, null)) {
                    throw IssuanceStageException(WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN)
                }
                active.persistedSnapshot = null
            }
            return
        }
        val payload = PersistedActiveSession(
            selections = active.selections?.map { PersistedCredentialSelection(it.selection.copy(holderBindings = it.bindings.map { binding -> CredentialHolderBinding(binding.material.keyId, binding.did) }), persistBindings(it.bindings)) },
            public = active.public,
            request = PersistedRequest(
                keyId = active.keyId,
                did = active.did,
                clientId = active.request.clientId,
                redirectUri = active.request.redirectUri.toString(),
                tokenRequestHeaders = active.request.tokenRequestHeaders,
            ),
            offer = active.resolved.offer?.let { json.encodeToString(it) },
            issuerMetadata = json.encodeToString(active.resolved.issuerMetadata),
            authorizationServerMetadata = json.encodeToString(active.resolved.authorizationServerMetadata),
            selectedPublicJwk = active.keyMaterial.exportPublicJwkObject().toString(),
            authorization = active.authorization?.public,
            codeVerifier = active.authorization?.pkce?.codeVerifier,
            state = active.state,
            expiresAtEpochMilliseconds = active.expiresAtEpochMilliseconds,
            attestationChallenge = active.attestationChallenge,
        )
        val expected = active.persistedSnapshot
        val replacement = WalletIssuanceSessionRecord(
            id = activeRecordId(active.public.id),
            sessionId = active.public.id,
            kind = WalletIssuanceSessionRecordKind.ACTIVE_SESSION,
            payload = json.encodeToString(payload),
            // Monotonic per-record updates also prevent identical state from creating an ABA race.
            updatedAtEpochMilliseconds = maxOf(Clock.System.now().toEpochMilliseconds(),
                expected?.updatedAtEpochMilliseconds?.plus(1) ?: Long.MIN_VALUE),
        )
        if (expected == null) store.put(replacement)
        else if (!store.compareAndSet(expected, replacement)) {
            throw IssuanceStageException(WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN)
        }
        active.persistedSnapshot = replacement
    }

    private suspend fun removePersistedActive(active: ActiveSession) {
        active.persistedSnapshot?.let { expected ->
            sessionStore?.compareAndSet(expected, null)
            active.persistedSnapshot = null
        }
    }

    private suspend fun prunePersistedActiveSessions() {
        val store = sessionStore ?: return
        val records = store.list()
        val now = nowEpochMilliseconds()
        val expired = records.filter {
            it.kind == WalletIssuanceSessionRecordKind.ACTIVE_SESSION &&
                    runCatching { json.decodeFromString<PersistedActiveSession>(it.payload).let { active ->
                        active.state != SessionState.PROCESSING && active.expiresAtEpochMilliseconds <= now
                    } }
                        .getOrDefault(false)
        }
        expired.forEach { record ->
            mutex.withLock { removeUnchangedRecord(record) }
        }
        val stale = records
            .filterNot { expired.any { removed -> removed.id == it.id } }
            .filter { it.kind == WalletIssuanceSessionRecordKind.ACTIVE_SESSION &&
                json.decodeFromString<PersistedActiveSession>(it.payload).state != SessionState.PROCESSING }
            .sortedByDescending { it.updatedAtEpochMilliseconds }
            .drop(MAX_ACTIVE_SESSIONS)
        stale.forEach { record ->
            mutex.withLock { removeUnchangedRecord(record) }
        }
    }

    private suspend fun retainDeferred(record: DeferredRecord, preserveOnFailure: Boolean = true): Unit = mutex.withLock {
        check(!runtime.closed) { "Wallet issuance is closed" }
        // Keep accepted remote progress available even if durable storage temporarily fails.
        if (preserveOnFailure) deferred[record.public.id] = record
        val store = sessionStore?.takeIf { record.persistable } ?: run {
            deferred[record.public.id] = record
            return
        }
        try {
            val payload = PersistedDeferredRecord(
                request = record.request?.let { request -> PersistedDeferredRequest(
                    configuration = request.configuration,
                    bindings = persistBindings(request.bindings),
                    proofRequired = request.proofRequired,
                    endpoint = request.endpoint,
                    transactionId = request.transactionId,
                    accessToken = request.accessToken,
                    tokenType = request.tokenType,
                    dpop = request.dpop,
                    dpopNonce = request.dpopNonce,
                    keyId = request.keyId,
                    selectedPublicJwk = request.selectedPublicJwk,
                    label = request.label,
                    metadata = request.metadata,
                    nextPollAtEpochMilliseconds = request.nextPollAtEpochMilliseconds,
                ) },
                receivedCredentials = record.receivedCredentials,
                operation = record.operation,
                public = record.public,
                sessionId = record.sessionId,
            )
            val replacement = WalletIssuanceSessionRecord(
                id = deferredRecordId(record.public.id),
                sessionId = record.sessionId,
                kind = WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL,
                payload = json.encodeToString(payload),
                updatedAtEpochMilliseconds = nowEpochMilliseconds(),
            )
            val expected = record.persistedSnapshot
            if (expected == null) store.put(replacement)
            else check(store.compareAndSet(expected, replacement)) { "Issuance continuation changed during the operation" }
            record.persistedSnapshot = replacement
            deferred[record.public.id] = record
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw IssuanceStageException(WalletIssuanceErrorCode.STORAGE, error)
        }
    }

    /** Retains a validated response before local writes; resumption never needs an issuer request. */
    internal suspend fun storeReceivedCredentials(
        credentials: List<StoredCredential>,
        configurationId: String?,
        credentialIdentifier: String?,
        persistable: Boolean,
        beforeCredentialsStored: suspend (Int) -> Unit,
        onCredentialStored: suspend (StoredCredential) -> Unit,
        sessionId: String? = null,
    ): WalletIssuanceOutcome {
        require(credentials.isNotEmpty())
        val public = WalletDeferredCredential(credentialIdentifier, Uuid.random().toString(), configurationId, null)
        val record = DeferredRecord(receivedCredentials = credentials, public = public,
            sessionId = sessionId ?: public.id, persistable = persistable)
        if (!mutex.withLock { !runtime.closed && pollingDeferred.add(public.id) }) return invalidSession(record.sessionId)
        try {
            try {
                withContext(NonCancellable) {
                    retainDeferred(record)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return failed(record.sessionId, WalletIssuanceErrorCode.STORAGE, deferredCredentials = listOf(public))
            }
            currentCoroutineContext().ensureActive()
            return resumeDeferredClaimed(public.id, beforeCredentialsStored, onCredentialStored)
        } finally {
            withContext(NonCancellable) { mutex.withLock { pollingDeferred.remove(public.id) } }
        }
    }

    /** Imports a validated Credential Endpoint continuation from the stateless full-flow API. */
    internal suspend fun retainDeferredCredential(
        public: WalletDeferredCredential,
        configuration: CredentialConfiguration,
        selection: ResolvedWalletCredentialSelection,
        proofRequired: Boolean,
        endpoint: String,
        transactionId: String,
        accessToken: String,
        tokenType: String,
        dpopAlgorithms: Set<String>?,
        senderKey: WalletKeyStoreEntry,
        persistable: Boolean,
        label: String?,
        metadata: JsonObject?,
    ) {
        retainDeferred(DeferredRecord(
            public = public,
            sessionId = public.id,
            persistable = persistable && selection.selection.holderBindings.none { it.key != null },
            request = DeferredRequest(
                configuration = configuration,
                bindings = selection.bindings,
                proofRequired = proofRequired,
                endpoint = endpoint,
                transactionId = transactionId,
                accessToken = accessToken,
                tokenType = tokenType,
                dpop = dpopAlgorithms,
                dpopNonce = null,
                keyMaterial = senderKey,
                keyId = senderKey.keyId,
                selectedPublicJwk = senderKey.exportPublicJwkObject().toString(),
                label = label,
                metadata = metadata,
                nextPollAtEpochMilliseconds = nextDeferredPoll(requireNotNull(public.intervalSeconds)),
            ),
        ))
    }

    private suspend fun loadDeferred(id: String): DeferredRecord? {
        val cached = mutex.withLock { deferred[id] }
        if (cached != null && !cached.persistable) return cached
        val record = sessionStore?.get(deferredRecordId(id))
        if (cached != null && (sessionStore == null || record == cached.persistedSnapshot)) {
            return cached.copy(request = cached.request?.let {
                it.copy(keyMaterial = resolvePersistedKeyMaterial(it.keyId, it.selectedPublicJwk),
                    bindings = restoreBindings(persistBindings(it.bindings)))
            })
        }
        if (record == null) {
            mutex.withLock { deferred.remove(id) }
            return null
        }
        require(record.kind == WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL) {
            "Stored deferred credential binding is invalid"
        }
        val persisted = json.decodeFromString<PersistedDeferredRecord>(record.payload)
        require(persisted.public.id == id && persisted.sessionId == record.sessionId) {
            "Stored deferred credential binding is invalid"
        }
        val request = persisted.request?.let {
            val bindings = restoreBindings(it.bindings)
            require(bindings.isNotEmpty()) { "Stored deferred credential has no holder bindings" }
            DeferredRequest(
                configuration = it.configuration, bindings = bindings, proofRequired = it.proofRequired,
                endpoint = it.endpoint, transactionId = it.transactionId, accessToken = it.accessToken,
                tokenType = it.tokenType, dpop = it.dpop, dpopNonce = it.dpopNonce,
                keyMaterial = resolvePersistedKeyMaterial(it.keyId, it.selectedPublicJwk),
                keyId = it.keyId, selectedPublicJwk = it.selectedPublicJwk, label = it.label, metadata = it.metadata,
                nextPollAtEpochMilliseconds = it.nextPollAtEpochMilliseconds,
            )
        }
        return DeferredRecord(
            request = request,
            receivedCredentials = persisted.receivedCredentials,
            operation = persisted.operation,
            persistedSnapshot = record,
            public = persisted.public,
            sessionId = persisted.sessionId,
            persistable = true,
        )
    }

    private suspend fun removeDeferred(record: DeferredRecord) {
        mutex.withLock {
            record.persistedSnapshot?.let {
                check(removeUnchangedRecord(it)) { "Issuance continuation changed before completion" }
            }
            deferred.remove(record.public.id)
        }
    }

    /** Called under the runtime mutex; the store also protects against other service processes. */
    private suspend fun removeUnchangedRecord(record: WalletIssuanceSessionRecord): Boolean {
        if (sessionStore?.compareAndSet(record, null) != true) return false
        when (record.kind) {
            WalletIssuanceSessionRecordKind.ACTIVE_SESSION -> sessions.remove(record.sessionId)
            WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL ->
                deferred.entries.removeAll { deferredRecordId(it.key) == record.id }
        }
        return true
    }

    private suspend fun removeSession(sessionId: String) {
        val active = mutex.withLock { sessions.remove(sessionId) }
        active?.let { removePersistedActive(it) }
    }

    private suspend fun invalidateActiveSessionBestEffort(sessionId: String) {
        withContext(NonCancellable) {
            val active = mutex.withLock { sessions.remove(sessionId) }
            try {
                active?.let { removePersistedActive(it) }
            } catch (_: Exception) {
                // A retained PROCESSING marker reports uncertainty when next loaded.
            }
        }
    }

    private suspend fun restoreSession(active: ActiveSession, state: SessionState) {
        mutex.withLock {
            if (sessions[active.public.id] === active && active.state == SessionState.PROCESSING) {
                active.state = state
                if (state == SessionState.AWAITING_ACCEPTANCE) active.authorization = null
                active.expiresAtEpochMilliseconds = nowEpochMilliseconds() +
                        when (state) {
                            SessionState.AWAITING_ACCEPTANCE -> sessionPolicy.reviewTtl.inWholeMilliseconds
                            SessionState.AWAITING_CALLBACK -> sessionPolicy.authorizationCallbackTtl.inWholeMilliseconds
                            SessionState.PROCESSING -> PROCESSING_EXPIRY
                        }
                persistActive(active)
            }
        }
    }

    private suspend fun rollbackAuthorizationStart(active: ActiveSession) {
        try {
            mutex.withLock {
                if (sessions[active.public.id] !== active ||
                    active.state !in setOf(SessionState.PROCESSING, SessionState.AWAITING_CALLBACK)) return@withLock
                active.authorization = null
                active.state = SessionState.AWAITING_ACCEPTANCE
                active.expiresAtEpochMilliseconds = nowEpochMilliseconds() + sessionPolicy.reviewTtl.inWholeMilliseconds
                persistActive(active)
            }
        } catch (_: Exception) {
            invalidateActiveSessionBestEffort(active.public.id)
        }
    }

    private suspend fun failAndRemove(
        sessionId: String,
        code: WalletIssuanceErrorCode,
    ): WalletIssuanceOutcome.Failed {
        removeSession(sessionId)
        return failed(sessionId, code)
    }

    private suspend fun failed(
        sessionId: String,
        code: WalletIssuanceErrorCode,
        storedIds: List<String> = emptyList(),
        deferredCredentials: List<WalletDeferredCredential> = emptyList(),
        failure: CredentialIssuanceFailure? = null,
    ): WalletIssuanceOutcome.Failed {
        emitEvent(WalletSessionEvent.issuance_failed)
        return WalletIssuanceOutcome.Failed(
            sessionId = sessionId,
            error = WalletIssuanceError(code, code.publicDescription()),
            storedCredentialIds = storedIds.toList(),
            deferredCredentials = deferredCredentials.toList(),
            failure = failure,
        )
    }

    private suspend fun emitEvent(event: WalletSessionEvent) {
        try {
            onEvent(event)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Observers are isolated from protocol state transitions.
        }
    }

    private fun invalidSession(sessionId: String): WalletIssuanceOutcome.Failed =
        WalletIssuanceOutcome.Failed(
            sessionId = sessionId,
            error = WalletIssuanceError(
                WalletIssuanceErrorCode.INVALID_SESSION,
                WalletIssuanceErrorCode.INVALID_SESSION.publicDescription(),
            ),
        )

    private fun WalletIssuanceErrorCode.publicDescription(): String = when (this) {
        WalletIssuanceErrorCode.INVALID_SESSION -> "The issuance session is unknown, expired, or already consumed."
        WalletIssuanceErrorCode.INVALID_CALLBACK -> "The authorization callback did not match the issuance session."
        WalletIssuanceErrorCode.INVALID_INPUT -> "The issuance continuation input is incomplete or invalid."
        WalletIssuanceErrorCode.AUTHORIZATION_FAILED -> "The authorization server rejected the authorization request."
        WalletIssuanceErrorCode.ISSUER_METADATA -> "The issuer metadata is incomplete or inconsistent."
        WalletIssuanceErrorCode.ISSUER_RESPONSE -> "The issuer returned an invalid or unsuccessful response."
        WalletIssuanceErrorCode.NETWORK -> "The issuer could not be reached."
        WalletIssuanceErrorCode.REMOTE_OUTCOME_UNCERTAIN -> "A deferred request is still in progress or its response was lost. The wallet will not automatically repeat it."
        WalletIssuanceErrorCode.STORAGE_OUTCOME_UNCERTAIN -> "Saving this credential batch is still in progress or was interrupted. Another writer cannot safely take over automatically."
        WalletIssuanceErrorCode.CRYPTO -> "The selected holder key could not create the required proof."
        WalletIssuanceErrorCode.STORAGE -> "The issued credential could not be stored."
        WalletIssuanceErrorCode.PROTOCOL -> "The issuance response did not satisfy OpenID4VCI 1.0 requirements."
    }

    private fun nextDeferredPoll(intervalSeconds: Long): Long {
        val delay = intervalSeconds.seconds.inWholeMilliseconds
        val current = nowEpochMilliseconds()
        return if (current > Long.MAX_VALUE - delay) Long.MAX_VALUE else current + delay
    }

    private fun nowEpochMilliseconds(): Long = now().toEpochMilliseconds()

    internal data class ResolvedOffer(
        val offer: CredentialOffer?,
        val issuerMetadata: ResolvedCredentialIssuerMetadata,
        val authorizationServerMetadata: AuthorizationServerMetadata,
        val offeredCredentials: List<OfferedCredentialResolver.ResolvedCredentialOffer>,
    ) {
        val grantType: GrantType? get() = if (offer == null) GrantType.AuthorizationCode else offer.getGrantType()
    }

    internal data class AuthorizationState(
        val public: WalletIssuanceAuthorization,
        val pkce: PKCEManager.PKCEData,
    )

    internal data class ActiveSession(
        val public: WalletIssuanceSession,
        val request: WalletIssuanceSessionRequest,
        val resolved: ResolvedOffer,
        var keyMaterial: WalletKeyStoreEntry,
        val keyId: String?,
        val did: String?,
        var authorization: AuthorizationState?,
        var state: SessionState,
        var expiresAtEpochMilliseconds: Long,
        var attestationChallenge: String? = null,
        /**
         * Whether the token request for this session used anonymous pre-authorized access.
         * Both token-exchange paths assign this before any proof is built, and token exchange and
         * proof building run in the same `complete()` call (no persist/restore in between), so the
         * default only applies before a token has been obtained. Recording it here lets credential
         * proofs omit `iss` without re-running attestation (which has side effects).
         */
        var tokenRequestAnonymous: Boolean = false,
        var persistedSnapshot: WalletIssuanceSessionRecord? = null,
        var selections: List<ResolvedWalletCredentialSelection>? = null,
    ) {
        val persistable: Boolean get() = request.key == null && selections?.all { it.selection.holderBindings.all { it.key == null } } != false
    }

    @Serializable
    internal enum class DeferredOperation { IDLE, POLLING, STORING }

    internal data class DeferredRecord(
        val request: DeferredRequest? = null,
        val receivedCredentials: List<StoredCredential>? = null,
        val operation: DeferredOperation = DeferredOperation.IDLE,
        var persistedSnapshot: WalletIssuanceSessionRecord? = null,
        val public: WalletDeferredCredential,
        val sessionId: String,
        val persistable: Boolean,
    ) {
        init {
            require((request != null) != (receivedCredentials != null))
            require(receivedCredentials == null || receivedCredentials.isNotEmpty())
            require(operation != DeferredOperation.POLLING || (request != null && receivedCredentials == null))
            require(operation != DeferredOperation.STORING || !receivedCredentials.isNullOrEmpty())
        }
    }

    internal data class DeferredRequest(
        val configuration: CredentialConfiguration,
        val nextPollAtEpochMilliseconds: Long,
        val bindings: List<ResolvedCredentialHolderBinding>,
        val proofRequired: Boolean,
        val endpoint: String,
        val transactionId: String,
        val accessToken: String,
        val tokenType: String,
        val dpop: Set<String>?,
        val dpopNonce: String?,
        val keyMaterial: WalletKeyStoreEntry,
        val keyId: String?,
        val selectedPublicJwk: String,
        val label: String?,
        val metadata: JsonObject? = null,
    )

    private data class ProtectedResponse(
        val response: HttpResponse,
        val dpopNonce: String?,
        val oauthError: String?,
    )

    @Serializable
    private data class PersistedRequest(
        val keyId: String?,
        val did: String?,
        val clientId: String,
        val redirectUri: String,
        val tokenRequestHeaders: Map<String, String>,
    )

    @Serializable
    private data class PersistedActiveSession(
        val selections: List<PersistedCredentialSelection>? = null,
        val public: WalletIssuanceSession,
        val request: PersistedRequest,
        val offer: String? = null,
        val issuerMetadata: String,
        val authorizationServerMetadata: String,
        val selectedPublicJwk: String,
        val authorization: WalletIssuanceAuthorization? = null,
        val codeVerifier: String? = null,
        val state: SessionState,
        val expiresAtEpochMilliseconds: Long,
        val attestationChallenge: String? = null,
    )

    @Serializable
    private data class PersistedDeferredRecord(
        val request: PersistedDeferredRequest? = null,
        val receivedCredentials: List<StoredCredential>? = null,
        val operation: DeferredOperation = DeferredOperation.IDLE,
        val public: WalletDeferredCredential,
        val sessionId: String,
    )

    @Serializable
    private data class PersistedDeferredRequest(
        val configuration: CredentialConfiguration,
        val nextPollAtEpochMilliseconds: Long,
        val bindings: List<PersistedHolderBinding>,
        val proofRequired: Boolean,
        val endpoint: String,
        val transactionId: String,
        val accessToken: String,
        val tokenType: String,
        val dpop: Set<String>?,
        val dpopNonce: String?,
        val keyId: String?,
        val selectedPublicJwk: String,
        val label: String?,
        val metadata: JsonObject? = null,
    )

    private suspend fun resolveIssuanceKeyMaterial(
        inlineKey: DirectSerializedKey?,
        keyId: String?,
    ): WalletKeyStoreEntry =
        inlineKey?.key?.let { WalletKeyStoreEntry(it.getKeyId(), it, null) }
            ?: wallet.resolveKeyMaterial(keyId, setOf(KeyUsage.SIGN))
            ?: error("No holder key is available for credential issuance")

    private suspend fun WalletKeyStoreEntry.exportPublicJwkObject(): JsonObject {
        crypto2Key?.let { key ->
            val exported = requireNotNull(key.capabilities.publicKeyExporter) {
                "Key '$keyId' does not export public material"
            }.exportPublicKey().toPublicJwk(key.spec)
            return Json.parseToJsonElement(exported.data.toByteArray().decodeToString()).jsonObject
        }
        val legacy = requireNotNull(legacyKey) { "Key '$keyId' has no usable public representation" }
        return legacy.getPublicKey().exportJWKObject()
    }


    private class IssuanceStageException(
        val code: WalletIssuanceErrorCode,
        cause: Throwable? = null,
        val failure: CredentialIssuanceFailure? = null,
    ) : Exception(code.name, cause)

    @Serializable
    internal enum class SessionState { AWAITING_ACCEPTANCE, AWAITING_CALLBACK, PROCESSING }

    private companion object {
        const val MAX_ACTIVE_SESSIONS = 32
        const val PROCESSING_EXPIRY = Long.MAX_VALUE
        const val ACTIVE_RECORD_PREFIX = "active:"
        const val DEFERRED_RECORD_PREFIX = "deferred:"

        fun activeRecordId(sessionId: String): String = "$ACTIVE_RECORD_PREFIX$sessionId"
        fun deferredRecordId(id: String): String = "$DEFERRED_RECORD_PREFIX$id"
    }
}
