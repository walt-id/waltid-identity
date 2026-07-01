package id.walt.wallet2.mobile

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidService
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.handlers.PresentCredentialRequest
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import id.walt.wallet2.handlers.WalletIssuanceHandler
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.HttpWalletAttestationProvider
import io.ktor.http.Url
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonElement

data class MobileWalletBootstrapResult(
    val keyId: String,
    val did: String,
)

data class MobileWalletCredential(
    val id: String,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val addedAt: String?,
)

data class MobileWalletPresentationResult(
    val success: Boolean,
    val redirectTo: String?,
    val verifierResponse: JsonElement? = null,
)

data class WalletAttestationConfig(
    val enterpriseBaseUrl: String,
    val attesterPath: String,
    val bearerToken: String = "",
    val enterpriseHostHeader: String = "",
)

class MobileWallet(
    walletId: String,
    private val keyStore: WalletKeyStore,
    private val didStore: WalletDidStore,
    credentialStore: WalletCredentialStore,
    private val keyGenerator: suspend (KeyType) -> Key,
    private val defaultKeyType: KeyType = KeyType.secp256r1,
    attestationConfig: WalletAttestationConfig? = null,
    private val onEvent: suspend (WalletSessionEvent) -> Unit = {},
) {
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
        keyType: KeyType? = null,
        didMethod: String = "key",
    ): MobileWalletBootstrapResult {
        DidService.minimalInit()

        val existingDids = didStore.listDids().toList()
        if (existingDids.isNotEmpty()) {
            val existingKeys = keyStore.listKeys().toList()
            require(existingKeys.isNotEmpty()) {
                "Wallet '${wallet.id}' has persisted DIDs but no persisted keys"
            }
            return MobileWalletBootstrapResult(
                keyId = existingKeys.first().keyId,
                did = existingDids.first().did,
            )
        }

        val effectiveKeyType = keyType ?: defaultKeyType
        val key = keyGenerator(effectiveKeyType)
        val keyId = keyStore.addKey(key)
        val didResult = DidService.registerByKey(didMethod, key)

        didStore.addDid(
            WalletDidEntry(
                did = didResult.did,
                document = didResult.didDocument.toJsonObject(),
            )
        )

        return MobileWalletBootstrapResult(
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

    suspend fun credentials(): List<MobileWalletCredential> =
        wallet.streamAllCredentials().toList().map { credential ->
            val meta = credential.toMetadata()
            MobileWalletCredential(
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
    ): MobileWalletPresentationResult {
        val result = WalletPresentationHandler.presentCredential(
            wallet = wallet,
            request = PresentCredentialRequest(
                requestUrl = Url(requestUrl.trim()),
                did = did,
                runPolicies = runPolicies,
            ),
            onEvent = onEvent,
        )

        return MobileWalletPresentationResult(
            success = result.transmissionSuccess ?: false,
            redirectTo = result.redirectTo,
            verifierResponse = result.verifierResponse,
        )
    }
}
