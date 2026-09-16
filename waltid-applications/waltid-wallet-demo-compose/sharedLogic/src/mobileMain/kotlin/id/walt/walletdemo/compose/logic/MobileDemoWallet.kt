package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWallet
import id.walt.wallet2.mobile.identity.*
import kotlin.uuid.Uuid
import id.walt.wallet2.mobile.MobileWalletMetadataDisplay
import id.walt.wallet2.mobile.MobileWalletPresentationCredentialSelection
import id.walt.wallet2.mobile.MobileWalletPresentationDisclosureSelection
import id.walt.wallet2.mobile.MobileWalletPresentationPreview
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewHandle
import id.walt.wallet2.mobile.MobileWalletPresentationPreviewResult
import id.walt.wallet2.mobile.MobileWalletPresentationRequestInfo
import id.walt.wallet2.mobile.MobileWalletPresentationResult
import id.walt.wallet2.mobile.MobileWalletResponseEncryption
import id.walt.wallet2.mobile.MobileWalletTransactionDataItem
import id.walt.wallet2.mobile.MobileWalletVerifierMetadata
import id.walt.wallet2.mobile.MobileWalletCredentialOffer
import id.walt.wallet2.mobile.MobileWalletIssuanceRequest
import id.walt.wallet2.mobile.WalletAttestationConfig
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationPolicy
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationSupport
import id.walt.wallet2.persistence.keys.KeyUseAuthorizationUnsupportedReason

internal class MobileDemoWallet(
    private val mobileWallet: MobileWallet,
    private val warning: String? = null,
    private val isIos: Boolean = false,
) : DemoWallet {
    private sealed interface IdentityAction {
        data class Create(val option: IdentityCreationOption) : IdentityAction
        data class Backup(val option: IdentityBackupOption) : IdentityAction
        data class Delete(val candidate: RecoveryCandidate) : IdentityAction
        data class Restore(val option: IdentityRestorationOption) : IdentityAction
    }
    private val identityChoices = mutableMapOf<String, IdentityAction>()

    override suspend fun identityDetails(): WalletDemoIdentityDetails? {
        val identity = (mobileWallet.identities.state() as? WalletIdentityState.Active)?.identity ?: return null
        identityChoices.clear()
        val choices = mutableListOf<WalletDemoIdentityChoice>()
        for (option in mobileWallet.identities.backupOptions(identity.id)) {
            val id = Uuid.random().toString()
            identityChoices[id] = IdentityAction.Backup(option)
            choices += WalletDemoIdentityChoice(id, "Back up with ${option.providerName}",
                "Saves a recovery secret for this signing key and DID. Credentials are not included.", true)
        }
        for (candidate in mobileWallet.identities.recoveryCandidates().filter { it.reference.recordId == identity.id }) {
            val id = Uuid.random().toString()
            identityChoices[id] = IdentityAction.Delete(candidate)
            choices += WalletDemoIdentityChoice(id, "Delete recovery record", candidate.providerName, true, destructive = true)
        }
        return WalletDemoIdentityDetails(storageChoice(identity.storage, false).title, identity.keyFacts.origin.name,
            identity.authorization.identityDescription(), when (val recovery = identity.recovery) {
                IdentityRecoveryState.Disabled -> "No recovery backup submitted."
                is IdentityRecoveryState.Submitted -> if (recovery.receipt == RecoveryReceipt.AcceptedLocally)
                    "Recovery record accepted locally; cloud delivery unknown." else "Recovery submission confirmed by provider."
                is IdentityRecoveryState.Recovered -> "The original signing key and DID were restored on this installation."
                is IdentityRecoveryState.RemovalRequested -> "Deletion requested; removal from other devices is not verified."
            }, choices)
    }

    override suspend fun identitySetup(): WalletDemoIdentitySetup? {
        identityChoices.clear()
        val state = mobileWallet.identities.state()
        if (state is WalletIdentityState.Active) return null
        if (state is WalletIdentityState.Pending) return WalletDemoIdentitySetup.Pending(state.identityId)
        val choices = mutableListOf<WalletDemoKeySetupOption>()
        if (state == WalletIdentityState.Absent) {
            for (intent in IdentityIntent.entries) {
                val options = mobileWallet.identities.creationOptions(intent) as? IdentityOptions.Available ?: continue
                for (option in listOf(options.recommended) + options.alternatives) {
                    val id = Uuid.random().toString()
                    identityChoices[id] = IdentityAction.Create(option)
                    val recovery = option.recoveryProviderName?.let { provider ->
                        WalletDemoKeyChoice("backup:$provider", "Back up with $provider",
                            "Save a recovery secret for the same signing key and DID. Credentials are not included. " +
                                "Cloud delivery depends on the provider and is not confirmed by a local save.")
                    } ?: WalletDemoKeyChoice("new", "No recovery backup",
                        "Keep this key on this device. If it is lost, you may need to have your credentials issued again.")
                    choices += WalletDemoKeySetupOption(id, recovery,
                        storageChoice(option.storage, option.recoverable), option.authorization.approvalChoice())
                }
            }
        }
        for (candidate in mobileWallet.identities.recoveryCandidates()) {
            for (option in mobileWallet.identities.restorationOptions(candidate)) {
                val id = Uuid.random().toString()
                identityChoices[id] = IdentityAction.Restore(option)
                choices += WalletDemoKeySetupOption(id,
                    WalletDemoKeyChoice("restore:${candidate.reference}", "Restore from ${candidate.providerName}",
                        "Restore the original signing key and DID. Credentials are not included.\n${option.did}"),
                    storageChoice(option.storage, true), option.authorization.approvalChoice(), restoring = true)
            }
        }
        return WalletDemoIdentitySetup.Choose(choices, if (state is WalletIdentityState.Unavailable)
            "The existing signing key is unavailable. Restore its original key to use credentials bound to it."
            else null, recoveryStorageNotice = if (isIos)
                "The Secure Enclave cannot restore a key. Recoverable keys use Keychain or the encrypted wallet database." else null)
    }

    private fun storageChoice(storage: IdentityKeyStorage, recoverable: Boolean): WalletDemoKeyChoice = when (storage) {
        IdentityKeyStorage.Hardware -> WalletDemoKeyChoice(storage.name,
            if (isIos) "Secure Enclave" else "Hardware required",
            if (isIos) "Generates and uses the key inside the Secure Enclave. This key cannot be restored on another device."
            else if (recoverable) "Imports the recoverable key into secure hardware for signing. Its recovery secret also exists outside that hardware."
            else "Generates and uses the key inside secure hardware. Setup fails if hardware protection is unavailable.")
        IdentityKeyStorage.NativeStorage -> WalletDemoKeyChoice(storage.name,
            if (isIos) "Keychain" else "Android Keystore",
            if (isIos) "Stores the key in the iOS Keychain. Signing runs outside the Secure Enclave."
            else "The operating system manages the key. Hardware protection is not required.")
        IdentityKeyStorage.EncryptedDatabase -> WalletDemoKeyChoice(storage.name, "Encrypted wallet database",
            "Stores the key in the encrypted wallet database and signs in software. No hardware protection or system signing prompt.")
    }

    override suspend fun chooseIdentity(choiceId: String) {
        val result = when (val action = identityChoices.remove(choiceId) ?: error("Refresh identity choices before trying again")) {
            is IdentityAction.Backup -> mobileWallet.identities.backup(action.option)
            is IdentityAction.Delete -> { mobileWallet.identities.deleteRecovery(action.candidate); return }
            is IdentityAction.Create -> mobileWallet.identities.create(action.option)
            is IdentityAction.Restore -> mobileWallet.identities.restore(action.option)
        }
        checkIdentityResult(result)
    }

    override suspend fun cancelIdentity(identityId: String) = mobileWallet.identities.cancelPending(identityId)
    override suspend fun resumeIdentity(identityId: String) { checkIdentityResult(mobileWallet.identities.resumePending(identityId)) }

    private fun checkIdentityResult(result: IdentityOperationResult) {
        if (result is IdentityOperationResult.Failed) error("Identity operation failed: ${result.reason}")
    }

    override suspend fun bootstrap(signingProtection: WalletDemoSigningProtection): WalletDemoBootstrapResult =
        (mobileWallet.identities.state() as? WalletIdentityState.Active)?.identity.let { selected ->
            val result = requireNotNull(selected) { "Select a signing identity before opening the wallet" }
            // Registration projects credential data; keep it outside the SDK identity lifecycle.
            mobileWallet.refreshDigitalCredentialRegistration()
            WalletDemoBootstrapResult(
                keyId = result.keyId,
                did = result.did,
                publicJwk = result.publicJwk,
                signingProtection = result.authorization.toDemoSigningProtection(),
                warning = warning,
            )
        }

    override suspend fun signingProtectionAvailability(
        signingProtection: WalletDemoSigningProtection,
    ): WalletDemoSigningProtectionAvailability = when (
        val support = mobileWallet.keyUseAuthorizationPreflight(
            keyUseAuthorizationPolicy = signingProtection.toKeyUseAuthorizationPolicy(),
        )
    ) {
        is KeyUseAuthorizationSupport.Supported -> WalletDemoSigningProtectionAvailability.Available
        is KeyUseAuthorizationSupport.Unsupported -> when (support.reason) {
            KeyUseAuthorizationUnsupportedReason.BiometricNotEnrolled ->
                WalletDemoSigningProtectionAvailability.BiometricNotEnrolled
            KeyUseAuthorizationUnsupportedReason.DeviceCredentialNotSet ->
                WalletDemoSigningProtectionAvailability.DeviceCredentialNotSet
            KeyUseAuthorizationUnsupportedReason.BiometricUnavailable ->
                WalletDemoSigningProtectionAvailability.BiometricUnavailable
            KeyUseAuthorizationUnsupportedReason.UnsupportedCombination ->
                WalletDemoSigningProtectionAvailability.Unsupported
        }
    }

    override suspend fun listCredentials(): List<WalletDemoCredential> =
        mobileWallet.credentials().map { credential ->
            WalletDemoCredential(
                id = credential.id,
                format = credential.format,
                issuer = credential.issuer,
                subject = credential.subject,
                label = credential.label ?: credential.format,
                addedAt = credential.addedAt,
                credentialDataJson = credential.credentialDataJson,
                metadataJson = credential.metadataJson,
            )
        }

    override suspend fun startIssuance(
        offerUrl: String,
        redirectUri: String,
        did: String?,
    ): WalletDemoIssuanceSession = mobileWallet.startIssuance(
        MobileWalletIssuanceRequest(
            offer = MobileWalletCredentialOffer.Uri(offerUrl),
            redirectUri = redirectUri,
            did = did,
        )
    ).toDemoIssuanceSession()

    override suspend fun beginAuthorizationIssuance(sessionId: String): WalletDemoIssuanceAuthorization =
        mobileWallet.beginAuthorizationIssuance(sessionId).let {
            WalletDemoIssuanceAuthorization(url = it.url)
        }

    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
    ): WalletDemoIssuanceOutcome =
        mobileWallet.continuePreAuthorizedIssuance(sessionId, transactionCode).toDemoIssuanceOutcome()

    override suspend fun continueAuthorizationIssuance(
        sessionId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome =
        mobileWallet.continueAuthorizationIssuance(sessionId, callbackUri).toDemoIssuanceOutcome()

    override suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome =
        mobileWallet.cancelIssuance(sessionId).toDemoIssuanceOutcome()

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletDemoIssuanceOutcome =
        mobileWallet.resumeDeferredIssuance(deferredCredentialId).toDemoIssuanceOutcome()

    override suspend fun present(requestUrl: String, did: String?): WalletDemoOperationResult =
        mobileWallet.present(requestUrl = requestUrl, did = did).toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationSent,
            failureMessage = WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation,
        )

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult =
        when (val result = mobileWallet.previewPresentation(requestUrl)) {
            is MobileWalletPresentationPreviewResult.Ready ->
                WalletDemoPresentationPreviewResult.Ready(result.preview.toDemoPreview())

            is MobileWalletPresentationPreviewResult.Invalid ->
                WalletDemoPresentationPreviewResult.Invalid(
                WalletDemoPresentationError(
                        previewHandle = WalletDemoPresentationPreviewHandle(result.previewHandle.value),
                        verifierMetadata = result.request.verifierMetadata?.toDemoMetadata(),
                        clientId = result.request.clientId,
                        responseUri = result.request.responseUri,
                        state = result.request.state,
                        nonce = result.request.nonce,
                        responseEncryption = result.request.responseEncryption.toDemoResponseEncryption(),
                        transactionData = emptyList(),
                        errorCode = result.errorCode.errorCode,
                        message = result.message,
                    )
                )
        }

    override suspend fun submitPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoOperationResult =
        mobileWallet.submitPresentation(
            previewHandle = MobileWalletPresentationPreviewHandle(previewHandle.value),
            selectedCredentialOptions = selectedCredentialOptions.map {
                MobileWalletPresentationCredentialSelection(
                    queryId = it.queryId,
                    credentialId = it.credentialId,
                )
            },
            selectedDisclosureOptions = selectedDisclosureOptions.map {
                MobileWalletPresentationDisclosureSelection(
                    queryId = it.queryId,
                    credentialId = it.credentialId,
                    path = it.path,
                )
            },
            did = did,
        ).toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationSent,
            failureMessage = WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation,
        )

    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) =
        mobileWallet.discardPresentationPreview(MobileWalletPresentationPreviewHandle(previewHandle.value))

    override suspend fun rejectPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
    ): WalletDemoOperationResult =
        mobileWallet.rejectPresentation(
            previewHandle = MobileWalletPresentationPreviewHandle(previewHandle.value),
        ).toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationRejected,
            failureMessage = WalletDisplayText.RejectionFinishedWithoutVerifierConfirmation,
        )

    override suspend fun deleteCredential(credentialId: String): Boolean =
        mobileWallet.deleteCredential(credentialId)

    override suspend fun deleteWallet() {
        mobileWallet.deleteWallet()
    }
}

internal fun WalletDemoSigningProtection.toKeyUseAuthorizationPolicy(): KeyUseAuthorizationPolicy = when (this) {
    WalletDemoSigningProtection.None -> KeyUseAuthorizationPolicy.None
    WalletDemoSigningProtection.Biometric -> KeyUseAuthorizationPolicy.BiometricTimedReuse(timeoutSeconds = 10)
}

private fun KeyUseAuthorizationPolicy.toDemoSigningProtection(): WalletDemoSigningProtection = when (this) {
    KeyUseAuthorizationPolicy.None -> WalletDemoSigningProtection.None
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> {
        check(timeoutSeconds == 10) {
            "Wallet key uses an unsupported biometric signing timeout: $timeoutSeconds seconds"
        }
        WalletDemoSigningProtection.Biometric
    }
    KeyUseAuthorizationPolicy.BiometricAny,
    is KeyUseAuthorizationPolicy.DeviceCredential,
    is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential,
    KeyUseAuthorizationPolicy.BiometricCurrentSet -> error(
        "Wallet key uses an unsupported per-operation biometric signing policy",
    )
}

private fun MobileWalletPresentationResult.toDemoOperationResult(
    successMessage: String,
    failureMessage: String,
): WalletDemoOperationResult =
    when (this) {
        is MobileWalletPresentationResult.Prepared.OpenUrl -> WalletDemoOperationResult.Success(
            successMessage,
            WalletDemoPresentationContinuation.Url(url),
        )

        is MobileWalletPresentationResult.Prepared.SubmitForm -> WalletDemoOperationResult.Success(
            successMessage,
            WalletDemoPresentationContinuation.FormPostHtml(html),
        )

        is MobileWalletPresentationResult.Transmitted.Succeeded -> WalletDemoOperationResult.Success(
            successMessage,
            redirectUrl?.let(WalletDemoPresentationContinuation::Url),
        )

        is MobileWalletPresentationResult.Transmitted.Failed -> WalletDemoOperationResult.Failure(failureMessage)
    }

internal fun DemoWalletConfig.toWalletAttestationConfig(): WalletAttestationConfig? =
    attestationBaseUrl.takeIf { it.isNotBlank() }?.let {
        WalletAttestationConfig(
            baseUrl = it,
            attesterPath = attestationAttesterPath,
            bearerToken = attestationBearerToken,
            hostHeader = attestationHostHeader,
        )
    }

private fun MobileWalletPresentationPreview.toDemoPreview(): WalletDemoPresentationPreview =
    WalletDemoPresentationPreview(
        previewHandle = WalletDemoPresentationPreviewHandle(previewHandle.value),
        verifierMetadata = request.verifierMetadata?.toDemoMetadata(),
        clientId = request.clientId,
        responseUri = request.responseUri,
        state = request.state,
        nonce = request.nonce,
        responseEncryption = request.responseEncryption.toDemoResponseEncryption(),
        transactionData = request.transactionData.toDemoTransactionDataGroups(),
        credentialOptions = credentialOptions.map { option ->
            WalletDemoPresentationCredentialOption(
                queryId = option.queryId,
                credentialId = option.credentialId,
                multiple = option.multiple,
                label = resolveCardTitle(
                    format = option.format,
                    credentialDataJson = option.credentialDataJson,
                    displayName = presentationDisplayName(
                        format = option.format,
                        metadataJson = option.metadataJson,
                        storedLabel = option.label,
                    ),
                    fallback = option.format,
                ),
                issuer = option.issuer,
                subject = option.subject,
                format = option.format,
                credentialDataJson = option.credentialDataJson,
                metadataJson = option.metadataJson,
                disclosures = option.disclosures.map { disclosure ->
                    WalletDemoPresentationDisclosure(
                        label = CredentialDisplayVocabulary.disclosureLabel(disclosure.name, disclosure.path),
                        path = disclosure.path,
                        valueJson = disclosure.valueJson,
                        displayValue = disclosure.displayValue,
                        selectivelyDisclosable = disclosure.selectivelyDisclosable,
                        required = disclosure.required,
                        selectable = disclosure.selectable,
                    )
                },
            )
        },
        credentialRequirements = credentialRequirements.map { requirement ->
            WalletDemoPresentationCredentialRequirement(options = requirement.options)
        },
    )

internal fun List<MobileWalletTransactionDataItem>.toDemoTransactionDataGroups(): List<ClaimGroup> =
    CredentialDisplayNormalizer.transactionDataGroups(
        map { item ->
            WalletDemoTransactionDataItem(
                type = item.type,
                displayName = item.displayName,
                credentialQueryIds = item.credentialQueryIds,
                supportedFields = item.supportedFields,
                rawJson = item.rawJson,
                detailsJson = item.detailsJson,
            )
        }
    )

private fun MobileWalletResponseEncryption.toDemoResponseEncryption(): WalletDemoResponseEncryption =
    when (this) {
        MobileWalletResponseEncryption.NotRequired -> WalletDemoResponseEncryption.NotRequired
        is MobileWalletResponseEncryption.Required -> WalletDemoResponseEncryption.Required(
            keyManagementAlgorithm = keyManagementAlgorithm,
            contentEncryptionAlgorithm = contentEncryptionAlgorithm,
            verifierKeyId = verifierKeyId,
            verifierKeyThumbprint = verifierKeyThumbprint,
        )
    }

internal fun MobileWalletMetadataDisplay.toDemoMetadataDisplay(): WalletDemoMetadataDisplay =
    WalletDemoMetadataDisplay(
        name = name,
        logoUri = logoUri,
        logoAltText = logoAltText,
        description = description,
        backgroundColor = backgroundColor,
        backgroundImageUri = backgroundImageUri,
        textColor = textColor,
    )

internal fun MobileWalletVerifierMetadata.toDemoMetadata(): WalletDemoVerifierMetadata =
    WalletDemoVerifierMetadata(
        display = display?.toDemoMetadataDisplay(),
        clientUri = clientUri,
        policyUri = policyUri,
        termsOfServiceUri = termsOfServiceUri,
    )

private fun KeyUseAuthorizationPolicy.approvalChoice(): WalletDemoKeyChoice = WalletDemoKeyChoice(
    toString(), when (this) {
        KeyUseAuthorizationPolicy.None -> "No signing prompt"
        KeyUseAuthorizationPolicy.BiometricCurrentSet -> "Current biometrics"
        KeyUseAuthorizationPolicy.BiometricAny -> "Biometrics"
        is KeyUseAuthorizationPolicy.BiometricTimedReuse -> "Biometrics · ${timeoutSeconds}s reuse"
        is KeyUseAuthorizationPolicy.DeviceCredential -> "Device passcode"
        is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> "Biometrics or device passcode"
    }, identityDescription(),
)

private fun KeyUseAuthorizationPolicy.identityDescription(): String = when (this) {
    KeyUseAuthorizationPolicy.None -> "Signing does not ask for system approval. The wallet PIN and app-unlock biometrics are separate."
    KeyUseAuthorizationPolicy.BiometricCurrentSet -> "Approve each signature with biometrics. Changing enrolled biometrics makes this key unusable."
    KeyUseAuthorizationPolicy.BiometricAny -> "Approve each signature with biometrics. Changing enrolled biometrics keeps this key usable."
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> "Approve signing with biometrics. The system can reuse approval for $timeoutSeconds seconds."
    is KeyUseAuthorizationPolicy.DeviceCredential -> "Approve signing with the device passcode. " + approvalReuse(timeoutSeconds)
    is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> "Approve signing with biometrics or the device passcode. " + approvalReuse(timeoutSeconds)
}

private fun approvalReuse(seconds: Int): String = if (seconds == 0) "Approval is required for each use." else "Approval can be reused for $seconds seconds."
