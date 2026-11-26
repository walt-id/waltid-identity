package id.walt.wallet2.mobile.issuance

import id.walt.crypto2.keys.Key
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.did.dids.Crypto2DidService
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.handlers.CredentialHolderBinding
import id.walt.wallet2.handlers.WalletCredentialSelection
import id.walt.wallet2.handlers.WalletIssuanceBatchSession
import id.walt.wallet2.handlers.WalletIssuanceOfferRequest
import id.walt.wallet2.mobile.identity.MobileDidSupport
import id.walt.wallet2.mobile.identity.WalletKeyType
import id.walt.wallet2.mobile.identity.SigningIdentityKeyPolicy
import id.walt.wallet2.mobile.identity.SigningIdentityManager
import id.walt.wallet2.mobile.identity.SigningIdentityState
import id.walt.wallet2.persistence.keys.MobileWalletKeyStore
import io.ktor.http.Url
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/** Chooses the issuance holder and owns generated batch keys until the session accepts them. */
internal class MobileWalletIssuanceKeys(
    private val keyStore: MobileWalletKeyStore,
    private val didStore: WalletDidStore,
    private val signingIdentityManager: SigningIdentityManager?,
    private val defaultKeyUseAuthorizationPolicy: KeyUseAuthorizationPolicy,
    private val generateAndPersistHolderKey: suspend (WalletKeyType, KeyUseAuthorizationPolicy) -> Key,
) {
    suspend fun offerRequest(request: IssuanceRequest): WalletIssuanceOfferRequest {
        val (offer, keyId, did, keyPolicy) = request
        val active = if (keyId == null || did == null) signingIdentityManager?.state() else null
        val identity = (active as? SigningIdentityState.Active)?.identity
        check(signingIdentityManager == null || active == null || identity != null) { "The wallet requires an active signing identity" }
        val selectedKeyId = keyId ?: identity?.keyId ?: keyStore.getDefaultKeyMaterial()?.keyId
            ?: error("No holder key is available for credential issuance")
        if (keyPolicy != SigningIdentityKeyPolicy.GeneralPurpose) {
            requireNotNull(signingIdentityManager) { "Restricted holder-key policy requires a managed signing identity" }
                .requireKeyPolicy(selectedKeyId, keyPolicy)
        }
        val selectedDid = did ?: identity?.did ?: didStore.getDefaultDid()
        return when (offer) {
            is CredentialOfferInput.Uri -> WalletIssuanceOfferRequest(
                offerUrl = Url(offer.value.trim()),
                offerJson = null,
                keyId = selectedKeyId,
                did = selectedDid,
            )
            is CredentialOfferInput.InlineJson -> WalletIssuanceOfferRequest(
                offerUrl = null,
                offerJson = Json.parseToJsonElement(offer.value).jsonObject,
                keyId = selectedKeyId,
                did = selectedDid,
            )
        }
    }

    suspend fun create(
        count: Int,
        keyType: WalletKeyType? = null,
        didMethod: String = "key",
        keyUseAuthorizationPolicy: KeyUseAuthorizationPolicy? = null,
    ): List<IssuanceHolderBinding> {
        require(count >= 1) { "At least one holder key must be requested" }
        MobileDidSupport.ensureInitialized()
        val created = mutableListOf<IssuanceHolderBinding>()
        try {
            repeat(count) {
                currentCoroutineContext().ensureActive()
                val key = generateAndPersistHolderKey(keyType ?: WalletKeyType.secp256r1,
                    keyUseAuthorizationPolicy ?: defaultKeyUseAuthorizationPolicy)
                created += IssuanceHolderBinding(key.id.value)
                currentCoroutineContext().ensureActive()
                val registered = Crypto2DidService.registerByKey(didMethod, key)
                created[created.lastIndex] = IssuanceHolderBinding(key.id.value, registered.did)
                didStore.addDid(WalletDidEntry(registered.did, registered.didDocument.toJsonObject()))
            }
            currentCoroutineContext().ensureActive()
            return created
        } catch (cause: Throwable) {
            cleanupHolderKeys(created, cause)
            throw cause
        }
    }

    suspend fun <T> withSelections(
        credentials: List<IssuanceCredentialSelection>?,
        operation: suspend (
            suspend (WalletIssuanceBatchSession, List<WalletCredentialSelection>?) -> List<WalletCredentialSelection>?,
            () -> Unit,
        ) -> T,
    ): T {
        val requested = credentials?.toList()
        val generated = mutableListOf<IssuanceHolderBinding>()
        var accepted = false
        var failure: Throwable? = null
        try {
            return operation({ session, previous ->
                if (requested == null) previous else requested.map { selection ->
                    require(session.offer.credentials.any { it.configurationId == selection.credentialConfigurationId }) { "Credential configuration was not offered" }
                    val previousSelection = previous?.singleOrNull {
                        it.credentialConfigurationId == selection.credentialConfigurationId && it.credentialIdentifier == selection.credentialIdentifier
                    }
                    val holders = selection.holders
                    val count = when (holders) {
                        is IssuanceHolders.Existing -> holders.bindings.size
                        is IssuanceHolders.NewKeys -> holders.count
                    }
                    require(count in 1..(session.batchSize ?: 1)) { "Requested copies exceed the issuer's batch limit" }
                    if (previous != null) {
                        require(previousSelection != null && previousSelection.holderBindings.size == count) {
                            "Cannot change holder selections after acceptance; start a new issuance session"
                        }
                        if (holders is IssuanceHolders.Existing) require(
                            previousSelection.holderBindings == holders.bindings.map { CredentialHolderBinding(it.keyId, it.did) }
                        ) { "Cannot change holder selections after acceptance; start a new issuance session" }
                        previousSelection
                    } else {
                        val bindings = when (holders) {
                            is IssuanceHolders.Existing -> holders.bindings
                            is IssuanceHolders.NewKeys -> {
                                val active = signingIdentityManager?.state()
                                val identity = (active as? SigningIdentityState.Active)?.identity
                                check(active == null || identity != null) { "The wallet requires an active signing identity" }
                                create(holders.count,
                                    keyUseAuthorizationPolicy = identity?.authorization,
                                ).also { generated += it }
                            }
                        }
                        WalletCredentialSelection(
                            selection.credentialConfigurationId, selection.credentialIdentifier,
                            bindings.map { CredentialHolderBinding(it.keyId, it.did) },
                        )
                    }
                }.also {
                    require(previous == null || it.size == previous.size) { "Cannot change holder selections after acceptance; start a new issuance session" }
                }
            }, { accepted = true })
        } catch (cause: Throwable) {
            failure = cause
            throw cause
        } finally {
            if (!accepted) cleanupHolderKeys(generated, failure)
        }
    }

    private suspend fun cleanupHolderKeys(bindings: List<IssuanceHolderBinding>, cause: Throwable?) =
        withContext(NonCancellable) {
            var failure = cause
            fun remember(error: Throwable) {
                if (failure == null) failure = error else failure!!.addSuppressed(error)
            }
            for (binding in bindings) {
                try { binding.did?.let { didStore.removeDid(it) } }
                catch (error: Throwable) { remember(error) }
                try { keyStore.removeKey(binding.keyId) }
                catch (error: Throwable) { remember(error) }
            }
            if (cause == null) failure?.let { throw it }
        }
}
