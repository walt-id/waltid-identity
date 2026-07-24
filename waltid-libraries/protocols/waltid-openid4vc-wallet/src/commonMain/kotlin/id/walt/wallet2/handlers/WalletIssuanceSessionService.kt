package id.walt.wallet2.handlers

import id.walt.credentials.CredentialParser
import id.walt.crypto.keys.DirectSerializedKey
import id.walt.crypto.keys.Key
import id.walt.openid4vci.CryptographicBindingMethod
import id.walt.openid4vci.GrantType
import id.walt.openid4vci.clientauth.ClientAuthenticationMethods
import id.walt.openid4vci.metadata.issuer.CredentialConfiguration
import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.openid4vci.metadata.oauth.AuthorizationServerMetadata
import id.walt.openid4vci.offers.CredentialOffer
import id.walt.openid4vci.responses.credential.CredentialResponse
import id.walt.openid4vci.responses.credential.IssuedCredential
import id.walt.openid4vci.responses.par.PushedAuthorizationResponse
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.webdatafetching.WebDataFetcher
import id.walt.webdatafetching.WebDataFetcherId
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationHeaders
import id.waltid.openid4vci.wallet.authorization.AuthorizationRequestBuilder
import id.waltid.openid4vci.wallet.authorization.AuthorizationResponseParser
import id.waltid.openid4vci.wallet.dpop.DPoPProofBuilder
import id.waltid.openid4vci.wallet.metadata.IssuerMetadataResolver
import id.waltid.openid4vci.wallet.metadata.OfferedCredentialResolver
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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.uuid.Uuid

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
)

/** Offered credential information safe for an app review screen. */
@Serializable
data class WalletIssuanceCredentialPreview(
    val configurationId: String,
    val format: String,
    val name: String?,
    val descriptionText: String?,
    val logoUri: String?,
)

/** Typed offer preview retained by the issuance session. */
@Serializable
data class WalletIssuanceOfferPreview(
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
)

/** Public handle returned by [WalletIssuanceSessionService.start]. */
@Serializable
data class WalletIssuanceSession(
    val id: String,
    val offer: WalletIssuanceOfferPreview,
    val authorization: WalletIssuanceAuthorization?,
)

/** Input used to start either supported grant from one offer. */
@Serializable
data class WalletIssuanceSessionRequest(
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
        checkOfferSource()
        require(clientId.isNotBlank()) { "clientId cannot be blank" }
    }

}

/** Callback continuation supplied after the browser returns to the wallet. */
@Serializable
data class WalletIssuanceAuthorizationCallback(
    val sessionId: String,
    val callbackUri: String,
)

/** Public reference for a credential that must be polled later. */
@Serializable
data class WalletDeferredCredential(
    val id: String,
    val credentialConfigurationId: String,
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
sealed interface WalletIssuanceOutcome {
    val sessionId: String

    data class Stored(
        override val sessionId: String,
        val credentialIds: List<String>,
    ) : WalletIssuanceOutcome

    data class Deferred(
        override val sessionId: String,
        val storedCredentialIds: List<String>,
        val credentials: List<WalletDeferredCredential>,
    ) : WalletIssuanceOutcome

    data class Cancelled(
        override val sessionId: String,
    ) : WalletIssuanceOutcome

    data class Failed(
        override val sessionId: String,
        val error: WalletIssuanceError,
        val storedCredentialIds: List<String> = emptyList(),
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
    private val onEvent: suspend (WalletSessionEvent) -> Unit = {},
    private val sessionStore: WalletIssuanceSessionStore? = null,
    httpClient: HttpClient? = null,
) {
    private val httpClient = httpClient ?: WebDataFetcher(WebDataFetcherId.WALLET2_ISSUANCE_HANDLER).httpClient
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()
    private val sessions = LinkedHashMap<String, ActiveSession>()
    private val deferred = LinkedHashMap<String, DeferredRecord>()

    /** Resolves and binds an offer, returning a browser request only for authorization-code grants. */
    suspend fun start(request: WalletIssuanceSessionRequest): WalletIssuanceSession {
        val key = wallet.resolveKey(request.key, request.keyId)
            ?: error("No holder key is available for credential issuance")
        val resolved = resolve(request)
        val grant = resolved.offer.getGrantType()
            ?: error("Credential offer does not contain a supported grant")
        val sessionId = Uuid.random().toString()
        val authorizationState = if (grant is GrantType.AuthorizationCode) {
            buildAuthorization(resolved, request, key)
        } else {
            null
        }
        val publicSession = WalletIssuanceSession(
            id = sessionId,
            offer = resolved.toPreview(grant),
            authorization = authorizationState?.public,
        )
        val selectedKeyId = request.keyId ?: if (request.key == null) {
            wallet.defaultKeyId ?: wallet.listAllKeys().firstOrNull()?.keyId
        } else {
            null
        }
        val active = ActiveSession(
            public = publicSession,
            request = request,
            resolved = resolved,
            key = key,
            keyId = selectedKeyId,
            did = request.did ?: wallet.defaultDid(),
            authorization = authorizationState,
            state = if (grant is GrantType.AuthorizationCode) SessionState.AWAITING_CALLBACK else SessionState.READY,
        )
        val evicted = mutex.withLock {
            val removed = if (sessions.size >= MAX_ACTIVE_SESSIONS) sessions.remove(sessions.keys.first()) else null
            sessions[sessionId] = active
            removed
        }
        evicted?.let { removePersistedActive(it.public.id) }
        try {
            persistActive(active)
            prunePersistedActiveSessions()
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock { sessions.remove(sessionId) }
                removePersistedActive(sessionId)
            }
            throw error
        } catch (error: Exception) {
            mutex.withLock { sessions.remove(sessionId) }
            removePersistedActive(sessionId)
            throw error
        }
        try {
            emitEvent(WalletSessionEvent.issuance_offer_resolved)
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                mutex.withLock { sessions.remove(sessionId) }
                removePersistedActive(sessionId)
            }
            throw error
        }
        return publicSession
    }

    /** Continues a pre-authorized session with the separately delivered transaction code. */
    suspend fun continuePreAuthorized(sessionId: String, transactionCode: String? = null): WalletIssuanceOutcome {
        val active = try {
            beginTransition(sessionId, SessionState.READY)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return failed(sessionId, WalletIssuanceErrorCode.STORAGE)
        } ?: return invalidSession(sessionId)
        val grant = active.resolved.offer.grants?.preAuthorizedCode
            ?: return failAndRemove(sessionId, WalletIssuanceErrorCode.INVALID_SESSION)
        val requirement = grant.txCode
        if (requirement != null && transactionCode.isNullOrBlank()) {
            restoreSession(active, SessionState.READY)
            return failed(sessionId, WalletIssuanceErrorCode.INVALID_INPUT)
        }
        return complete(active, retryTokenRejection = requirement != null) {
            tokenForPreAuthorized(active, grant.preAuthorizedCode, transactionCode)
        }
    }

    /** Strictly validates and consumes an authorization callback before exchanging its code. */
    suspend fun continueAuthorization(callback: WalletIssuanceAuthorizationCallback): WalletIssuanceOutcome {
        val active = try {
            beginTransition(callback.sessionId, SessionState.AWAITING_CALLBACK)
        } catch (error: CancellationException) {
            throw error
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
                requireIssuer = active.resolved.authorizationServerMetadata.customParameters
                    ?.get(AUTHORIZATION_RESPONSE_ISS_PARAMETER_SUPPORTED)
                    ?.jsonPrimitive
                    ?.booleanOrNull == true,
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

    /** Cancels an active session and removes any deferred continuations bound to it. */
    suspend fun cancel(sessionId: String): WalletIssuanceOutcome {
        val active = try {
            loadActive(sessionId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return failed(sessionId, WalletIssuanceErrorCode.STORAGE)
        }
        val removed = try {
            val removedFromMemory = mutex.withLock {
            if (active?.state == SessionState.PROCESSING) return@withLock false
            val activeRemoved = sessions.remove(sessionId) != null
            val deferredRemoved = deferred.entries.removeAll { it.value.sessionId == sessionId }
            activeRemoved || deferredRemoved
            }
            if (active?.state == SessionState.PROCESSING) return invalidSession(sessionId)
            val persisted = sessionStore?.list().orEmpty().filter { it.sessionId == sessionId }
            persisted.forEach { sessionStore?.remove(it.id) }
            removedFromMemory || persisted.isNotEmpty()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return failed(sessionId, WalletIssuanceErrorCode.STORAGE)
        }
        return if (removed) WalletIssuanceOutcome.Cancelled(sessionId) else invalidSession(sessionId)
    }

    /** Invalidates and removes every active and deferred issuance continuation. */
    suspend fun clearSessions() {
        withContext(NonCancellable) {
            mutex.withLock {
                sessions.clear()
                deferred.clear()
            }
            sessionStore?.let { store ->
                store.list().forEach { store.remove(it.id) }
            }
        }
    }

    /** Polls one deferred result while preserving its access material inside the engine. */
    suspend fun resumeDeferred(deferredCredentialId: String): WalletIssuanceOutcome {
        val record = try {
            takeDeferred(deferredCredentialId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return failed(deferredCredentialId, WalletIssuanceErrorCode.STORAGE)
        } ?: return invalidSession(deferredCredentialId)
        val response = try {
            postProtected(
                endpoint = record.endpoint,
                accessToken = record.accessToken,
                tokenType = record.tokenType,
                dpop = record.dpop,
                key = record.key,
                dpopNonce = record.dpopNonce,
                body = buildJsonObject { put("transaction_id", record.transactionId) }.toString(),
            )
        } catch (error: CancellationException) {
            withContext(NonCancellable) { restoreDeferred(record) }
            throw error
        } catch (error: IssuanceStageException) {
            if (error.code == WalletIssuanceErrorCode.NETWORK) {
                restoreDeferred(record)
            }
            return failed(record.sessionId, error.code)
        } catch (_: Exception) {
            return failed(record.sessionId, WalletIssuanceErrorCode.PROTOCOL)
        }

        if (response.response.status == HttpStatusCode.Accepted || response.response.oauthError() == "issuance_pending") {
            restoreDeferred(record.copy(dpopNonce = response.dpopNonce))
            return WalletIssuanceOutcome.Deferred(record.sessionId, emptyList(), listOf(record.public))
        }
        if (response.response.status != HttpStatusCode.OK) {
            restoreDeferred(record.copy(dpopNonce = response.dpopNonce))
            return failed(record.sessionId, WalletIssuanceErrorCode.ISSUER_RESPONSE)
        }

        return try {
            val credentials = response.response.body<CredentialResponse>().credentials
                ?.takeIf { it.isNotEmpty() }
                ?: run {
                    restoreDeferred(record.copy(dpopNonce = response.dpopNonce))
                    return failed(record.sessionId, WalletIssuanceErrorCode.PROTOCOL)
                }
            val stored = credentials.map { wallet.parseAndStore(it, record.label) }
            stored.forEach { emitEvent(WalletSessionEvent.issuance_credential_stored) }
            emitEvent(WalletSessionEvent.issuance_completed)
            WalletIssuanceOutcome.Stored(record.sessionId, stored.map { it.id })
        } catch (error: CancellationException) {
            withContext(NonCancellable) { restoreDeferred(record) }
            throw error
        } catch (_: Exception) {
            failed(record.sessionId, WalletIssuanceErrorCode.STORAGE)
        }
    }

    private suspend fun complete(
        active: ActiveSession,
        retryTokenRejection: Boolean = false,
        obtainToken: suspend () -> TokenRequestBuilder.TokenResponse,
    ): WalletIssuanceOutcome {
        val storedIds = mutableListOf<String>()
        var persistedDeferredIds: List<String> = emptyList()
        return try {
            val advertisedDpop = active.dpopAlgorithms()
            if (advertisedDpop != null && active.key.keyType.jwsAlg !in advertisedDpop) {
                throw IssuanceStageException(WalletIssuanceErrorCode.CRYPTO)
            }
            val token = obtainToken()
            val dpop = dpopAlgorithmsForToken(token.token_type, advertisedDpop)
            emitEvent(WalletSessionEvent.issuance_token_obtained)

            val deferredResults = issueCredentials(active, token, dpop, storedIds)
            persistDeferred(deferredResults.map { it.record })
            persistedDeferredIds = deferredResults.map { it.public.id }
            removeSession(active.public.id)
            if (deferredResults.isNotEmpty()) {
                emitEvent(WalletSessionEvent.issuance_deferred)
                WalletIssuanceOutcome.Deferred(active.public.id, storedIds, deferredResults.map { it.public })
            } else {
                emitEvent(WalletSessionEvent.issuance_completed)
                WalletIssuanceOutcome.Stored(active.public.id, storedIds)
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) {
                removeSession(active.public.id)
                removeDeferred(persistedDeferredIds)
            }
            throw error
        } catch (error: TokenRequestException) {
            if (
                retryTokenRejection &&
                storedIds.isEmpty() &&
                error.oauthError == "invalid_grant"
            ) {
                restoreSession(active, SessionState.READY)
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
            removeDeferred(persistedDeferredIds)
            failed(active.public.id, error.code, storedIds)
        } catch (_: IllegalArgumentException) {
            removeSession(active.public.id)
            removeDeferred(persistedDeferredIds)
            failed(active.public.id, WalletIssuanceErrorCode.PROTOCOL, storedIds)
        } catch (_: Exception) {
            removeSession(active.public.id)
            removeDeferred(persistedDeferredIds)
            failed(active.public.id, WalletIssuanceErrorCode.ISSUER_RESPONSE, storedIds)
        }
    }

    private suspend fun issueCredentials(
        active: ActiveSession,
        token: TokenRequestBuilder.TokenResponse,
        dpopAlgorithms: Set<String>?,
        storedIds: MutableList<String>,
    ): List<PendingDeferred> {
        val pending = mutableListOf<PendingDeferred>()
        for (offered in active.resolved.offeredCredentials) {
            val proof = try {
                proofForCredential(active, offered.configuration)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                throw IssuanceStageException(WalletIssuanceErrorCode.CRYPTO, error)
            }
            val proofNonce = if (proof.required) fetchNonce(active.resolved.issuerMetadata) else null
            val proofJwt = if (proof.required) {
                try {
                    buildCredentialProof(active, offered.configuration, proofNonce)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    throw IssuanceStageException(WalletIssuanceErrorCode.CRYPTO, error)
                }
            } else {
                null
            }
            if (proofJwt != null) emitEvent(WalletSessionEvent.issuance_proof_signed)

            val requestBody = buildJsonObject {
                put("credential_configuration_id", offered.credentialConfigurationId)
                proofJwt?.let { jwt ->
                    putJsonObject("proofs") {
                        put("jwt", buildJsonArray { add(JsonPrimitive(jwt)) })
                    }
                }
            }.toString()
            val protected = postProtected(
                endpoint = active.resolved.issuerMetadata.credentialEndpoint,
                accessToken = token.access_token,
                tokenType = token.token_type,
                dpop = dpopAlgorithms,
                key = active.key,
                dpopNonce = null,
                body = requestBody,
            )

            when (protected.response.status) {
                HttpStatusCode.OK -> {
                    val response = try {
                        protected.response.body<CredentialResponse>()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL, error)
                    }
                    if (response.transactionId != null || response.credentials.isNullOrEmpty()) {
                        throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL)
                    }
                    val credentials = response.credentials
                        ?: throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL)
                    emitEvent(WalletSessionEvent.issuance_credential_received)
                    val label = offered.configuration.credentialMetadata?.display?.firstOrNull()?.name
                    credentials.forEach { issued ->
                        val stored = try {
                            wallet.parseAndStore(issued, label)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            throw IssuanceStageException(WalletIssuanceErrorCode.STORAGE, error)
                        }
                        storedIds += stored.id
                        emitEvent(WalletSessionEvent.issuance_credential_stored)
                    }
                }

                HttpStatusCode.Accepted -> {
                    val response = try {
                        protected.response.body<CredentialResponse>()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL, error)
                    }
                    if (response.credentials != null || response.transactionId.isNullOrBlank()) {
                        throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL)
                    }
                    val transactionId = response.transactionId
                        ?: throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL)
                    val deferredEndpoint = active.resolved.issuerMetadata.deferredCredentialEndpoint
                        ?: throw IssuanceStageException(WalletIssuanceErrorCode.PROTOCOL)
                    val public = WalletDeferredCredential(
                        id = Uuid.random().toString(),
                        credentialConfigurationId = offered.credentialConfigurationId,
                        intervalSeconds = response.interval,
                    )
                    val label = offered.configuration.credentialMetadata?.display?.firstOrNull()?.name
                    pending += PendingDeferred(
                        public = public,
                        record = DeferredRecord(
                            public = public,
                            sessionId = active.public.id,
                            endpoint = deferredEndpoint,
                            transactionId = transactionId,
                            accessToken = token.access_token,
                            tokenType = token.token_type,
                            dpop = dpopAlgorithms,
                            dpopNonce = protected.dpopNonce,
                            key = active.key,
                            keyId = active.keyId,
                            selectedPublicJwk = active.key.getPublicKey().exportJWKObject().toString(),
                            persistable = active.persistable,
                            label = label,
                        ),
                    )
                }

                else -> throw IssuanceStageException(WalletIssuanceErrorCode.ISSUER_RESPONSE)
            }
        }
        return pending
    }

    private suspend fun tokenForPreAuthorized(
        active: ActiveSession,
        preAuthorizedCode: String,
        transactionCode: String?,
    ): TokenRequestBuilder.TokenResponse {
        val metadata = active.resolved.authorizationServerMetadata
        val tokenEndpoint = requireNotNull(metadata.tokenEndpoint) { "Authorization server has no token endpoint" }
        val attestation = attestationHeaders(metadata, active.request.clientId, active.key)
        val anonymous = metadata.preAuthorizedGrantAnonymousAccessSupported == true &&
            active.request.tokenRequestHeaders.isEmpty() && attestation == null
        return TokenRequestBuilder(active.clientConfiguration(), httpClient).exchangePreAuthorizedCode(
            tokenEndpoint = tokenEndpoint,
            preAuthorizedCode = preAuthorizedCode,
            txCode = transactionCode,
            additionalHeaders = active.request.tokenRequestHeaders,
            attestationHeaders = attestation,
            anonymous = anonymous,
            dpopProofFactory = active.dpopFactory(),
        )
    }

    private suspend fun tokenForAuthorizationCode(
        active: ActiveSession,
        code: String,
        codeVerifier: String,
    ): TokenRequestBuilder.TokenResponse {
        val metadata = active.resolved.authorizationServerMetadata
        val tokenEndpoint = requireNotNull(metadata.tokenEndpoint) { "Authorization server has no token endpoint" }
        val attestation = attestationHeaders(metadata, active.request.clientId, active.key)
        return TokenRequestBuilder(active.clientConfiguration(), httpClient).exchangeAuthorizationCode(
            tokenEndpoint = tokenEndpoint,
            code = code,
            codeVerifier = codeVerifier,
            additionalHeaders = active.request.tokenRequestHeaders,
            attestationHeaders = attestation,
            dpopProofFactory = active.dpopFactory(),
        )
    }

    private fun ActiveSession.dpopAlgorithms(): Set<String>? {
        val metadata = resolved.authorizationServerMetadata
        val grant = resolved.offer.getGrantType() ?: return null
        val grantIsAdvertised = metadata.grantTypesSupported?.contains(grant.value)
            ?: (grant is GrantType.AuthorizationCode)
        return metadata.dpopSigningAlgValuesSupported
            ?.takeIf { grantIsAdvertised && it.isNotEmpty() }
    }

    private fun ActiveSession.dpopFactory(): DPoPProofFactory? =
        dpopAlgorithms()?.let { algorithms ->
            { endpoint: String, nonce: String? ->
                DPoPProofBuilder().buildProof(
                    key = key,
                    httpMethod = "POST",
                    targetUri = endpoint,
                    nonce = nonce,
                    supportedAlgorithms = algorithms,
                )
            }
        }

    private suspend fun buildAuthorization(
        resolved: ResolvedOffer,
        request: WalletIssuanceSessionRequest,
        key: Key,
    ): AuthorizationState {
        val metadata = resolved.authorizationServerMetadata
        val endpoint = requireNotNull(metadata.authorizationEndpoint) {
            "Authorization server has no authorization endpoint"
        }
        val builder = AuthorizationRequestBuilder(
            ClientConfiguration(request.clientId, listOf(request.redirectUri.toString()))
        )
        val credentialConfigurationIds = resolved.offeredCredentials.map { it.credentialConfigurationId }
        val issuerState = resolved.offer.grants?.authorizationCode?.issuerState
        val parEndpoint = metadata.pushedAuthorizationRequestEndpoint
        require(metadata.requirePushedAuthorizationRequests != true || parEndpoint != null) {
            "Authorization server requires PAR but does not advertise an endpoint"
        }

        if (parEndpoint != null) {
            val pushed = builder.buildPushedAuthorizationRequestStateForCredentialConfigurations(
                credentialConfigurationIds = credentialConfigurationIds,
                issuerState = issuerState,
                usePKCE = true,
                metadata = metadata,
                redirectUri = request.redirectUri.toString(),
            )
            val attestation = attestationHeaders(metadata, request.clientId, key)
            val response = httpClient.post(parEndpoint) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(Parameters.build {
                    pushed.parameters.forEach { (name, value) -> append(name, value) }
                }.formUrlEncode())
                attestation?.let { headers ->
                    header(ClientAttestationHeaders.HEADER_ATTESTATION, headers.attestationJwt)
                    header(ClientAttestationHeaders.HEADER_ATTESTATION_POP, headers.popJwt)
                }
            }
            require(response.status.isSuccess()) { "Pushed authorization request failed" }
            val par = response.body<PushedAuthorizationResponse>()
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
                ),
                pkce = pkce,
            )
        }

        val direct = builder.buildAuthorizationRequestForCredentialConfigurations(
            authorizationEndpoint = endpoint,
            credentialConfigurationIds = credentialConfigurationIds,
            issuerState = issuerState,
            usePKCE = true,
            metadata = metadata,
            redirectUri = request.redirectUri.toString(),
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
            ),
            pkce = pkce,
        )
    }

    private suspend fun resolve(request: WalletIssuanceSessionRequest): ResolvedOffer {
        val offer = if (request.offerJson != null) {
            val inline = json.decodeFromString<CredentialOffer>(request.offerJson.toString())
            CredentialOfferResolver(httpClient).resolveCredentialOffer(inline, null)
        } else {
            val parsed = CredentialOfferParser.parseCredentialOfferUrl(request.getEffectiveOfferString())
            CredentialOfferResolver(httpClient).resolveCredentialOffer(parsed.credentialOffer, parsed.credentialOfferUri)
        }
        val resolver = IssuerMetadataResolver(httpClient)
        val issuerMetadata = resolver.resolveCredentialIssuerMetadata(offer.credentialIssuer)
        require(issuerMetadata.credentialIssuer == offer.credentialIssuer) {
            "Credential issuer metadata identifier does not match the offer"
        }
        val grantAuthorizationServer = when (offer.getGrantType()) {
            is GrantType.AuthorizationCode -> offer.grants?.authorizationCode?.authorizationServer
            is GrantType.PreAuthorizedCode -> offer.grants?.preAuthorizedCode?.authorizationServer
            else -> null
        }
        val selectedAuthorizationServer = selectAuthorizationServer(issuerMetadata, grantAuthorizationServer)
        val authorizationServerMetadata = resolver.resolveAuthorizationServerMetadata(selectedAuthorizationServer)
        require(authorizationServerMetadata.issuer == selectedAuthorizationServer) {
            "Authorization server metadata issuer does not match the selected server"
        }
        val offered = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata)
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

    private fun ResolvedOffer.toPreview(grant: GrantType): WalletIssuanceOfferPreview {
        val issuerDisplay = issuerMetadata.display?.firstOrNull()
        val txCode = offer.grants?.preAuthorizedCode?.txCode
        return WalletIssuanceOfferPreview(
            grant = when (grant) {
                is GrantType.AuthorizationCode -> WalletIssuanceGrant.AUTHORIZATION_CODE
                is GrantType.PreAuthorizedCode -> WalletIssuanceGrant.PRE_AUTHORIZED_CODE
                else -> error("Unsupported credential offer grant")
            },
            issuer = WalletIssuanceIssuerPreview(
                identifier = offer.credentialIssuer,
                name = issuerDisplay?.name,
                locale = issuerDisplay?.locale,
                logoUri = issuerDisplay?.logo?.uri,
                logoAltText = issuerDisplay?.logo?.altText,
            ),
            credentials = offeredCredentials.map { offered ->
                val display = offered.configuration.credentialMetadata?.display?.firstOrNull()
                WalletIssuanceCredentialPreview(
                    configurationId = offered.credentialConfigurationId,
                    format = offered.configuration.format.value,
                    name = display?.name,
                    descriptionText = display?.description,
                    logoUri = display?.logo?.uri,
                )
            },
            transactionCode = txCode?.let {
                WalletIssuanceTransactionCode(it.inputMode, it.length, it.description)
            },
        )
    }

    private data class CredentialProofRequirement(val required: Boolean)

    private fun proofForCredential(active: ActiveSession, configuration: CredentialConfiguration): CredentialProofRequirement {
        val proofTypes = configuration.proofTypesSupported ?: return CredentialProofRequirement(false)
        val jwt = proofTypes["jwt"] ?: error("Issuer requires an unsupported credential proof type")
        require(active.key.keyType.jwsAlg in jwt.proofSigningAlgValuesSupported) {
            "Selected holder key algorithm is not supported for credential proof"
        }
        return CredentialProofRequirement(true)
    }

    private suspend fun buildCredentialProof(
        active: ActiveSession,
        configuration: CredentialConfiguration,
        nonce: String?,
    ): String {
        val methods = requireNotNull(configuration.cryptographicBindingMethodsSupported)
        val builder = JwtProofBuilder()
        val didKeyId = resolveHolderDidKeyId(active)
        val didMethod = didKeyId
            ?.removePrefix("did:")
            ?.substringBefore(':')
        val supportsHolderDid = didMethod != null && methods.any {
            it is CryptographicBindingMethod.Did && it.method == didMethod
        }
        // Prefer a DID only after proving that its verification method belongs to the selected key.
        // This preserves the holder identity needed by W3C VC issuers without weakening key binding.
        val proofs = if (supportsHolderDid) {
            builder.buildJwtProof(
                key = active.key,
                audience = active.resolved.offer.credentialIssuer,
                nonce = nonce,
                keyId = didKeyId,
            )
        } else if (methods.any { it is CryptographicBindingMethod.Jwk || it is CryptographicBindingMethod.CoseKey }) {
            builder.buildJwtProof(
                key = active.key,
                audience = active.resolved.offer.credentialIssuer,
                nonce = nonce,
                includeJwk = true,
            )
        } else {
            error("Issuer requires a DID that is bound to the selected holder key")
        }
        return proofs.jwt?.singleOrNull() ?: error("Credential proof builder returned no JWT")
    }

    private suspend fun resolveHolderDidKeyId(active: ActiveSession): String? {
        val did = active.did ?: return null
        val baseDid = did.substringBefore('#')
        val requestedKeyId = did.takeIf { '#' in it }
        val selectedJwk = active.key.getPublicKey().exportJWKObject()
        val storedDid = wallet.didStore?.getDid(baseDid)

        if (storedDid != null) {
            val verificationMethods = storedDid.document["verificationMethod"]?.jsonArray ?: return null
            for (method in verificationMethods) {
                val methodObject = method.jsonObject
                val methodId = methodObject["id"]?.jsonPrimitive?.contentOrNull ?: continue
                if (requestedKeyId != null && methodId != requestedKeyId) continue
                val publicKeyJwk = methodObject["publicKeyJwk"]?.jsonObject ?: continue
                if (publicJwkMatches(publicKeyJwk, selectedJwk)) return methodId
            }
            return null
        }

        val staticDidMatches = wallet.staticDid?.substringBefore('#') == baseDid
        val staticKeyJwk = wallet.staticKey?.getPublicKey()?.exportJWKObject()
        val staticKeyMatches = staticKeyJwk != null && publicJwkMatches(staticKeyJwk, selectedJwk)
        return did.takeIf { staticDidMatches && staticKeyMatches }
    }

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
        key: Key,
        dpopNonce: String?,
        body: String,
    ): ProtectedResponse {
        var nonce = dpopNonce
        repeat(2) { attempt ->
            val proof = try {
                dpop?.let { algorithms ->
                    DPoPProofBuilder().buildProof(
                        key = key,
                        httpMethod = "POST",
                        targetUri = endpoint,
                        accessToken = accessToken,
                        nonce = nonce,
                        supportedAlgorithms = algorithms,
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
            val suppliedNonce = response.headers[DPOP_NONCE_HEADER]
            if (
                attempt == 0 &&
                proof != null &&
                response.oauthError() == USE_DPOP_NONCE &&
                !suppliedNonce.isNullOrBlank()
            ) {
                nonce = suppliedNonce
                return@repeat
            }
            return ProtectedResponse(response, suppliedNonce ?: nonce)
        }
        error("DPoP nonce retry exhausted")
    }

    private suspend fun HttpResponse.oauthError(): String? {
        if (status.isSuccess()) return null
        if (headers[HttpHeaders.WWWAuthenticate]?.contains(USE_DPOP_NONCE, ignoreCase = true) == true) {
            return USE_DPOP_NONCE
        }
        return try {
            Json.parseToJsonElement(bodyAsText()).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun attestationHeaders(
        metadata: AuthorizationServerMetadata,
        clientId: String,
        key: Key,
    ): ClientAttestationHeaders? {
        val assembler = attestationAssembler ?: return null
        if (metadata.tokenEndpointAuthMethodsSupported
                ?.contains(ClientAuthenticationMethods.ATTEST_JWT_CLIENT_AUTH) != true
        ) {
            return null
        }
        return assembler.buildAttestationHeaders(key, clientId, metadata.issuer).also {
            emitEvent(WalletSessionEvent.issuance_attestation_obtained)
        }
    }

    private fun dpopAlgorithmsForToken(
        tokenType: String,
        advertisedAlgorithms: Set<String>?,
    ): Set<String>? = when {
        tokenType.equals("DPoP", ignoreCase = true) -> requireNotNull(advertisedAlgorithms) {
            "Authorization server returned a DPoP token without advertising DPoP support"
        }

        tokenType.equals("Bearer", ignoreCase = true) -> null
        else -> error("Authorization server returned an unsupported token type")
    }

    private fun authorizationScheme(tokenType: String): String =
        if (tokenType.equals("DPoP", ignoreCase = true)) "DPoP" else "Bearer"

    private suspend fun Wallet.parseAndStore(issued: IssuedCredential, label: String?): StoredCredential {
        val raw = issued.credential.let { value ->
            if (value is JsonPrimitive) value.content else value.toString()
        }
        val (_, parsed) = CredentialParser.detectAndParse(raw)
        return StoredCredential(
            id = Uuid.random().toString(),
            credential = parsed,
            label = label,
            addedAt = Clock.System.now(),
        ).also { addCredential(it) }
    }

    private fun ActiveSession.clientConfiguration() =
        ClientConfiguration(request.clientId, listOf(request.redirectUri.toString()))

    private suspend fun beginTransition(sessionId: String, expected: SessionState): ActiveSession? {
        val active = loadActive(sessionId) ?: return null
        val marked = mutex.withLock {
            if (sessions[sessionId] !== active || active.state != expected) return@withLock false
            active.state = SessionState.PROCESSING
            true
        }
        if (!marked) return null
        try {
            persistActive(active)
        } catch (error: CancellationException) {
            invalidateActiveSessionBestEffort(sessionId)
            throw error
        } catch (error: Exception) {
            invalidateActiveSessionBestEffort(sessionId)
            throw IssuanceStageException(WalletIssuanceErrorCode.STORAGE, error)
        }
        return active
    }

    private suspend fun loadActive(sessionId: String): ActiveSession? {
        mutex.withLock { sessions[sessionId] }?.let { return it }
        val store = sessionStore ?: return null
        val record = store.get(activeRecordId(sessionId)) ?: return null
        require(record.kind == WalletIssuanceSessionRecordKind.ACTIVE_SESSION && record.sessionId == sessionId) {
            "Stored issuance session binding is invalid"
        }
        val persisted = json.decodeFromString<PersistedActiveSession>(record.payload)
        require(persisted.public.id == sessionId) { "Stored issuance session identifier is invalid" }
        if (persisted.state == SessionState.PROCESSING) {
            invalidateActiveSessionBestEffort(sessionId)
            return null
        }

        val offer = json.decodeFromString<CredentialOffer>(persisted.offer)
        val issuerMetadata = json.decodeFromString<CredentialIssuerMetadata>(persisted.issuerMetadata)
        val authorizationServerMetadata =
            json.decodeFromString<AuthorizationServerMetadata>(persisted.authorizationServerMetadata)
        val offeredCredentials = OfferedCredentialResolver.resolveOfferedCredentials(offer, issuerMetadata)
        val resolved = ResolvedOffer(offer, issuerMetadata, authorizationServerMetadata, offeredCredentials)
        validatePersistedResolution(persisted.public, resolved)

        val request = WalletIssuanceSessionRequest(
            offerJson = Json.parseToJsonElement(persisted.offer).jsonObject,
            keyId = persisted.request.keyId,
            did = persisted.request.did,
            clientId = persisted.request.clientId,
            redirectUri = Url(persisted.request.redirectUri),
            tokenRequestHeaders = persisted.request.tokenRequestHeaders,
        )
        val key = resolvePersistedKey(persisted.request.keyId, persisted.selectedPublicJwk)
        require((persisted.public.authorization != null) == (persisted.codeVerifier != null)) {
            "Stored issuance session PKCE binding is invalid"
        }
        val authorization = persisted.public.authorization?.let { public ->
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
            key = key,
            keyId = persisted.request.keyId,
            did = persisted.request.did,
            authorization = authorization,
            state = persisted.state,
        )
        return mutex.withLock {
            sessions[sessionId] ?: active.also { sessions[sessionId] = it }
        }
    }

    private fun validatePersistedResolution(
        public: WalletIssuanceSession,
        resolved: ResolvedOffer,
    ) {
        require(resolved.offer.credentialIssuer == public.offer.issuer.identifier) {
            "Stored issuance session issuer binding is invalid"
        }
        require(resolved.issuerMetadata.credentialIssuer == resolved.offer.credentialIssuer) {
            "Stored credential issuer metadata binding is invalid"
        }
        require(resolved.authorizationServerMetadata.issuer in resolved.issuerMetadata.authorizationServerIssuers()) {
            "Stored authorization server binding is invalid"
        }
        require(
            resolved.offeredCredentials.map { it.credentialConfigurationId } ==
                public.offer.credentials.map { it.configurationId }
        ) { "Stored offered credential binding is invalid" }
        val authorizationExpected = public.offer.grant == WalletIssuanceGrant.AUTHORIZATION_CODE
        require((public.authorization != null) == authorizationExpected) {
            "Stored issuance grant binding is invalid"
        }
    }

    private suspend fun resolvePersistedKey(keyId: String?, selectedPublicJwk: String): Key {
        val key = keyId?.let { wallet.findKey(it) } ?: wallet.defaultKey()
            ?: error("The holder key for the issuance session is no longer available")
        val expected = Json.parseToJsonElement(selectedPublicJwk).jsonObject
        require(publicJwkMatches(key.getPublicKey().exportJWKObject(), expected)) {
            "The holder key no longer matches the issuance session"
        }
        return key
    }

    private suspend fun persistActive(active: ActiveSession) {
        val store = sessionStore ?: return
        if (!active.persistable) return
        val payload = PersistedActiveSession(
            public = active.public,
            request = PersistedRequest(
                keyId = active.keyId,
                did = active.did,
                clientId = active.request.clientId,
                redirectUri = active.request.redirectUri.toString(),
                tokenRequestHeaders = active.request.tokenRequestHeaders,
            ),
            offer = json.encodeToString(active.resolved.offer),
            issuerMetadata = json.encodeToString(active.resolved.issuerMetadata),
            authorizationServerMetadata = json.encodeToString(active.resolved.authorizationServerMetadata),
            selectedPublicJwk = active.key.getPublicKey().exportJWKObject().toString(),
            codeVerifier = active.authorization?.pkce?.codeVerifier,
            state = active.state,
        )
        store.put(
            WalletIssuanceSessionRecord(
                id = activeRecordId(active.public.id),
                sessionId = active.public.id,
                kind = WalletIssuanceSessionRecordKind.ACTIVE_SESSION,
                payload = json.encodeToString(payload),
                updatedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    private suspend fun removePersistedActive(sessionId: String) {
        sessionStore?.remove(activeRecordId(sessionId))
    }

    private suspend fun prunePersistedActiveSessions() {
        val store = sessionStore ?: return
        val stale = store.list()
            .filter { it.kind == WalletIssuanceSessionRecordKind.ACTIVE_SESSION }
            .sortedByDescending { it.updatedAtEpochMilliseconds }
            .drop(MAX_ACTIVE_SESSIONS)
        stale.forEach { record ->
            store.remove(record.id)
            mutex.withLock { sessions.remove(record.sessionId) }
        }
    }

    private suspend fun persistDeferred(records: List<DeferredRecord>) {
        if (records.isEmpty()) return
        mutex.withLock { records.forEach { deferred[it.public.id] = it } }
        val store = sessionStore ?: return
        try {
            records.filter { it.persistable }.forEach { record ->
                val payload = PersistedDeferredRecord(
                    public = record.public,
                    sessionId = record.sessionId,
                    endpoint = record.endpoint,
                    transactionId = record.transactionId,
                    accessToken = record.accessToken,
                    tokenType = record.tokenType,
                    dpop = record.dpop,
                    dpopNonce = record.dpopNonce,
                    keyId = record.keyId,
                    selectedPublicJwk = record.selectedPublicJwk,
                    label = record.label,
                )
                store.put(
                    WalletIssuanceSessionRecord(
                        id = deferredRecordId(record.public.id),
                        sessionId = record.sessionId,
                        kind = WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL,
                        payload = json.encodeToString(payload),
                        updatedAtEpochMilliseconds = Clock.System.now().toEpochMilliseconds(),
                    )
                )
            }
        } catch (error: CancellationException) {
            withContext(NonCancellable) { removeDeferred(records.map { it.public.id }) }
            throw error
        } catch (error: Exception) {
            withContext(NonCancellable) { removeDeferred(records.map { it.public.id }) }
            throw IssuanceStageException(WalletIssuanceErrorCode.STORAGE, error)
        }
    }

    private suspend fun takeDeferred(id: String): DeferredRecord? {
        val cached = mutex.withLock { deferred.remove(id) }
        if (cached != null) {
            if (cached.persistable) sessionStore?.remove(deferredRecordId(id))
            return cached
        }
        val record = sessionStore?.get(deferredRecordId(id)) ?: return null
        require(record.kind == WalletIssuanceSessionRecordKind.DEFERRED_CREDENTIAL) {
            "Stored deferred credential binding is invalid"
        }
        val persisted = json.decodeFromString<PersistedDeferredRecord>(record.payload)
        require(persisted.public.id == id && persisted.sessionId == record.sessionId) {
            "Stored deferred credential binding is invalid"
        }
        val key = resolvePersistedKey(persisted.keyId, persisted.selectedPublicJwk)
        sessionStore.remove(record.id)
        return DeferredRecord(
            public = persisted.public,
            sessionId = persisted.sessionId,
            endpoint = persisted.endpoint,
            transactionId = persisted.transactionId,
            accessToken = persisted.accessToken,
            tokenType = persisted.tokenType,
            dpop = persisted.dpop,
            dpopNonce = persisted.dpopNonce,
            key = key,
            keyId = persisted.keyId,
            selectedPublicJwk = persisted.selectedPublicJwk,
            persistable = true,
            label = persisted.label,
        )
    }

    private suspend fun restoreDeferred(record: DeferredRecord) {
        persistDeferred(listOf(record))
    }

    private suspend fun removeDeferred(ids: List<String>) {
        if (ids.isEmpty()) return
        mutex.withLock { ids.forEach(deferred::remove) }
        ids.forEach { sessionStore?.remove(deferredRecordId(it)) }
    }

    private suspend fun removeSession(sessionId: String) {
        mutex.withLock { sessions.remove(sessionId) }
        removePersistedActive(sessionId)
    }

    private suspend fun invalidateActiveSessionBestEffort(sessionId: String) {
        withContext(NonCancellable) {
            mutex.withLock { sessions.remove(sessionId) }
            try {
                removePersistedActive(sessionId)
            } catch (_: Exception) {
                // A retained PROCESSING marker is rejected when the session is next loaded.
            }
        }
    }

    private suspend fun restoreSession(active: ActiveSession, state: SessionState) {
        var restored = false
        mutex.withLock {
            if (sessions[active.public.id] === active && active.state == SessionState.PROCESSING) {
                active.state = state
                restored = true
            }
        }
        if (restored) persistActive(active)
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
    ): WalletIssuanceOutcome.Failed {
        emitEvent(WalletSessionEvent.issuance_failed)
        return WalletIssuanceOutcome.Failed(
            sessionId = sessionId,
            error = WalletIssuanceError(code, code.publicDescription()),
            storedCredentialIds = storedIds,
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
        WalletIssuanceErrorCode.CRYPTO -> "The selected holder key could not create the required proof."
        WalletIssuanceErrorCode.STORAGE -> "The issued credential could not be stored."
        WalletIssuanceErrorCode.PROTOCOL -> "The issuance response did not satisfy OpenID4VCI 1.0 requirements."
    }

    private data class ResolvedOffer(
        val offer: CredentialOffer,
        val issuerMetadata: CredentialIssuerMetadata,
        val authorizationServerMetadata: AuthorizationServerMetadata,
        val offeredCredentials: List<OfferedCredentialResolver.ResolvedCredentialOffer>,
    )

    private data class AuthorizationState(
        val public: WalletIssuanceAuthorization,
        val pkce: id.waltid.openid4vci.wallet.oauth.PKCEManager.PKCEData,
    )

    private data class ActiveSession(
        val public: WalletIssuanceSession,
        val request: WalletIssuanceSessionRequest,
        val resolved: ResolvedOffer,
        val key: Key,
        val keyId: String?,
        val did: String?,
        val authorization: AuthorizationState?,
        var state: SessionState,
    ) {
        val persistable: Boolean get() = request.key == null
    }

    private data class DeferredRecord(
        val public: WalletDeferredCredential,
        val sessionId: String,
        val endpoint: String,
        val transactionId: String,
        val accessToken: String,
        val tokenType: String,
        val dpop: Set<String>?,
        val dpopNonce: String?,
        val key: Key,
        val keyId: String?,
        val selectedPublicJwk: String,
        val persistable: Boolean,
        val label: String?,
    )

    private data class PendingDeferred(
        val public: WalletDeferredCredential,
        val record: DeferredRecord,
    )
    private data class ProtectedResponse(val response: HttpResponse, val dpopNonce: String?)

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
        val public: WalletIssuanceSession,
        val request: PersistedRequest,
        val offer: String,
        val issuerMetadata: String,
        val authorizationServerMetadata: String,
        val selectedPublicJwk: String,
        val codeVerifier: String? = null,
        val state: SessionState,
    )

    @Serializable
    private data class PersistedDeferredRecord(
        val public: WalletDeferredCredential,
        val sessionId: String,
        val endpoint: String,
        val transactionId: String,
        val accessToken: String,
        val tokenType: String,
        val dpop: Set<String>?,
        val dpopNonce: String?,
        val keyId: String?,
        val selectedPublicJwk: String,
        val label: String?,
    )

    private class IssuanceStageException(
        val code: WalletIssuanceErrorCode,
        cause: Throwable? = null,
    ) : Exception(code.name, cause)

    @Serializable
    private enum class SessionState { READY, AWAITING_CALLBACK, PROCESSING }

    private companion object {
        const val MAX_ACTIVE_SESSIONS = 32
        const val DPOP_HEADER = "DPoP"
        const val DPOP_NONCE_HEADER = "DPoP-Nonce"
        const val USE_DPOP_NONCE = "use_dpop_nonce"
        const val AUTHORIZATION_RESPONSE_ISS_PARAMETER_SUPPORTED =
            "authorization_response_iss_parameter_supported"
        const val ACTIVE_RECORD_PREFIX = "active:"
        const val DEFERRED_RECORD_PREFIX = "deferred:"

        fun activeRecordId(sessionId: String): String = "$ACTIVE_RECORD_PREFIX$sessionId"
        fun deferredRecordId(id: String): String = "$DEFERRED_RECORD_PREFIX$id"
    }
}
