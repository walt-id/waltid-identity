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
import id.walt.wallet2.handlers.ResolveVpRequestRequest
import id.walt.wallet2.handlers.WalletIssuanceHandler
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.HttpWalletAttestationProvider
import io.ktor.http.Url
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonElement

/**
 * Result returned after a mobile wallet has been initialized with signing material and a DID.
 *
 * @property keyId Identifier of the persisted signing key used by the wallet.
 * @property did Decentralized identifier registered for the persisted key.
 */
data class MobileWalletBootstrapResult(
    val keyId: String,
    val did: String,
)

/**
 * Lightweight credential summary suitable for mobile UI lists.
 *
 * @property id Wallet-local credential identifier.
 * @property format Credential format, such as `jwt_vc_json`, `vc+sd-jwt`, or `mso_mdoc`.
 * @property issuer Issuer identifier extracted from the credential when available.
 * @property subject Subject identifier extracted from the credential when available.
 * @property label Optional display label stored with the credential.
 * @property addedAt ISO-8601 timestamp string for when the credential was added, when known.
 */
data class MobileWalletCredential(
    val id: String,
    val format: String,
    val issuer: String?,
    val subject: String?,
    val label: String?,
    val addedAt: String?,
)

/**
 * Result returned after attempting to answer an OpenID4VP presentation request.
 *
 * @property success `true` when the wallet transmitted the presentation successfully.
 * @property redirectTo Optional verifier redirect URI returned by the presentation flow.
 * @property verifierResponse Raw verifier response body, when the verifier returns structured JSON.
 */
data class MobileWalletPresentationResult(
    val success: Boolean,
    val redirectTo: String?,
    val verifierResponse: JsonElement? = null,
)

/**
 * Encryption details for a VP authorization request.
 *
 * Per OID4VP 1.0 §6, when the verifier requires `response_mode=direct_post.jwt`,
 * the wallet must encrypt its response. This result provides details for
 * consent screen UI display.
 *
 * @property isEncryptionRequired True if the verifier requires encrypted responses.
 * @property encAlgorithm Content encryption algorithm (e.g., "A128GCM"), null if not applicable.
 * @property algAlgorithm Key agreement algorithm (e.g., "ECDH-ES"), null if not applicable.
 * @property verifierKeyThumbprint SHA-256 thumbprint of verifier's encryption key for audit display.
 */
data class MobileWalletEncryptionInfo(
    val isEncryptionRequired: Boolean,
    val encAlgorithm: String?,
    val algAlgorithm: String?,
    val verifierKeyThumbprint: String?,
)

/**
 * Result returned when inspecting a VP request before presenting.
 *
 * @property nonce The verifier's nonce for the presentation.
 * @property clientId The verifier's client identifier.
 * @property responseUri The verifier's response URI.
 * @property encryption Encryption requirements for this request.
 */
data class MobileWalletRequestInspection(
    val nonce: String?,
    val clientId: String?,
    val responseUri: String?,
    val encryption: MobileWalletEncryptionInfo,
)

/**
 * OAuth 2.0 client-attestation configuration used during mobile issuance.
 *
 * The wallet uses this configuration to request a client attestation JWT from the
 * enterprise client-attester service and attach the resulting proof to token requests.
 *
 * @property enterpriseBaseUrl Base URL of the enterprise deployment that hosts the attester service.
 * @property attesterPath Path to the attester endpoint, relative to [enterpriseBaseUrl].
 * @property bearerToken Optional bearer token for protected attester endpoints.
 * @property enterpriseHostHeader Optional `Host` header override for tunneled local enterprise tests.
 */
data class WalletAttestationConfig(
    val enterpriseBaseUrl: String,
    val attesterPath: String,
    val bearerToken: String = "",
    val enterpriseHostHeader: String = "",
)

/**
 * Android and iOS facade for the walt.id wallet SDK.
 *
 * Use [MobileWalletFactory] to create instances with the correct platform storage, key provider,
 * and SQLDelight driver. The facade keeps the public mobile API intentionally small while delegating
 * protocol work to the core wallet handlers.
 */
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

    /**
     * Initializes the wallet by creating or reusing platform-backed key material and a DID.
     *
     * If the wallet already contains persisted DIDs, the first persisted DID and key are reused.
     *
     * @param keyType Optional key type override. When omitted, [MobileWalletConfig.defaultKeyType] is used.
     * @param didMethod DID method passed to [DidService.registerByKey], for example `key`.
     * @return The key identifier and DID used by this wallet.
     * @throws IllegalArgumentException When persisted DID state exists without a persisted key.
     */
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

    /**
     * Receives credentials from an OpenID4VCI credential offer.
     *
     * @param offerUrl Credential offer URL, including `openid-credential-offer://` URLs.
     * @param txCode Optional transaction code for pre-authorized offers.
     * @param clientId Client identifier sent to the issuer.
     * @return Wallet-local identifiers of the stored credentials.
     */
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

    /**
     * Lists all credentials currently stored in the mobile wallet.
     *
     * @return Credential summaries ordered by the underlying credential store.
     */
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

    /**
     * Inspects a VP authorization request before presenting.
     *
     * Use this to show consent screen details including encryption status.
     * Call this before [present] to preview what the verifier is requesting
     * and whether the response will be encrypted.
     *
     * @param requestUrl Authorization request URL received from the verifier.
     * @return Request details including verifier info and encryption requirements.
     */
    suspend fun inspectRequest(requestUrl: String): MobileWalletRequestInspection {
        val resolved = WalletPresentationHandler.resolveRequest(
            ResolveVpRequestRequest(requestUrl = Url(requestUrl.trim()))
        )
        
        return MobileWalletRequestInspection(
            nonce = resolved.nonce,
            clientId = resolved.clientId,
            responseUri = resolved.responseUri?.toString(),
            encryption = MobileWalletEncryptionInfo(
                isEncryptionRequired = resolved.requiresEncryptedResponse,
                encAlgorithm = if (resolved.requiresEncryptedResponse) "A128GCM" else null,
                algAlgorithm = if (resolved.requiresEncryptedResponse) "ECDH-ES" else null,
                verifierKeyThumbprint = null // Would require full AuthorizationRequest resolution
            )
        )
    }

    /**
     * Presents matching wallet credentials to an OpenID4VP verifier request.
     *
     * @param requestUrl Authorization request URL received from the verifier.
     * @param did Optional DID override for selecting the wallet DID used in the presentation.
     * @param runPolicies Optional override for verifier policy execution in the core presentation handler.
     * @return Transmission status, optional redirect, and optional verifier response details.
     */
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
