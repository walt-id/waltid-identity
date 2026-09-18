package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.MobileWallet
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.wallet2.mobile.identity.*
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
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
import id.walt.crypto2.keys.KeyOrigin
import id.walt.crypto2.keys.KeySecurityLevel
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.crypto2.keys.KeyUseAuthorizationUnsupportedReason

internal class MobileDemoWallet(
    private val mobileWallet: MobileWallet,
    private val warning: String? = null,
    private val isIos: Boolean = false,
) : DemoWallet {
    private sealed interface IdentityAction {
        data class Create(val option: SigningIdentityCreationOption) : IdentityAction
        data class Backup(val option: SigningIdentityBackupOption) : IdentityAction
        data class Delete(val candidate: SigningIdentityRecoveryCandidate) : IdentityAction
        data class Restore(val option: SigningIdentityRestorationOption) : IdentityAction
    }
    private val identityChoices = mutableMapOf<String, IdentityAction>()

    override suspend fun identityDetails(): WalletDemoIdentityDetails? {
        val identity = checkNotNull((mobileWallet.signingIdentity.state() as? SigningIdentityState.Active)?.identity) {
            "The wallet has no active signing key."
        }
        identityChoices.clear()
        val choices = mutableListOf<WalletDemoIdentityChoice>()
        for (option in mobileWallet.signingIdentity.backupOptions(identity.id)) {
            val id = Uuid.random().toString()
            identityChoices[id] = IdentityAction.Backup(option)
            val recovery = recoveryChoice(option.providerName, option.recoveryAvailability.scope)
            choices += WalletDemoIdentityChoice(id, recovery.title, recovery.detail, true)
        }
        val discovery = mobileWallet.signingIdentity.discoverRecovery()
        for (candidate in discovery.candidates.filter { it.reference.recordId == identity.id }) {
            val id = Uuid.random().toString()
            identityChoices[id] = IdentityAction.Delete(candidate)
            choices += WalletDemoIdentityChoice(id, "Delete key backup", candidate.providerName, true, destructive = true)
        }
        return WalletDemoIdentityDetails(storageChoice(identity.storage, false).title, when (identity.keyFacts.origin) {
                KeyOrigin.GENERATED -> "Generated on this device"
                KeyOrigin.IMPORTED -> "Imported"
                KeyOrigin.UNKNOWN -> "Unknown"
            },
            identity.authorization.identityDescription(isIos), when (val recovery = identity.recovery) {
                SigningIdentityRecoveryState.Disabled -> "No key backup submitted."
                is SigningIdentityRecoveryState.Submitted -> if (recovery.receipt == RecoveryReceipt.AcceptedLocally)
                    "Saved on this device. Delivery to another device is not confirmed." else "Backup confirmed by the provider."
                is SigningIdentityRecoveryState.Recovered -> "The original signing key was restored on this installation."
                is SigningIdentityRecoveryState.RemovalRequested -> "Backup deletion requested. Removal from other devices is not confirmed."
            }, choices, protection = when (identity.keyFacts.securityLevel) {
                KeySecurityLevel.SOFTWARE -> "Software"
                KeySecurityLevel.TRUSTED_ENVIRONMENT -> "Trusted execution environment (TEE)"
                KeySecurityLevel.STRONGBOX -> "StrongBox"
                KeySecurityLevel.SECURE_ENCLAVE -> "Secure Enclave"
                KeySecurityLevel.UNKNOWN -> "Unknown"
            }, providerFailures = discovery.failures.map { "${providerTitle(it.providerName)}: ${it.message}" })
    }

    override suspend fun identitySetup(): WalletDemoIdentitySetup? {
        val setupActions = mutableMapOf<String, IdentityAction>()
        val state = mobileWallet.signingIdentity.state()
        if (state is SigningIdentityState.Active) return null
        if (state is SigningIdentityState.Pending) return WalletDemoIdentitySetup.Pending(state.identityId, state.reason.explanation(), state.reason.canRetry)
        val choices = mutableListOf<WalletDemoKeySetupOption>()
        val unavailableReasons = mutableListOf<String>()
        if (state == SigningIdentityState.Absent) {
            for (intent in SigningIdentityIntent.entries) {
                val options = when (val result = mobileWallet.signingIdentity.creationOptions(intent)) {
                    is SigningIdentityCreationOptions.Available -> result
                    is SigningIdentityCreationOptions.Unavailable -> {
                        unavailableReasons += result.reasons
                        continue
                    }
                }
                for (option in listOf(options.recommended) + options.alternatives) {
                    val id = Uuid.random().toString()
                    setupActions[id] = IdentityAction.Create(option)
                    val recovery = option.recoveryProviderName?.let { provider ->
                        recoveryChoice(provider, option.recoveryAvailability?.scope)
                    } ?: WalletDemoKeyChoice("new", "Create without a key backup",
                        "No recovery backup is created. If the key is lost, credentials using it may need to be issued again.")
                    choices += WalletDemoKeySetupOption(id, recovery,
                        storageChoice(option.storage, option.recoverable), option.authorization.approvalChoice(isIos))
                }
            }
        }
        val discovery = mobileWallet.signingIdentity.discoverRecovery()
        val recoveryUnavailableReasons = discovery.failures.map { "${providerTitle(it.providerName)}: ${it.message}" }.toMutableList()
        for (candidate in discovery.candidates) {
            val options = try { mobileWallet.signingIdentity.restorationOptions(candidate) }
            catch (cause: CancellationException) { throw cause }
            catch (cause: Exception) {
                recoveryUnavailableReasons += "${providerTitle(candidate.providerName)}: Could not read this key backup. Try again."
                continue
            }
            for (option in options) {
                val id = Uuid.random().toString()
                setupActions[id] = IdentityAction.Restore(option)
                choices += WalletDemoKeySetupOption(id,
                    WalletDemoKeyChoice("restore:${candidate.reference}", "Restore from ${providerTitle(candidate.providerName)}",
                        "Restore the original signing key. Credentials are not included.\nKey ${CryptographyProvider.Default.get(SHA256).hasher().hash(option.did.encodeToByteArray()).toHexString().take(12)}", identifier = option.did),
                    storageChoice(option.storage, true), option.authorization.approvalChoice(isIos), restoring = true)
            }
        }
        identityChoices.clear()
        identityChoices.putAll(setupActions)
        return WalletDemoIdentitySetup.Choose(choices, if (state is SigningIdentityState.Unavailable)
            state.reason.explanation()
            else unavailableReasons.takeIf { choices.isEmpty() }?.distinct()?.joinToString("\n"), recoveryStorageNotice = if (isIos)
                "The Secure Enclave cannot restore a key. Recoverable keys use Keychain or the encrypted wallet database." else null,
            recoveryUnavailableReasons = recoveryUnavailableReasons)
    }

    private fun recoveryChoice(provider: String, scope: RecoveryScope?): WalletDemoKeyChoice =
        if (scope == RecoveryScope.DeviceTransfer) WalletDemoKeyChoice("backup:$provider", "Transfer during Android phone setup",
            "Recover your signing key when copying apps and data to another Android phone. Requires this phone and a supported Android transfer flow. No cloud backup; credentials are not included.")
        else WalletDemoKeyChoice("backup:$provider", "Back up with ${providerTitle(provider)}",
            "Save a backup of your signing key. Credentials are not included. Saving on this device does not confirm cloud delivery.")

    private fun storageChoice(storage: SigningIdentityKeyStorage, recoverable: Boolean): WalletDemoKeyChoice = when (storage) {
        SigningIdentityKeyStorage.HardwareBacked -> WalletDemoKeyChoice(storage.name,
            if (isIos) "Secure Enclave" else "Hardware required",
            if (isIos) "Generates and uses the key inside the Secure Enclave. This key cannot be restored on another device."
            else if (recoverable) "Imports the recoverable key into secure hardware for signing. Its recovery secret also exists outside that hardware."
            else "Generates and uses the key inside secure hardware. Setup fails if hardware protection is unavailable.")
        SigningIdentityKeyStorage.NativeStorage -> WalletDemoKeyChoice(storage.name,
            if (isIos) "Keychain" else "Android Keystore",
            if (isIos) "Stores the key in the iOS Keychain. Signing runs outside the Secure Enclave."
            else "The operating system manages the key. Hardware protection is not required.")
        SigningIdentityKeyStorage.EncryptedDatabase -> WalletDemoKeyChoice(storage.name, "Encrypted wallet database",
            "Stores the key in the encrypted wallet database and signs in software. No hardware protection or system signing prompt.")
    }

    override suspend fun chooseIdentity(choiceId: String) {
        val result = when (val action = identityChoices.remove(choiceId) ?: error("Refresh identity choices before trying again")) {
            is IdentityAction.Backup -> mobileWallet.signingIdentity.backup(action.option)
            is IdentityAction.Delete -> { mobileWallet.signingIdentity.deleteRecovery(action.candidate); return }
            is IdentityAction.Create -> mobileWallet.signingIdentity.create(action.option)
            is IdentityAction.Restore -> mobileWallet.signingIdentity.restore(action.option)
        }
        checkIdentityResult(result)
    }

    override suspend fun cancelIdentity(identityId: String) = mobileWallet.signingIdentity.cancelPending(identityId)
    override suspend fun resumeSigningIdentity(identityId: String) { checkIdentityResult(mobileWallet.signingIdentity.resumePending(identityId)) }

    private fun checkIdentityResult(result: SigningIdentityOperationResult) {
        if (result is SigningIdentityOperationResult.Failed) throw WalletDemoKeyOperationException(result.reason.explanation())
    }

    override suspend fun bootstrap(signingProtection: WalletDemoSigningProtection): WalletDemoBootstrapResult =
        (mobileWallet.signingIdentity.state() as? SigningIdentityState.Active)?.identity.let { selected ->
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

private fun KeyUseAuthorizationPolicy.approvalChoice(isIos: Boolean): WalletDemoKeyChoice = WalletDemoKeyChoice(
    toString(), when (this) {
        KeyUseAuthorizationPolicy.None -> "No signing prompt"
        KeyUseAuthorizationPolicy.BiometricCurrentSet -> "Biometrics · current enrollment"
        KeyUseAuthorizationPolicy.BiometricAny -> "Biometrics · allow new enrollment"
        is KeyUseAuthorizationPolicy.BiometricTimedReuse -> "Biometrics · ${timeoutSeconds}s reuse"
        is KeyUseAuthorizationPolicy.DeviceCredential -> if (isIos) "Device passcode" else "Device screen lock"
        is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> if (isIos) "Biometrics or device passcode" else "Biometrics or screen lock"
    }, identityDescription(isIos),
)

private fun KeyUseAuthorizationPolicy.identityDescription(isIos: Boolean): String = when (this) {
    KeyUseAuthorizationPolicy.None -> "Signing does not ask for system approval. The wallet PIN and app-unlock biometrics are separate."
    KeyUseAuthorizationPolicy.BiometricCurrentSet -> "Approve each signature with biometrics. Changing enrolled biometrics makes this key unusable."
    KeyUseAuthorizationPolicy.BiometricAny -> "Approve each use with biometrics, including newly enrolled biometrics. Security changes can still make the key unavailable."
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> "Approve signing with biometrics. The system can reuse approval for $timeoutSeconds seconds."
    is KeyUseAuthorizationPolicy.DeviceCredential -> (if (isIos) "Approve signing with the device passcode. " else "Approve signing with the device PIN, pattern, or password. ") + approvalReuse(timeoutSeconds)
    is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> (if (isIos) "Approve signing with biometrics or the device passcode. " else "Approve signing with biometrics or the device PIN, pattern, or password. ") + approvalReuse(timeoutSeconds)
}

private fun approvalReuse(seconds: Int): String = if (seconds == 0) "Approval is required for each use." else "Approval can be reused for $seconds seconds."

private fun providerTitle(name: String): String = if (name == "iCloud Keychain recovery") "iCloud Keychain" else name

internal val SigningIdentityFailure.canRetry: Boolean
    get() = this !in setOf(SigningIdentityFailure.ProviderConflict, SigningIdentityFailure.ProviderRejected,
        SigningIdentityFailure.InvalidRecoveryRecord, SigningIdentityFailure.UnsupportedPolicy,
        SigningIdentityFailure.ExistingIdentity, SigningIdentityFailure.KeyUnavailable)

internal fun SigningIdentityFailure.explanation(): String = when (this) {
    SigningIdentityFailure.UnsupportedPolicy -> "This device cannot use the selected protection. Choose another supported option."
    SigningIdentityFailure.StaleOption -> "The available options have changed. Check the options again before continuing."
    SigningIdentityFailure.KeyUnavailable -> "The signing key is unavailable. Restore its backup to use this key again."
    SigningIdentityFailure.InvalidRecoveryRecord -> "This backup could not be validated. Choose another backup; no replacement key was created."
    SigningIdentityFailure.AuthorizationNotCompleted -> "Signing approval was not completed. Try again and approve the system prompt."
    SigningIdentityFailure.NativeOperationFailed -> "The device could not complete the key operation. Try again; protection has not been reduced."
    SigningIdentityFailure.ProviderUnavailable -> "The backup provider is unavailable. Check its availability, then try again."
    SigningIdentityFailure.ExistingIdentity -> "A signing key is already active or being set up. Complete or cancel that setup first."
    SigningIdentityFailure.ProviderInteractionRequired -> "Unlock or sign in to your backup provider, then retry setup."
    SigningIdentityFailure.ProviderRejected -> "The backup provider rejected this request. Check its access and storage settings, or choose another backup option."
    SigningIdentityFailure.ProviderConflict -> "A different backup already uses this identifier. Choose another backup destination. The existing backup has not been overwritten."
    SigningIdentityFailure.ProviderConfirmationPending -> "The provider has not met the required backup confirmation. Check again later to continue setup with the same key."
}
