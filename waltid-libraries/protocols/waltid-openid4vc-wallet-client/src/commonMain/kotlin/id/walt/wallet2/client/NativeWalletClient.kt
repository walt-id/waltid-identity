package id.walt.wallet2.client

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
import id.walt.wallet2.stores.inmemory.InMemoryCredentialStore
import id.walt.wallet2.stores.inmemory.InMemoryDidStore
import id.walt.wallet2.stores.inmemory.InMemoryKeyStore
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.HttpWalletAttestationProvider
import io.ktor.http.Url
import kotlinx.coroutines.flow.toList
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class NativeWalletBootstrapResult(
    val keyId: String,
    val did: String,
)

data class NativeWalletCredential(
    val id: String,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val addedAt: String?,
)

data class NativeWalletPresentationResult(
    val success: Boolean,
    val redirectTo: String?,
)

data class WalletAttestationConfig(
    val enterpriseBaseUrl: String,
    val attesterPath: String,
    val bearerToken: String = "",
    val enterpriseHostHeader: String = "",
)

@OptIn(ExperimentalUuidApi::class)
class NativeWalletClient(
    walletId: String = Uuid.random().toString(),
    private val attestationConfig: WalletAttestationConfig? = null,
    private val onEvent: suspend (WalletSessionEvent) -> Unit = {},
) {
    private val keyStore = InMemoryKeyStore()
    private val didStore = InMemoryDidStore()
    private val credentialStore = InMemoryCredentialStore()

    private val attestationAssembler: ClientAttestationAssembler? = attestationConfig?.let { config ->
        ClientAttestationAssembler(
            HttpWalletAttestationProvider(
                enterpriseBaseUrl = config.enterpriseBaseUrl,
                attesterPath = config.attesterPath,
                bearerToken = config.bearerToken,
                enterpriseHostHeader = config.enterpriseHostHeader,
            )
        )
    }

    private val wallet = Wallet(
        id = walletId,
        keyStores = listOf(keyStore),
        didStore = didStore,
        credentialStores = listOf(credentialStore),
    )

    suspend fun bootstrap(
        keyType: KeyType = KeyType.secp256r1,
        didMethod: String = "key",
    ): NativeWalletBootstrapResult {
        DidService.minimalInit()
        val key = JWKKey.generate(keyType)
        val keyId = keyStore.addKey(key)
        val didResult = DidService.registerByKey(didMethod, key)

        didStore.addDid(
            WalletDidEntry(
                did = didResult.did,
                document = didResult.didDocument.toJsonObject(),
            )
        )

        return NativeWalletBootstrapResult(
            keyId = keyId,
            did = didResult.did,
        )
    }

    suspend fun receive(
        offerUrl: String,
        txCode: String? = null,
        clientId: String = "wallet-client",
    ): List<String> =
        WalletIssuanceHandler.receiveCredential(
            wallet = wallet,
            request = ReceiveCredentialRequest(
                offerUrl = Url(offerUrl.trim()),
                txCode = txCode?.ifBlank { null },
                clientId = clientId,
            ),
            attestationAssembler = attestationAssembler,
            onEvent = onEvent,
        ).credentialIds

    suspend fun credentials(): List<NativeWalletCredential> =
        wallet.streamAllCredentials().toList().map { credential ->
            val meta = credential.toMetadata()
            NativeWalletCredential(
                id = meta.id,
                format = meta.format,
                issuer = meta.issuer,
                subject = meta.subject,
                label = meta.label,
                addedAt = meta.addedAt?.toString(),
            )
        }

    suspend fun present(
        requestUrl: String,
        did: String? = null,
        runPolicies: Boolean? = null,
    ): NativeWalletPresentationResult {
        val result = WalletPresentationHandler.presentCredential(
            wallet = wallet,
            request = PresentCredentialRequest(
                requestUrl = Url(requestUrl.trim()),
                did = did,
                runPolicies = runPolicies,
            ),
            onEvent = onEvent,
        )

        return NativeWalletPresentationResult(
            success = result.transmissionSuccess ?: false,
            redirectTo = result.redirectTo,
        )
    }
}
