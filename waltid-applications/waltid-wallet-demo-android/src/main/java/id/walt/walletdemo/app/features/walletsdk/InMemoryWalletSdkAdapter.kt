package id.walt.walletdemo.app.features.walletsdk

import id.walt.crypto.keys.KeyType
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.did.dids.DidService
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.handlers.PresentCredentialRequest
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import id.walt.wallet2.handlers.WalletIssuanceHandler
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.walt.wallet2.client.WalletBootstrapResult
import id.walt.wallet2.client.WalletClientAdapter
import id.walt.wallet2.client.WalletCredentialSummary
import id.walt.wallet2.client.WalletPresentationResult
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import io.ktor.http.Url
import kotlinx.coroutines.flow.toList
import java.util.UUID

class InMemoryWalletSdkAdapter(
    private val walletId: String = UUID.randomUUID().toString(),
    private val onEvent: suspend (WalletSessionEvent) -> Unit = {},
) : WalletClientAdapter {

    private val keyStore = InMemoryKeyStore()
    private val didStore = InMemoryDidStore()
    private val credentialStore = InMemoryCredentialStore()

    private val wallet = Wallet(
        id = walletId,
        keyStores = listOf(keyStore),
        didStore = didStore,
        credentialStores = listOf(credentialStore),
    )

    override suspend fun bootstrapWallet(
        keyType: KeyType,
        didMethod: String,
    ): WalletBootstrapResult {
        DidService.minimalInit()
        val key = JWKKey.generate(keyType)
        val keyId = keyStore.addKey(key)

        val didResult = DidService.registerByKey(didMethod, key)
        didStore.addDid(
            WalletDidEntry(
                did = didResult.did,
                document = didResult.didDocument.toString(),
            )
        )

        return WalletBootstrapResult(
            keyId = keyId,
            did = didResult.did,
        )
    }

    override suspend fun receiveCredential(
        offerUrl: String,
        txCode: String?,
        clientId: String,
    ): List<String> {
        val result = WalletIssuanceHandler.receiveCredential(
            wallet = wallet,
            request = ReceiveCredentialRequest(
                offerUrl = Url(offerUrl),
                txCode = txCode,
                clientId = clientId,
            ),
            onEvent = onEvent,
        )
        return result.credentialIds
    }

    override suspend fun listCredentials(): List<WalletCredentialSummary> =
        wallet.streamAllCredentials().toList().map { credential ->
            val meta = credential.toMetadata()
            WalletCredentialSummary(
                id = meta.id,
                format = meta.format,
                issuer = meta.issuer,
                subject = meta.subject,
                label = meta.label,
                addedAt = meta.addedAt?.toString(),
            )
        }

    override suspend fun presentCredential(
        requestUrl: String,
        did: String?,
        runPolicies: Boolean?,
    ): WalletPresentationResult {
        val result = WalletPresentationHandler.presentCredential(
            wallet = wallet,
            request = PresentCredentialRequest(
                requestUrl = Url(requestUrl),
                did = did,
                runPolicies = runPolicies,
            ),
            onEvent = onEvent,
        )
        return WalletPresentationResult(
            getUrl = result.getUrl,
            formPostHtml = result.formPostHtml,
            transmissionSuccess = result.transmissionSuccess,
            redirectTo = result.redirectTo,
        )
    }
}
