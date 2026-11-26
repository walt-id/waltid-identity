package id.walt.walletdemo.compose.logic

import id.walt.wallet2.mobile.credentials.CredentialStatusMaintenanceEnrollment
import id.walt.wallet2.mobile.credentials.CredentialStatusMaintenancePreference
import id.walt.wallet2.mobile.issuance.DeferredIssuanceId
import id.walt.wallet2.handlers.IssuanceClientIdentification
import id.walt.wallet2.handlers.PresentationReviewChangedException
import id.walt.wallet2.mobile.issuance.IssuanceSession
import id.walt.wallet2.mobile.issuance.IssuanceAuthorizationId
import id.walt.wallet2.mobile.issuance.IssuanceSessionId
import id.walt.wallet2.mobile.Wallet
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import id.walt.wallet2.mobile.identity.*
import id.walt.wallet2.mobile.issuance.IssuanceClientRegistration
import id.walt.wallet2.mobile.presentation.PresentationSelection
import kotlin.uuid.Uuid
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import id.walt.wallet2.mobile.presentation.MetadataDisplay
import id.walt.wallet2.mobile.presentation.PresentationCredentialSelection
import id.walt.wallet2.mobile.presentation.PresentationDisclosureSelection
import id.walt.wallet2.mobile.presentation.PresentationPreview
import id.walt.wallet2.mobile.presentation.PresentationPreviewHandle
import id.walt.wallet2.mobile.presentation.PresentationPreviewResult
import id.walt.wallet2.mobile.presentation.PresentationRequest
import id.walt.wallet2.mobile.presentation.PresentationResult
import id.walt.wallet2.mobile.proximity.ProximityCapabilities
import id.walt.wallet2.mobile.proximity.ProximityConfiguration
import id.walt.wallet2.mobile.proximity.ProximitySession
import id.walt.wallet2.mobile.presentation.PresentationResponseEncryption
import id.walt.wallet2.mobile.presentation.PresentationTransactionData
import id.walt.wallet2.mobile.presentation.VerifierMetadata
import id.walt.wallet2.mobile.issuance.CredentialOfferInput
import id.walt.wallet2.mobile.issuance.IssuanceRequest
import id.walt.wallet2.mobile.issuance.WalletAttestationConfig
import id.walt.crypto2.keys.KeyOrigin
import id.walt.crypto2.keys.KeySecurityLevel
import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.KeyUseAuthorizationSupport
import id.walt.crypto2.keys.KeyUseAuthorizationUnsupportedReason
import id.walt.wallet2.status.CredentialStatusRefreshMode
import id.walt.wallet2.status.CredentialStatusReviewChangedException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class SdkDemoWallet(
    private val wallet: Wallet,
    private val warning: String? = null,
    private val isIos: Boolean = false,
    private val onRegistrationRefreshed: suspend () -> Unit = {},
    private val preferredSigningProtection: WalletDemoSigningProtection = WalletDemoSigningProtection.Biometric,
) : ProximityDemoWallet {
    override suspend fun proximityPresentationCapabilities(
        configuration: ProximityConfiguration,
    ): ProximityCapabilities =
        wallet.proximityPresentationCapabilities(configuration)

    override suspend fun startProximityPresentation(
        configuration: ProximityConfiguration,
    ): ProximitySession =
        wallet.startProximityPresentation(configuration)
    private sealed interface IdentityAction {
        data class Create(val option: SigningIdentityCreationOption) : IdentityAction
        data class Backup(val option: SigningIdentityBackupOption) : IdentityAction
        data class Delete(val candidate: SigningIdentityRecoveryCandidate) : IdentityAction
        data class Restore(val option: SigningIdentityRestorationOption) : IdentityAction
    }
    private val identityChoices = mutableMapOf<String, IdentityAction>()

    override suspend fun identityDetails(): WalletDemoIdentityDetails? {
        val identity = checkNotNull((wallet.signingIdentity.state() as? SigningIdentityState.Active)?.identity) {
            "The wallet has no active signing key."
        }
        identityChoices.clear()
        val choices = mutableListOf<WalletDemoIdentityChoice>()
        for (option in wallet.signingIdentity.backupOptions(identity.id)) {
            val id = Uuid.random().toString()
            identityChoices[id] = IdentityAction.Backup(option)
            val recovery = recoveryChoice(option.providerName, option.recoveryAvailability.scope)
            choices += WalletDemoIdentityChoice(id, if (option.recoveryAvailability.scope == RecoveryScope.DeviceTransfer) "Save key for device transfer" else recovery.title, recovery.detail, true)
        }
        val discovery = wallet.signingIdentity.discoverRecovery()
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
                is SigningIdentityRecoveryState.Recovered -> "The original signing key was restored in this wallet."
                is SigningIdentityRecoveryState.RemovalRequested -> "Backup deletion requested. Keys already restored on other devices are not deleted."
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
        val state = wallet.signingIdentity.state()
        if (state is SigningIdentityState.Active) return null
        if (state is SigningIdentityState.Pending) return WalletDemoIdentitySetup.Pending(state.identityId, state.reason.explanation(), state.reason.canRetry)
        val choices = mutableListOf<WalletDemoKeySetupOption>()
        val unavailableReasons = mutableListOf<String>()
        if (state == SigningIdentityState.Absent) {
            for (intent in SigningIdentityIntent.entries) {
                val options = when (val result = wallet.signingIdentity.creationOptions(intent)) {
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
                    } ?: WalletDemoKeyChoice("new", "Without a backup",
                        "No recovery backup is created. If the key is lost, credentials using it may need to be issued again.")
                    choices += WalletDemoKeySetupOption(id, recovery,
                        storageChoice(option.storage, option.recoverable), option.authorization.approvalChoice(isIos))
                }
            }
        }
        val discovery = wallet.signingIdentity.discoverRecovery()
        val recoveryUnavailableReasons = discovery.failures.map { "${providerTitle(it.providerName)}: ${it.message}" }.toMutableList()
        for (candidate in discovery.candidates) {
            val options = try { wallet.signingIdentity.restorationOptions(candidate) }
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
            recoveryUnavailableReasons = recoveryUnavailableReasons,
            preferredApproval = preferredSigningProtection.approvalChoice(),
            existingKeyUnavailable = state is SigningIdentityState.Unavailable)
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
            if (isIos) "The key is stored in the iOS Keychain. Signing takes place outside the Secure Enclave."
            else "The operating system manages the key. Hardware protection is not required.")
        SigningIdentityKeyStorage.EncryptedDatabase -> WalletDemoKeyChoice(storage.name, "Encrypted wallet database",
            "The key is stored in the encrypted wallet database and used for software signing. Hardware protection and system signing prompts are not available.")
    }

    override suspend fun chooseIdentity(choiceId: String) {
        val result = when (val action = identityChoices.remove(choiceId) ?: error("Refresh identity choices before trying again")) {
            is IdentityAction.Backup -> wallet.signingIdentity.backup(action.option)
            is IdentityAction.Delete -> { wallet.signingIdentity.deleteRecovery(action.candidate); return }
            is IdentityAction.Create -> wallet.signingIdentity.create(action.option)
            is IdentityAction.Restore -> wallet.signingIdentity.restore(action.option)
        }
        checkIdentityResult(result)
    }

    override suspend fun cancelIdentity(identityId: String) = wallet.signingIdentity.cancelPending(identityId)
    override suspend fun resumeSigningIdentity(identityId: String) { checkIdentityResult(wallet.signingIdentity.resumePending(identityId)) }

    private fun checkIdentityResult(result: SigningIdentityOperationResult) {
        if (result is SigningIdentityOperationResult.Failed) throw WalletDemoKeyOperationException(result.reason.explanation())
    }

    override suspend fun bootstrap(signingProtection: WalletDemoSigningProtection): WalletDemoBootstrapResult =
        (wallet.signingIdentity.state() as? SigningIdentityState.Active)?.identity.let { selected ->
            val result = requireNotNull(selected) { "Select a signing identity before opening the wallet" }
            WalletDemoBootstrapResult(
                keyId = result.keyId,
                did = result.did,
                publicJwk = result.publicJwk,
                signingProtection = result.authorization.toDemoSigningProtection(),
                warning = warning,
            )
        }

    override suspend fun refreshDigitalCredentialRegistration() {
        wallet.refreshDigitalCredentialRegistration()
        currentCoroutineContext().ensureActive()
        onRegistrationRefreshed()
    }

    override suspend fun signingProtectionAvailability(
        signingProtection: WalletDemoSigningProtection,
    ): WalletDemoSigningProtectionAvailability = when (
        val support = wallet.keyUseAuthorizationPreflight(
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

    override suspend fun reconcileBackgroundStatus(automaticChecks: Boolean, resumePaused: Boolean) =
        wallet.reconcileCredentialStatusMaintenance(automaticChecks, resumePaused)?.toDemoEnrollment()

    override suspend fun setBackgroundStatusPreference(preference: DemoBackgroundStatusPreference) =
        wallet.setCredentialStatusMaintenance(CredentialStatusMaintenancePreference.valueOf(preference.name))?.toDemoEnrollment()

    override suspend fun invalidateCredentialStatusSnapshot() = wallet.invalidateCredentialStatusSnapshot()

    override fun credentialStatusSnapshots(): Flow<WalletDemoCredentialStatuses> =
        wallet.credentialStatuses.map { it.toDemoStatuses() }

    override suspend fun credentialStatusSnapshot(): WalletDemoCredentialStatuses =
        wallet.credentialStatusSnapshot().toDemoStatuses()

    override suspend fun acknowledgeCredentialStatusNotice(credentialId: String, version: String) =
        wallet.acknowledgeCredentialStatusNotice(credentialId, version)

    override suspend fun refreshCredentialStatuses(ids: Set<String>?, force: Boolean): WalletDemoStatusRefreshSummary =
        wallet.refreshCredentialStatuses(ids?.toList(),
            if (force) CredentialStatusRefreshMode.FORCE else CredentialStatusRefreshMode.IF_DUE).toDemoSummary()

    override suspend fun listCredentials(): List<WalletDemoCredential> =
        wallet.credentials().map { it.toDemoCredential() }

    override suspend fun startIssuance(
        offerUrl: String,
        redirectUri: String,
        did: String?,
    ): WalletDemoIssuanceSession = wallet.startIssuance(
        IssuanceRequest(
            offer = CredentialOfferInput.Uri(offerUrl),
            did = did,
        )
    ).toDemoIssuanceSession()

    override suspend fun beginAuthorizationIssuance(sessionId: String, redirectUri: String, credentials: List<WalletDemoCredentialSelection>): WalletDemoIssuanceAuthorization =
        (requireNotNull(wallet.resumeIssuance(IssuanceSessionId(sessionId))) as IssuanceSession.AuthorizationCode)
            .beginAuthorization(IssuanceClientRegistration("eudiw-abca", redirectUri), credentials.toIssuanceSelections())
            .let { pending -> WalletDemoIssuanceAuthorization(pending.id.value, pending.request.url) }

    override suspend fun listDeferredIssuance(): List<WalletDemoDeferredCredential> =
        wallet.listDeferredIssuance().map { it.toDemoDeferredCredential() }


    override suspend fun continuePreAuthorizedIssuance(
        sessionId: String,
        transactionCode: String?,
        credentials: List<WalletDemoCredentialSelection>,
    ): WalletDemoIssuanceOutcome =
        (requireNotNull(wallet.resumeIssuance(IssuanceSessionId(sessionId))) as IssuanceSession.PreAuthorized)
            .let { offer ->
                offer.accept(transactionCode, credentials.toIssuanceSelections(),
                    clientId = "eudiw-abca".takeIf { offer.clientIdentification == IssuanceClientIdentification.CLIENT_ID_REQUIRED })
            }.toDemoIssuanceOutcome()

    override suspend fun completeAuthorization(
        authorizationId: String,
        callbackUri: String,
    ): WalletDemoIssuanceOutcome =
        wallet.completeAuthorization(IssuanceAuthorizationId(authorizationId), callbackUri).toDemoIssuanceOutcome()

    override suspend fun cancelIssuance(sessionId: String): WalletDemoIssuanceOutcome =
        wallet.cancelIssuance(IssuanceSessionId(sessionId)).toDemoIssuanceOutcome()

    override suspend fun resumeDeferredIssuance(deferredCredentialId: String): WalletDemoIssuanceOutcome =
        wallet.resumeDeferredIssuance(DeferredIssuanceId(deferredCredentialId)).toDemoIssuanceOutcome()

    override suspend fun previewPresentation(requestUrl: String): WalletDemoPresentationPreviewResult =
        when (val result = wallet.previewPresentation(requestUrl)) {
            is PresentationPreviewResult.Ready ->
                WalletDemoPresentationPreviewResult.Ready(result.preview.toDemoPreview())

            is PresentationPreviewResult.Invalid ->
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

    override val supportsClaimReview: Boolean get() = true

    override suspend fun reviewPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
        selectedCredentialOptions: List<WalletDemoPresentationCredentialSelection>,
        selectedDisclosureOptions: List<WalletDemoPresentationDisclosureSelection>,
        did: String?,
    ): WalletDemoPreparedClaimReview {
        val review = wallet.reviewPresentation(
            PresentationPreviewHandle(previewHandle.value),
            PresentationSelection(
                credentials = selectedCredentialOptions.map { PresentationCredentialSelection(it.queryId, it.credentialId) },
                disclosures = selectedDisclosureOptions.map { PresentationDisclosureSelection(it.queryId, it.credentialId, it.path) },
            ),
            did,
        )
        val statuses = WalletDemoCredentialStatuses(review.statuses.associate { it.reference.credentialId to it.toDemoStatus() })
        return review.claims.toDemoClaimReview().copy(retained = WalletDemoRetainedPresentation(statuses, review.payment?.toDemoPaymentConsent()) {
            try {
                wallet.submitPresentation(review).toDemoOperationResult(WalletDisplayText.PresentationSent,
                    WalletDisplayText.PresentationFinishedWithoutVerifierConfirmation)
            } catch (_: CredentialStatusReviewChangedException) {
                throw WalletDemoStatusReviewChanged()
            } catch (_: PresentationReviewChangedException) {
                throw WalletDemoStatusReviewChanged()
            }
        })
    }

    override suspend fun discardPresentationPreview(previewHandle: WalletDemoPresentationPreviewHandle) =
        wallet.discardPresentationPreview(PresentationPreviewHandle(previewHandle.value))

    override suspend fun rejectPresentation(
        previewHandle: WalletDemoPresentationPreviewHandle,
    ): WalletDemoOperationResult =
        wallet.rejectPresentation(
            previewHandle = PresentationPreviewHandle(previewHandle.value),
        ).toDemoOperationResult(
            successMessage = WalletDisplayText.PresentationRejected,
            failureMessage = WalletDisplayText.RejectionFinishedWithoutVerifierConfirmation,
        )

    override suspend fun deleteCredential(credentialId: String): Boolean =
        wallet.deleteCredential(credentialId)

    override suspend fun deleteWallet() {
        wallet.deleteLocalData()
    }

    private fun CredentialStatusMaintenanceEnrollment.toDemoEnrollment() =
        DemoBackgroundStatusEnrollment(DemoBackgroundStatusPreference.valueOf(preference.name), binding)
}

/** Additional choices are preflighted by the existing signing-identity setup on each platform. */
internal fun WalletDemoSigningProtectionMode.alternativeAuthorizations(): List<KeyUseAuthorizationPolicy> =
    WalletDemoSigningProtection.entries
        .filter { it != defaultSelection && allows(it) }
        .map { it.toKeyUseAuthorizationPolicy() }

internal fun WalletDemoSigningProtection.toKeyUseAuthorizationPolicy(): KeyUseAuthorizationPolicy = when (this) {
    WalletDemoSigningProtection.None -> KeyUseAuthorizationPolicy.None
    WalletDemoSigningProtection.Biometric -> KeyUseAuthorizationPolicy.BiometricTimedReuse(timeoutSeconds = 10)
    WalletDemoSigningProtection.BiometricPerUse -> KeyUseAuthorizationPolicy.BiometricCurrentSet
}

internal fun KeyUseAuthorizationPolicy.toDemoSigningProtection(): WalletDemoSigningProtection = when (this) {
    KeyUseAuthorizationPolicy.None -> WalletDemoSigningProtection.None
    KeyUseAuthorizationPolicy.BiometricCurrentSet -> WalletDemoSigningProtection.BiometricPerUse
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> {
        check(timeoutSeconds == 10) {
            "Wallet key uses an unsupported biometric signing timeout: $timeoutSeconds seconds"
        }
        WalletDemoSigningProtection.Biometric
    }
    KeyUseAuthorizationPolicy.BiometricAny,
    is KeyUseAuthorizationPolicy.DeviceCredential,
    is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> error(
        "Wallet key uses an unsupported per-operation biometric signing policy",
    )
}

private fun PresentationResult.toDemoOperationResult(
    successMessage: String,
    failureMessage: String,
): WalletDemoOperationResult =
    when (this) {
        is PresentationResult.Prepared.OpenUrl -> WalletDemoOperationResult.Success(
            successMessage,
            WalletDemoPresentationContinuation.Url(url),
        )

        is PresentationResult.Prepared.SubmitForm -> WalletDemoOperationResult.Success(
            successMessage,
            WalletDemoPresentationContinuation.FormPostHtml(html),
        )

        is PresentationResult.Transmitted.Succeeded -> WalletDemoOperationResult.Success(
            successMessage,
            redirectUrl?.let(WalletDemoPresentationContinuation::Url),
        )

        is PresentationResult.Transmitted.Failed -> WalletDemoOperationResult.Failure(failureMessage)
    }

internal fun DemoWalletConfig.toWalletProviderConfig(): id.walt.wallet2.mobile.issuance.WalletProviderConfig? =
    if (!walletProviderEnabled) null else id.walt.wallet2.mobile.issuance.WalletProviderConfig(
        baseUrl = walletProviderBaseUrl,
        servicePath = walletProviderServicePath,
        bearerToken = walletProviderBearerToken,
    )

internal fun DemoWalletConfig.toWalletAttestationConfig(): WalletAttestationConfig? =
    attestationBaseUrl.takeIf { it.isNotBlank() }?.let {
        WalletAttestationConfig(
            baseUrl = it,
            attesterPath = attestationAttesterPath,
            bearerToken = attestationBearerToken,
            hostHeader = attestationHostHeader,
        )
    }

private fun PresentationPreview.toDemoPreview(): WalletDemoPresentationPreview =
    WalletDemoPresentationPreview(
        previewHandle = WalletDemoPresentationPreviewHandle(previewHandle.value),
        requiresPaymentConsent = request.transactionData.any { it.type == "urn:eudi:sca:payment:1" } && credentialOptions.any { it.credential.format == "dc+sd-jwt" },
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
                credentialId = option.credential.id,
                multiple = option.multiple,
                label = resolveCardTitle(
                    format = option.credential.format,
                    credentialDataJson = option.credential.credentialDataJson,
                    displayName = presentationDisplayName(
                        format = option.credential.format,
                        metadataJson = option.credential.metadataJson,
                        storedLabel = option.credential.label,
                    ),
                    fallback = option.credential.format,
                ),
                issuer = option.credential.issuer,
                subject = option.credential.subject,
                format = option.credential.format,
                credentialDataJson = option.credential.credentialDataJson,
                metadataJson = option.credential.metadataJson,
                disclosures = option.disclosures.map { disclosure ->
                    WalletDemoPresentationDisclosure(
                        label = CredentialDisplayVocabulary.disclosureLabel(disclosure.name, disclosure.path),
                        path = disclosure.path,
                        valueJson = disclosure.valueJson,
                        displayValue = disclosure.displayValue,
                        selectivelyDisclosable = disclosure.selectivelyDisclosable,
                        required = disclosure.required,
                        selectable = disclosure.selectable,
                        requested = disclosure.requested,
                    )
                },
            )
        },
        credentialRequirements = credentialRequirements.map { requirement ->
            WalletDemoPresentationCredentialRequirement(options = requirement.options)
        },
    )

internal fun List<PresentationTransactionData>.toDemoTransactionDataGroups(): List<ClaimGroup> =
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

private fun PresentationResponseEncryption.toDemoResponseEncryption(): WalletDemoResponseEncryption =
    when (this) {
        PresentationResponseEncryption.NotRequired -> WalletDemoResponseEncryption.NotRequired
        is PresentationResponseEncryption.Required -> WalletDemoResponseEncryption.Required(
            keyManagementAlgorithm = keyManagementAlgorithm,
            contentEncryptionAlgorithm = contentEncryptionAlgorithm,
            verifierKeyId = verifierKeyId,
            verifierKeyThumbprint = verifierKeyThumbprint,
        )
    }

internal fun MetadataDisplay.toDemoMetadataDisplay(): WalletDemoMetadataDisplay =
    WalletDemoMetadataDisplay(
        name = name,
        logoUri = logoUri,
        logoAltText = logoAltText,
        description = description,
        backgroundColor = backgroundColor,
        backgroundImageUri = backgroundImageUri,
        textColor = textColor,
    )

internal fun VerifierMetadata.toDemoMetadata(): WalletDemoVerifierMetadata =
    WalletDemoVerifierMetadata(
        display = display?.toDemoMetadataDisplay(),
        clientUri = clientUri,
        policyUri = policyUri,
        termsOfServiceUri = termsOfServiceUri,
    )

private fun KeyUseAuthorizationPolicy.approvalChoice(isIos: Boolean): WalletDemoKeyChoice = when (this) {
    KeyUseAuthorizationPolicy.None -> WalletDemoSigningProtection.None.approvalChoice()
    KeyUseAuthorizationPolicy.BiometricCurrentSet -> WalletDemoSigningProtection.BiometricPerUse.approvalChoice()
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> if (timeoutSeconds == 10)
        WalletDemoSigningProtection.Biometric.approvalChoice() else WalletDemoKeyChoice(toString(), "Biometrics with timed approval", identityDescription(isIos))
    KeyUseAuthorizationPolicy.BiometricAny -> WalletDemoKeyChoice(toString(), "Current and future biometrics", identityDescription(isIos))
    is KeyUseAuthorizationPolicy.DeviceCredential -> WalletDemoKeyChoice(toString(), if (isIos) "Device passcode" else "Device screen lock", identityDescription(isIos))
    is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> WalletDemoKeyChoice(toString(), if (isIos) "Biometrics or device passcode" else "Biometrics or screen lock", identityDescription(isIos))
}

private fun KeyUseAuthorizationPolicy.identityDescription(isIos: Boolean): String = when (this) {
    KeyUseAuthorizationPolicy.None -> "Signing does not require system approval."
    KeyUseAuthorizationPolicy.BiometricCurrentSet -> "Approve each signature with biometrics. Changing enrolled biometrics makes this key unusable."
    KeyUseAuthorizationPolicy.BiometricAny -> "Approve each use with biometrics, including newly enrolled biometrics. Security changes can still make the key unavailable."
    is KeyUseAuthorizationPolicy.BiometricTimedReuse -> biometricTimedApprovalDescription(timeoutSeconds)
    is KeyUseAuthorizationPolicy.DeviceCredential -> (if (isIos) "Approve signing with the device passcode. " else "Approve signing with the device PIN, pattern, or password. ") + approvalReuse(timeoutSeconds)
    is KeyUseAuthorizationPolicy.BiometricOrDeviceCredential -> (if (isIos) "Approve signing with biometrics or the device passcode. " else "Approve signing with biometrics or the device PIN, pattern, or password. ") + approvalReuse(timeoutSeconds)
}

private fun durationSeconds(seconds: Int): String = if (seconds == 1) "1 second" else "$seconds seconds"

private fun approvalReuse(seconds: Int): String = if (seconds == 0) "Approval is required for each use." else "The system may reuse that approval for ${durationSeconds(seconds)}."

private fun providerTitle(name: String): String = if (name == "iCloud Keychain recovery") "iCloud Keychain" else name

internal val SigningIdentityFailure.canRetry: Boolean
    get() = this !in setOf(SigningIdentityFailure.ProviderConflict, SigningIdentityFailure.ProviderRejected,
        SigningIdentityFailure.InvalidRecoveryRecord, SigningIdentityFailure.UnsupportedPolicy,
        SigningIdentityFailure.ExistingIdentity, SigningIdentityFailure.KeyUnavailable)

internal fun SigningIdentityFailure.explanation(): String = when (this) {
    SigningIdentityFailure.UnsupportedPolicy -> "This device cannot use the selected protection. Choose another supported option."
    SigningIdentityFailure.StaleOption -> "The available options have changed. Check the options again before continuing."
    SigningIdentityFailure.KeyUnavailable -> "The signing key is unavailable."
    SigningIdentityFailure.InvalidRecoveryRecord -> "This backup could not be validated. Choose another backup; no replacement key was created."
    SigningIdentityFailure.AuthorizationNotCompleted -> "Signing approval was not completed. Try again and approve the system prompt."
    SigningIdentityFailure.NativeOperationFailed -> "The device could not complete the key operation. Try again; protection has not been reduced."
    SigningIdentityFailure.ProviderUnavailable -> "The backup provider is unavailable. Check its availability, then try again."
    SigningIdentityFailure.ExistingIdentity -> "A signing key is already active or being set up. Complete or cancel that setup first."
    SigningIdentityFailure.ProviderInteractionRequired -> "Unlock or sign in to your backup provider, then retry setup."
    SigningIdentityFailure.ProviderRejected -> "The backup provider rejected this request. Check its access and storage settings, or choose another backup option."
    SigningIdentityFailure.ProviderConflict -> "A different backup already uses this identifier. Choose another backup destination. The existing backup has not been overwritten."
    SigningIdentityFailure.ProviderConfirmationPending -> "The backup provider has not confirmed the backup yet. Check again later to continue setup with the same key."
}

fun id.walt.wallet2.mobile.presentation.ReviewedPresentationClaims.toDemoClaimReview() =
    WalletDemoPreparedClaimReview(credentials.map { contribution ->
        WalletDemoCredentialClaims(contribution.queryId, contribution.credentialId, contribution.format,
            contribution.claimsJson, contribution.wholeCredential, contribution.locations.map { location ->
                WalletDemoClaimLocation(location.sourcePathJson, location.receivedPathJson,
                    location.sharedBy, location.alwaysCarried, location.isBinary)
            })
    })
