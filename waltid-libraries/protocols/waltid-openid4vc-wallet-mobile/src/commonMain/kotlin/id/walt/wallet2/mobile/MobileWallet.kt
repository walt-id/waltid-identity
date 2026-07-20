@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package id.walt.wallet2.mobile

import id.walt.crypto.keys.Key
import id.walt.crypto.keys.KeyType
import id.walt.did.dids.DidService
import id.walt.did.dids.registrar.dids.DidKeyCreateOptions
import id.walt.did.dids.registrar.local.key.DidKeyRegistrar
import id.walt.verifier.openid.models.authorization.AuthorizationRequest
import id.walt.wallet2.data.Wallet
import id.walt.wallet2.data.WalletCredentialStore
import id.walt.wallet2.data.WalletDidEntry
import id.walt.wallet2.data.WalletDidStore
import id.walt.wallet2.data.WalletKeyStore
import id.walt.wallet2.data.WalletSessionEvent
import id.walt.wallet2.handlers.PresentCredentialRequest
import id.walt.wallet2.handlers.PresentationCredentialOption
import id.walt.wallet2.handlers.PresentationCredentialRequirement
import id.walt.wallet2.handlers.PresentationCredentialSelection
import id.walt.wallet2.handlers.PresentationDisclosureSelection
import id.walt.wallet2.handlers.PreviewPresentationRequest
import id.walt.wallet2.handlers.PreviewPresentationResult
import id.walt.wallet2.handlers.RejectPresentationRequest
import id.walt.wallet2.handlers.ReceiveCredentialRequest
import id.walt.wallet2.handlers.ResolveOfferRequest
import id.walt.wallet2.handlers.SubmitPresentationRequest
import id.walt.wallet2.handlers.WalletIssuanceHandler
import id.walt.wallet2.handlers.WalletPresentationHandler
import id.waltid.openid4vci.wallet.attestation.ClientAttestationAssembler
import id.waltid.openid4vci.wallet.attestation.HttpWalletAttestationProvider
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2
import id.waltid.openid4vp.wallet.WalletPresentFunctionality2.WalletPresentResult
import id.waltid.openid4vp.wallet.response.ResponseEncryption
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
 * Result of answering an OpenID4VP presentation request.
 *
 * Each subtype represents the next action required from the host app. This keeps
 * mutually exclusive response artifacts out of the same result instance.
 */
public sealed interface MobileWalletPresentationResult {
    /** The protocol response still requires a host-app delivery action. */
    public sealed interface Prepared : MobileWalletPresentationResult {
        /** The host app must open [url] to deliver the protocol response. */
        public data class OpenUrl(
            /** URL the host app must open to deliver the protocol response. */
            public val url: String,
        ) : Prepared

        /** The host app must render [html] so its self-submitting form can deliver the protocol response. */
        public data class SubmitForm(
            /** HTML document the host app must render to deliver the protocol response. */
            public val html: String,
        ) : Prepared
    }

    /** The protocol response was transmitted and the verifier returned a JSON response. */
    public sealed interface Transmitted : MobileWalletPresentationResult {
        /** Raw verifier response body encoded as JSON. */
        public val verifierResponseJson: String

        /** The verifier accepted the protocol response. */
        public data class Succeeded(
            override val verifierResponseJson: String,
            /** Optional post-response redirect for the host app to open. */
            public val redirectUrl: String? = null,
        ) : Transmitted

        /** The verifier rejected or could not process the protocol response. */
        public data class Failed(
            override val verifierResponseJson: String,
        ) : Transmitted
    }
}

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
    private val preferredLocales: List<String> = emptyList(),
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
        ).toMobileOfferResolution(preferredLocales)

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
     * @return The prepared host action or transmitted verifier outcome.
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

        return result.toMobilePresentationResult()
    }

    /**
     * Resolves and previews an OpenID4VP presentation request without submitting credentials.
     * Protocol failures with a validated response destination are returned as [MobileWalletPresentationPreviewResult.Invalid].
     * Resolution or validation failures that cannot be answered safely remain local exceptions.
     */
    public suspend fun previewPresentation(requestUrl: String): MobileWalletPresentationPreviewResult {
        val result = WalletPresentationHandler.previewPresentation(
            wallet = wallet,
            request = PreviewPresentationRequest(
                requestUrl = Url(requestUrl.trim()),
            ),
            transactionDataTypeRegistry = transactionDataProfiles.toTransactionDataTypeRegistry(),
            onEvent = ::emitSessionEvent,
        )

        return when (result) {
            is PreviewPresentationResult.Invalid ->
                MobileWalletPresentationPreviewResult.Invalid(
                    request = result.authorizationRequest.toMobileRequestInfo(preferredLocales),
                    errorCode = result.error.code.toMobileErrorCode(),
                    message = result.error.message,
                )

            is PreviewPresentationResult.Ready -> {
                val profilesByType = transactionDataProfiles.associateBy { it.type }
                val transactionData = result.transactionData.map { item ->
                    val profile = profilesByType[item.type]
                    MobileWalletTransactionDataItem(
                        type = item.type,
                        displayName = profile?.displayName ?: item.type,
                        credentialQueryIds = item.credentialQueryIds,
                        supportedFields = profile?.fields.orEmpty(),
                        rawJson = item.rawJson.encodeJsonObject(),
                        detailsJson = item.details.encodeJsonObject(),
                    )
                }
                MobileWalletPresentationPreviewResult.Ready(
                    MobileWalletPresentationPreview(
                        request = result.authorizationRequest.toMobileRequestInfo(
                            preferredLocales = preferredLocales,
                            responseEncryption = result.responseEncryption,
                            transactionData = transactionData,
                        ),
                        credentialOptions = result.credentialOptions.map { it.toMobileCredentialOption() },
                        credentialRequirements = result.credentialRequirements.map { it.toMobileCredentialRequirement() },
                    )
                )
            }
        }
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
     * Sends an OpenID4VP error response for a request resolved by [previewPresentation].
     * When [errorCode] is omitted, the wallet uses the detected error for an invalid preview or
     * [MobileWalletPresentationErrorCode.accessDenied] for a valid request declined by the user.
     */
    public suspend fun rejectPresentation(
        requestUrl: String,
        errorCode: MobileWalletPresentationErrorCode? = null,
        errorDescription: String? = null,
    ): MobileWalletPresentationResult =
        WalletPresentationHandler.rejectPresentation(
            wallet = wallet,
            request = RejectPresentationRequest(
                requestUrl = Url(requestUrl.trim()),
                errorCode = errorCode?.errorCode,
                errorDescription = errorDescription,
            ),
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

    private fun JsonObject.encodeJsonObject(): String =
        Json.encodeToString(JsonObject.serializer(), this)

    private fun JsonElement.displayValue(): String? =
        when (this) {
            is JsonPrimitive -> contentOrNull
            else -> toString()
        }
}

internal fun WalletPresentResult.toMobilePresentationResult(): MobileWalletPresentationResult =
    verifierResponse?.let { Json.encodeToString(JsonElement.serializer(), it) }.let { responseJson ->
        val responseUrl = getUrl
        val formHtml = formPostHtml
        when {
            responseUrl != null -> {
                require(
                    transmissionSuccess == null &&
                        formHtml == null &&
                        responseJson == null &&
                        redirectTo == null
                ) {
                    "Prepared URL result contains incompatible protocol fields"
                }
                MobileWalletPresentationResult.Prepared.OpenUrl(responseUrl)
            }

            formHtml != null -> {
                require(transmissionSuccess == null && responseJson == null && redirectTo == null) {
                    "Prepared form result contains incompatible protocol fields"
                }
                MobileWalletPresentationResult.Prepared.SubmitForm(formHtml)
            }

            transmissionSuccess == true -> MobileWalletPresentationResult.Transmitted.Succeeded(
                verifierResponseJson = requireNotNull(responseJson) {
                    "Transmitted presentation result is missing the verifier response"
                },
                redirectUrl = redirectTo,
            )

            transmissionSuccess == false -> MobileWalletPresentationResult.Transmitted.Failed(
                verifierResponseJson = requireNotNull(responseJson) {
                    "Failed presentation transmission is missing the verifier response"
                },
            )

            else -> error("Presentation result has no protocol outcome")
        }
    }

private fun AuthorizationRequest.toMobileRequestInfo(
    preferredLocales: List<String>,
    responseEncryption: ResponseEncryption.Metadata? = null,
    transactionData: List<MobileWalletTransactionDataItem> = emptyList(),
): MobileWalletPresentationRequestInfo = MobileWalletPresentationRequestInfo(
    clientId = clientId,
    verifierMetadata = clientMetadata?.toMobileVerifierMetadata(preferredLocales),
    responseUri = responseUri,
    state = state,
    nonce = nonce,
    responseEncryption = responseEncryption.toMobileResponseEncryption(),
    transactionData = transactionData,
)

private fun WalletPresentFunctionality2.OID4VPErrorCode.toMobileErrorCode(): MobileWalletPresentationErrorCode = when (this) {
    WalletPresentFunctionality2.OID4VPErrorCode.ACCESS_DENIED -> MobileWalletPresentationErrorCode.accessDenied
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST -> MobileWalletPresentationErrorCode.invalidRequest
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_CLIENT -> MobileWalletPresentationErrorCode.invalidClient
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_SCOPE -> MobileWalletPresentationErrorCode.invalidScope
    WalletPresentFunctionality2.OID4VPErrorCode.UNAUTHORIZED_CLIENT -> MobileWalletPresentationErrorCode.unauthorizedClient
    WalletPresentFunctionality2.OID4VPErrorCode.UNSUPPORTED_RESPONSE_TYPE -> MobileWalletPresentationErrorCode.unsupportedResponseType
    WalletPresentFunctionality2.OID4VPErrorCode.SERVER_ERROR -> MobileWalletPresentationErrorCode.serverError
    WalletPresentFunctionality2.OID4VPErrorCode.TEMPORARILY_UNAVAILABLE -> MobileWalletPresentationErrorCode.temporarilyUnavailable
    WalletPresentFunctionality2.OID4VPErrorCode.VP_FORMATS_NOT_SUPPORTED -> MobileWalletPresentationErrorCode.vpFormatsNotSupported
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_REQUEST_URI_METHOD -> MobileWalletPresentationErrorCode.invalidRequestUriMethod
    WalletPresentFunctionality2.OID4VPErrorCode.INVALID_TRANSACTION_DATA -> MobileWalletPresentationErrorCode.invalidTransactionData
    WalletPresentFunctionality2.OID4VPErrorCode.WALLET_UNAVAILABLE -> MobileWalletPresentationErrorCode.walletUnavailable
}
