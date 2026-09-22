package id.walt.wallet2.handlers

import id.walt.openid4vci.metadata.issuer.CredentialIssuerMetadata
import id.walt.wallet2.data.StoredCredential
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletKeyStoreEntry
import id.walt.wallet2.data.WalletSessionEvent
import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.uuid.Uuid

/** Authorization already acquired by a grant-specific caller. DPoP always uses its sender key. */
internal class CredentialIssuanceAccess(
    val accessToken: String,
    val senderKey: WalletKeyStoreEntry,
    dpopAlgorithms: Set<String>?,
    val proofIssuer: String?,
) {
    val dpop = dpopAlgorithms?.let { DpopRequestContext(it, senderKey) }
    val tokenType: String get() = if (dpop == null) "Bearer" else "DPoP"
    val persistable: Boolean get() = senderKey.keyReference != null
}

/** Executes accepted targets once; grant acquisition and session ownership stay with the caller. */
internal suspend fun executeCredentialTargets(
    wallet: Wallet,
    httpClient: HttpClient,
    issuerMetadata: CredentialIssuerMetadata,
    targets: List<ResolvedCredentialIssuanceTarget>,
    access: CredentialIssuanceAccess,
    sessions: WalletIssuanceSessionService,
    sessionId: String? = null,
    labelFor: (String) -> String? = { issuerMetadata.credentialConfigurationsSupported.getValue(it).credentialMetadata?.display?.firstOrNull()?.name },
    requestMetadata: JsonObject? = null,
    ensureOwned: suspend () -> Unit = {},
    onEvent: suspend (WalletSessionEvent) -> Unit = {},
    beforeCredentialsStored: suspend (Int) -> Unit = {},
    onCredentialStored: suspend (StoredCredential) -> Unit,
    onDeferredCredential: suspend (DeferredCredentialTransaction) -> Unit,
) {
    for ((index, resolved) in targets.withIndex()) {
        val (target, selected) = resolved
        var stage = CredentialIssuanceStage.PROOF
        try {
            ensureOwned()
            val configuration = issuerMetadata.credentialConfigurationsSupported.getValue(target.credentialConfigurationId)
            val algorithms = supportedJwtProofAlgorithms(configuration.proofTypesSupported, wallet.attachedKeyAttestationProvider() != null)
            stage = CredentialIssuanceStage.REQUEST
            val response = WalletIssuanceHandler.requestCredentialWithNonceRetry(
                FetchCredentialRequest(Url(issuerMetadata.credentialEndpoint), access.accessToken,
                    target.credentialConfigurationId, credentialIdentifier = target.credentialIdentifier),
                issuerMetadata.nonceEndpoint, httpClient,
                buildProof = algorithms?.let { { nonce ->
                    WalletIssuanceHandler.buildProofCollection(wallet, selected.bindings, configuration,
                        issuerMetadata.credentialIssuer, nonce, access.proofIssuer)
                } },
                onProofGenerated = { if (algorithms != null) onEvent.emitSafely(WalletSessionEvent.issuance_proof_signed) },
                dpop = access.dpop,
                onStage = { stage = it },
            )
            ensureOwned()
            stage = CredentialIssuanceStage.RESPONSE
            val label = labelFor(target.credentialConfigurationId)
            val metadata = storedCredentialDisplayMetadata(issuerMetadata, target.credentialConfigurationId, requestMetadata)
            val rawCredentials = response.credentials
            if (rawCredentials == null) {
                val endpoint = requireNotNull(issuerMetadata.deferredCredentialEndpoint) { "Issuer did not advertise a deferred endpoint" }
                val public = WalletIssuanceContinuation(
                    id = Uuid.random().toString(), credentialConfigurationId = target.credentialConfigurationId,
                    credentialIdentifier = target.credentialIdentifier, intervalSeconds = requireNotNull(response.interval))
                val transaction = DeferredCredentialTransaction(
                    target.credentialConfigurationId, target.credentialIdentifier, requireNotNull(response.transactionId),
                    selected.bindings.map { CredentialHolderBinding(it.material.keyId, it.did) },
                    intervalSeconds = requireNotNull(response.interval), proofRequired = algorithms != null,
                    deferredCredentialId = public.id)
                stage = CredentialIssuanceStage.STORAGE
                withContext(NonCancellable) {
                    try {
                        ensureOwned()
                        sessions.retainDeferredCredential(public, configuration, selected, transaction.proofRequired,
                            endpoint, transaction.transactionId, access.accessToken, access.tokenType,
                            access.dpop?.algorithms, access.senderKey, access.persistable, label, metadata,
                            sessionId = sessionId ?: public.id, dpopNonce = access.dpop?.nonce)
                    } finally {
                        // Keep accepted progress during a storage outage, but not after terminal closure.
                        sessions.ensureOpen()
                        ensureOwned()
                        onDeferredCredential(transaction)
                    }
                }
                onEvent.emitSafely(WalletSessionEvent.issuance_deferred)
            } else {
                onEvent.emitSafely(WalletSessionEvent.issuance_credential_received)
                withIssuedCredentialNotification(
                    httpClient = httpClient,
                    notificationEndpoint = issuerMetadata.notificationEndpoint,
                    notificationId = response.notificationId,
                    accessToken = access.accessToken,
                    tokenType = access.tokenType,
                    dpopProofFactory = access.dpop?.let {
                        dpopProofFactoryFor(access.tokenType, it.algorithms, access.senderKey, access.accessToken)
                    },
                ) {
                    val prepared = wallet.prepareIssuedCredentials(rawCredentials.map {
                        val value = it.credential
                        if (value is JsonPrimitive) value.content else value.toString()
                    }, selected.bindings, label, metadata, proofRequired = algorithms != null,
                        expectedConfiguration = configuration)
                    stage = CredentialIssuanceStage.STORAGE
                    ensureOwned()
                    val outcome = sessions.storeReceivedCredentials(
                        prepared, target.credentialConfigurationId, target.credentialIdentifier,
                        persistable = access.persistable, sessionId = sessionId,
                        beforeCredentialsStored = beforeCredentialsStored,
                        onCredentialStored = { entry ->
                            stage = CredentialIssuanceStage.OBSERVER
                            onCredentialStored(entry)
                            stage = CredentialIssuanceStage.STORAGE
                        },
                    )
                    if (outcome is WalletIssuanceOutcome.Failed) throw CredentialStorageException(outcome)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw CredentialIssuanceException(
                CredentialIssuanceFailure(target, stage, targets.drop(index + 1).map { it.target }), error,
                storageOutcome = (error as? CredentialStorageException)?.outcome,
            )
        }
    }
}
