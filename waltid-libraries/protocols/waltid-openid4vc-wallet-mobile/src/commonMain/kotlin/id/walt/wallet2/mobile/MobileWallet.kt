@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.data.WalletX509TrustConfig
import id.walt.wallet2.handlers.PresentCredentialRequest
import id.walt.wallet2.handlers.PresentationCredentialOption
import id.walt.wallet2.handlers.PresentationCredentialRequirement
import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.handlers.PresentationDisclosureSelection
import id.walt.wallet2.handlers.PreviewPresentationRequest
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import id.walt.wallet2.handlers.ResolveOfferRequest
import id.walt.wallet2.handlers.SubmitPresentationRequest
import id.walt.wallet2.handlers.WalletIssuanceHandler
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.HttpWalletAttestationProvider
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Result returned after a mobile wallet has been initialized with signing material and a DID.
 *
 * @property keyId Identifier of the persisted signing key used by the wallet.
 * @property did Decentralized identifier registered for the persisted key.
 */
public data class MobileWalletBootstrapResult(
    public val keyId: String,
    public val did: String,
)

/**
 * Credential entry suitable for mobile UI lists and detail display.
 *
 * The credential content is exposed as a JSON string so Kotlin, Swift, and other
 * consumers can decode it with native platform tools without depending on Kotlinx
 * JSON value types in the public mobile API.
 *
 * @property id Wallet-local credential identifier.
 * @property format Credential format, such as `jwt_vc_json`, `vc+sd-jwt`, or `mso_mdoc`.
 * @property issuer Issuer identifier extracted from the credential when available.
 * @property subject Subject identifier extracted from the credential when available.
 * @property label Optional display label stored with the credential.
 * @property addedAt ISO-8601 timestamp string for when the credential was added, when known.
 * @property credentialDataJson Parsed credential data encoded as JSON for app-side display.
 */
public data class MobileWalletCredential(
    public val id: String,
    public val format: String,
    public val issuer: String?,
    public val subject: String?,
    public val label: String?,
    public val addedAt: String?,
    public val credentialDataJson: String,
)

/**
 * Result of resolving an OpenID4VCI credential offer before issuance.
 *
 * @property transactionCodeRequired Whether the app must collect a transaction code from the user.
 */
public data class MobileWalletOfferResolution(
    public val transactionCodeRequired: Boolean,
    /** Issuer identifier (URL) from the credential offer. */
    public val credentialIssuer: String,
    /** Credential configuration IDs advertised in the offer. */
    public val offeredCredentials: List<String>,
)

/**
 * Result returned after attempting to answer an OpenID4VP presentation request.
 *
 * @property success `true` when the wallet transmitted the presentation successfully.
 * @property redirectTo Optional verifier redirect URI returned by the presentation flow.
 * @property verifierResponseJson Raw verifier response body encoded as JSON, when the verifier returns structured JSON.
 */
public data class MobileWalletPresentationResult(
    public val success: Boolean,
    public val redirectTo: String?,
    public val verifierResponseJson: String? = null,
)

/**
 * OAuth 2.0 client-attestation configuration used during mobile issuance.
 *
 * The wallet uses this configuration to request a client attestation JWT from the
 * enterprise client-attester service and attach the resulting proof to token requests.
 *
 * @property baseUrl Base URL of the deployment that hosts the attester service.
 * @property attesterPath Path to the attester endpoint, relative to [baseUrl].
 * @property bearerToken Optional bearer token for protected attester endpoints.
 * @property hostHeader Optional `Host` header override for tunneled local tests.
 */
public data class WalletAttestationConfig(
    public val baseUrl: String,
    public val attesterPath: String,
    public val bearerToken: String = "",
    public val hostHeader: String = "",
)

/**
 * Android and iOS facade for the walt.id wallet SDK.
 *
 * Use [MobileWalletFactory] to create instances with the correct platform storage, key provider,
 * and SQLDelight driver. The facade keeps the public mobile API intentionally small while delegating
 * protocol work to the core wallet handlers.
 */
public class MobileWallet internal constructor(
    walletId: String,
    private val keyStore: WalletKeyStore,
    private val didStore: WalletDidStore,
    private val credentialStore: WalletCredentialStore,
    private val keyGenerator: suspend (KeyType) -> Key,
    private val defaultKeyType: MobileWalletKeyType = MobileWalletKeyType.secp256r1,
    attestationConfig: WalletAttestationConfig? = null,
    requestObjectX509Trust: WalletX509TrustConfig? = null,
    requestObjectAudience: String = "https://self-issued.me/v2",
    private val transactionDataProfiles: List<MobileWalletTransactionDataProfile> = emptyList(),
    private val onEvent: suspend (MobileWalletEvent) -> Unit = {},
    private val deleteLocalPersistence: suspend () -> Unit = {},
) {
    private val eventStream = MobileWalletEventStream()

    /**
     * Buffered stream of recent issuance and presentation events emitted by this wallet.
     */
    public val events: Flow<MobileWalletEvent> = eventStream.events

    private val attestationAssembler: ClientAttestationAssembler? = attestationConfig?.let { config ->
        ClientAttestationAssembler(
            HttpWalletAttestationProvider(
                baseUrl = config.baseUrl,
                attesterPath = config.attesterPath,
                bearerToken = config.bearerToken,
                hostHeader = config.hostHeader,
            )
        )
    }

    private val wallet = Wallet(
        id = walletId,
        keyStores = listOf(keyStore),
        didStore = didStore,
        credentialStores = listOf(credentialStore),
        requestObjectX509TrustPolicy = requestObjectX509Trust?.toTrustPolicy(),
        requestObjectAudience = requestObjectAudience,
    )

    /**
     * Initializes the wallet by creating or reusing platform-backed key material and a DID.
     *
     * If the wallet already contains persisted DIDs, the first persisted DID and key are reused.
     *
     * @param keyType Optional key type override. When omitted, [MobileWalletConfig.defaultKeyType] is used.
     * @param didMethod DID method used for registering a new DID. The default `key` method is handled locally.
     * @return The key identifier and DID used by this wallet.
     * @throws IllegalArgumentException When persisted DID state exists without a persisted key.
     */
    public suspend fun bootstrap(
        keyType: MobileWalletKeyType? = null,
        didMethod: String = "key",
    ): MobileWalletBootstrapResult {
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
        val key = keyGenerator(effectiveKeyType.toKeyType())
        val keyId = keyStore.addKey(key)
        val didResult = registerDidByKey(didMethod, key)

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

    private suspend fun registerDidByKey(didMethod: String, key: Key) =
        when (didMethod.lowercase()) {
            "key" -> DidKeyRegistrar().registerByKey(key, DidKeyCreateOptions(keyType = key.keyType))
            else -> {
                DidService.minimalInit()
                DidService.registerByKey(didMethod, key)
            }
        }

    /**
     * Resolves a credential offer and reports any transaction code the app must collect.
     *
     * Apps can use this before [receive] to decide whether to prompt the user for a code. While the
     * preview is retained, the matching [receive] call reuses this exact resolution.
     *
     * @param offerUrl Credential offer URL, including `openid-credential-offer://` URLs.
     * @return Issuer, offered credential, and transaction-code metadata for app-side review.
     */
    public suspend fun resolveOffer(offerUrl: String): MobileWalletOfferResolution =
        WalletIssuanceHandler.previewOffer(
            wallet = wallet,
            request = ResolveOfferRequest(offerUrl = Url(offerUrl.trim())),
        ).let { result ->
            MobileWalletOfferResolution(
                transactionCodeRequired = result.txCodeRequired,
                credentialIssuer = result.credentialIssuer,
                offeredCredentials = result.offeredCredentials,
            )
        }

    /**
     * Receives credentials from an OpenID4VCI credential offer.
     *
     * A matching prior [resolveOffer] call binds issuance to the reviewed resolution. Without one,
     * the offer is resolved as part of this call.
     *
     * @param offerUrl Credential offer URL, including `openid-credential-offer://` URLs.
     * @param txCode Optional transaction code for pre-authorized offers.
     * @param clientId Client identifier sent to the issuer.
     * @return Wallet-local identifiers of the stored credentials.
     */
    public suspend fun receive(
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
            onEvent = ::emitSessionEvent,
        ).credentialIds

    /**
     * Lists all credentials currently stored in the mobile wallet.
     *
     * @return Credential entries, including display JSON, ordered by the underlying credential store.
     */
    public suspend fun credentials(): List<MobileWalletCredential> =
        wallet.streamAllCredentials().toList().map { credential ->
            val meta = credential.toMetadata()
            MobileWalletCredential(
                id = meta.id,
                format = meta.format,
                issuer = meta.issuer,
                subject = meta.subject,
                label = meta.label,
                addedAt = meta.addedAt?.toString(),
                credentialDataJson = credential.credential.credentialData.encodeJsonObject(),
            )
        }

    /**
     * Presents matching wallet credentials to an OpenID4VP verifier request.
     *
     * This immediate submission API is intended for callers that already handled
     * request review and user consent. Apps that need to display verifier details,
     * credential choices, selective disclosures, or transaction data should use
     * [previewPresentation] followed by [submitPresentation].
     *
     * @param requestUrl Authorization request URL received from the verifier.
     * @param did Optional DID override for selecting the wallet DID used in the presentation.
     * @param runPolicies Optional override for verifier policy execution in the core presentation handler.
     * @return Transmission status, optional redirect, and optional verifier response details.
     */
    public suspend fun present(
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
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            onEvent = ::emitSessionEvent,
        )

        return MobileWalletPresentationResult(
            success = result.transmissionSuccess ?: false,
            redirectTo = result.redirectTo,
            verifierResponseJson = result.verifierResponse?.let {
                Json.encodeToString(JsonElement.serializer(), it)
            },
        )
    }

    /**
     * Resolves and previews an OpenID4VP presentation request without submitting credentials.
     */
    public suspend fun previewPresentation(requestUrl: String): MobileWalletPresentationPreview {
        val result = WalletPresentationHandler.previewPresentation(
            wallet = wallet,
            request = PreviewPresentationRequest(
                requestUrl = Url(requestUrl.trim()),
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            onEvent = ::emitSessionEvent,
        )

        val profilesByType = transactionDataProfiles.associateBy { it.type }
        val encryptionRequirements = WalletPresentationHandler.inspectEncryptionRequirements(result.authorizationRequest)
        val encryption = if (encryptionRequirements.isEncryptionRequired) {
            MobileWalletEncryptionInfo.Required(
                contentEncryptionAlgorithm = requireNotNull(encryptionRequirements.encAlgorithm),
                keyManagementAlgorithm = requireNotNull(encryptionRequirements.algAlgorithm),
                verifierKeyThumbprint = requireNotNull(encryptionRequirements.verifierKeyThumbprint),
            )
        } else {
            MobileWalletEncryptionInfo.NotRequired
        }
        return MobileWalletPresentationPreview(
            request = MobileWalletPresentationRequestInfo(
                clientId = result.authorizationRequest.clientId,
                verifierName = result.authorizationRequest.clientMetadata?.clientName,
                responseUri = result.authorizationRequest.responseUri,
                state = result.authorizationRequest.state,
                nonce = result.authorizationRequest.nonce,
                responseMode = result.authorizationRequest.responseMode?.let { mode ->
                    Json.encodeToString(OpenID4VPResponseMode.serializer(), mode).trim('"')
                },
                transactionData = result.transactionData.map { item ->
                    val profile = profilesByType[item.type]
                    MobileWalletTransactionDataItem(
                        type = item.type,
                        displayName = profile?.displayName ?: item.type,
                        credentialQueryIds = item.credentialQueryIds,
                        supportedFields = profile?.fields.orEmpty(),
                        rawJson = item.rawJson.encodeJsonObject(),
                        detailsJson = item.details.encodeJsonObject(),
                    )
                },
            ),
            credentialOptions = result.credentialOptions.map { it.toMobileCredentialOption() },
            credentialRequirements = result.credentialRequirements.map { it.toMobileCredentialRequirement() },
            encryption = encryption,
        )
    }

    /**
     * Submits a presentation using the credential options selected by the user from [previewPresentation].
     */
    public suspend fun submitPresentation(
        requestUrl: String,
        selectedCredentialOptions: List<MobileWalletPresentationCredentialSelection>,
        selectedDisclosureOptions: List<MobileWalletPresentationDisclosureSelection>? = null,
        did: String? = null,
        runPolicies: Boolean? = null,
    ): MobileWalletPresentationResult =
        WalletPresentationHandler.submitPresentation(
            wallet = wallet,
            request = SubmitPresentationRequest(
                requestUrl = Url(requestUrl.trim()),
                selectedCredentialOptions = selectedCredentialOptions.map {
                    PresentationCredentialSelection(
                        queryId = it.queryId,
                        credentialId = it.credentialId,
                    )
                },
                selectedDisclosureOptions = selectedDisclosureOptions?.map {
                    PresentationDisclosureSelection(
                        queryId = it.queryId,
                        credentialId = it.credentialId,
                        path = it.path,
                    )
                },
                did = did,
                runPolicies = runPolicies,
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            onEvent = ::emitSessionEvent,
        ).toMobilePresentationResult()

    /**
     * Deletes local wallet material owned by this mobile wallet instance.
     *
     * The active key, credential, and DID stores receive store-level remove calls. The wallet then closes
     * and deletes the encrypted local database and deletes the configured database key.
     */
    public suspend fun deleteWallet() {
        keyStore.listKeys().toList().forEach { key ->
            keyStore.removeKey(key.keyId)
        }
        credentialStore.listCredentials().toList().forEach { credential ->
            credentialStore.removeCredential(credential.id)
        }
        didStore.listDids().toList().forEach { did ->
            didStore.removeDid(did.did)
        }
        deleteLocalPersistence()
    }

    private suspend fun emitSessionEvent(event: WalletSessionEvent) {
        val mobileEvent = event.toMobileWalletEvent()
        eventStream.tryEmit(mobileEvent)
        onEvent(mobileEvent)
    }

    private fun PresentationCredentialOption.toMobileCredentialOption(): MobileWalletPresentationCredentialOption =
        MobileWalletPresentationCredentialOption(
            queryId = queryId,
            credentialId = credentialId,
            multiple = multiple,
            format = format,
            issuer = issuer,
            subject = subject,
            label = label,
            credentialDataJson = credentialData.encodeJsonObject(),
            disclosures = disclosures.map { disclosure ->
                MobileWalletPresentationDisclosure(
                    path = disclosure.path,
                    name = disclosure.name,
                    valueJson = Json.encodeToString(JsonElement.serializer(), disclosure.value),
                    displayValue = disclosure.value.displayValue(),
                    selectivelyDisclosable = disclosure.selectivelyDisclosable,
                    required = disclosure.required,
                    selectable = disclosure.selectable,
                )
            },
        )

    private fun PresentationCredentialRequirement.toMobileCredentialRequirement(): MobileWalletPresentationCredentialRequirement =
        MobileWalletPresentationCredentialRequirement(options = options)

    private fun WalletPresentResult.toMobilePresentationResult(): MobileWalletPresentationResult =
        MobileWalletPresentationResult(
            success = transmissionSuccess ?: false,
            redirectTo = redirectTo,
            verifierResponseJson = verifierResponse?.let {
                Json.encodeToString(JsonElement.serializer(), it)
            },
        )

    private fun JsonObject.encodeJsonObject(): String =
        Json.encodeToString(JsonObject.serializer(), this)

    private fun JsonElement.displayValue(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull
            else -> toString()
        }
}
