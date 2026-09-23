package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.digitalCredentialsRequirements
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private enum class SettingsDestination(val title: StringResource) {
    Main(Res.string.settings_title),
    SigningKey(Res.string.settings_signing_key),
    Technical(Res.string.settings_technical),
    Nearby(Res.string.settings_nearby),
    Connection(Res.string.settings_connection),
    ReaderAuthentication(Res.string.settings_reader_authentication),
    DigitalCredentialsApi(Res.string.settings_dc_api),
}

@Composable
internal fun SettingsScreen(
    state: WalletDemoUiState,
    onShowDcApiPresentationPreviewChange: (Boolean) -> Unit,
    onProximityTransportProfileChange: ((WalletDemoProximityTransportProfile) -> Unit)?,
    onBack: () -> Unit,
    onIdentityAction: (String) -> Unit,
    onRefreshIdentityDetails: () -> Unit,
    onLock: () -> Unit,
    onResetWallet: () -> Unit,
    onRequestSigningProtectionChange: (WalletDemoSigningProtection) -> Unit,
    onConfirmSigningProtectionChange: () -> Unit,
    onCancelSigningProtectionChange: () -> Unit,
    onSignOut: (() -> Unit)? = null,
    readerTrustSettingsContent: (@Composable () -> Unit)? = null,
    readerTrustPolicySummary: String? = null,
    onProximityApprovalModeChange: ((WalletDemoProximityApprovalMode) -> Unit)? = null,
    resetWalletDescription: String? = null,
) {
    val currentState by rememberUpdatedState(state)
    val currentReaderPolicy by rememberUpdatedState(readerTrustPolicySummary)
    var deleteRecovery by remember { mutableStateOf<String?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var path by rememberSaveable(stateSaver = listSaver(
        save = { entries: List<SettingsDestination> -> entries.map { it.name } },
        restore = { entries -> entries.map(SettingsDestination::valueOf) },
    )) { mutableStateOf(listOf(SettingsDestination.Main)) }
    val back = { if (path.size > 1) path = path.dropLast(1) else onBack() }
    fun open(destination: SettingsDestination) { path = path + destination }
    SystemBackHandler(enabled = path.size == 1, onBack = back)

    Surface(Modifier.fillMaxSize().testTag(WalletUiTestTags.SettingsScreen), color = MaterialTheme.colorScheme.background) {
        NavDisplay(backStack = path, onBack = back) { destination ->
            NavEntry(destination) {
                Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(back, Modifier.testTag(WalletUiTestTags.SettingsBack)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.settings_back))
                        }
                        Text(stringResource(destination.title), style = MaterialTheme.typography.titleLarge)
                    }
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).fillMaxWidth()
                            .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp)
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        currentState.sharingSettingsError?.let { SettingsNotice(it, error = true) }
                        when (destination) {
                            SettingsDestination.Main -> {
                                SettingsSection(stringResource(Res.string.settings_wallet)) {
                                    if (currentState.pinLockEnabled) {
                                        SettingsNavigationRow(stringResource(Res.string.settings_signing_key),
                                            { open(SettingsDestination.SigningKey) }, Modifier.testTag(WalletUiTestTags.SettingsSigningKey), summary = stringResource(Res.string.settings_key_subtitle),
                                            icon = { SettingsSymbol(Res.drawable.settings_key) })
                                        SettingsDivider()
                                    }
                                    SettingsNavigationRow(stringResource(Res.string.settings_technical),
                                        { open(SettingsDestination.Technical) }, Modifier.testTag(WalletUiTestTags.SettingsTechnicalDetails), summary = stringResource(Res.string.settings_technical_subtitle),
                                        icon = { SettingsSymbol(Res.drawable.settings_code) })
                                }
                                if (currentState.pinLockEnabled) {
                                    SettingsSection(stringResource(Res.string.settings_sharing),
                                        modifier = Modifier.testTag(WalletUiTestTags.SettingsCredentialSharing)) {
                                        if (onProximityTransportProfileChange != null) {
                                            SettingsNavigationRow(stringResource(Res.string.settings_nearby),
                                                { open(SettingsDestination.Nearby) },
                                                Modifier.testTag(WalletUiTestTags.SettingsProximityPresentation),
                                                summary = stringResource(currentState.proximityTransportProfile.titleResource),
                                                icon = { SettingsSymbol(Res.drawable.settings_nearby) })
                                            SettingsDivider()
                                        }
                                        SettingsNavigationRow(stringResource(Res.string.settings_dc_api),
                                            { open(SettingsDestination.DigitalCredentialsApi) }, Modifier.testTag(WalletUiTestTags.SettingsDigitalCredentialsApi),
                                            summary = stringResource(if (currentState.showDcApiPresentationPreview) Res.string.settings_review_on else Res.string.settings_review_off),
                                            icon = { SettingsSymbol(Res.drawable.settings_id_card) })
                                    }
                                }
                                SettingsSection {
                                    if (currentState.pinLockEnabled) {
                                        SettingsActionRow(stringResource(Res.string.settings_lock), onLock,
                                            Modifier.testTag(WalletUiTestTags.SettingsLock), icon = { Icon(Icons.Default.Lock, null) })
                                        SettingsDivider()
                                    }
                                    onSignOut?.let { signOut ->
                                        SettingsActionRow(stringResource(Res.string.settings_sign_out), signOut,
                                            Modifier.testTag(WalletUiTestTags.SettingsSignOut),
                                            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) })
                                        SettingsDivider()
                                    }
                                    SettingsActionRow(stringResource(Res.string.settings_reset), { confirmReset = true },
                                        Modifier.testTag(WalletUiTestTags.SettingsReset), destructive = true,
                                        icon = { Icon(Icons.Default.Refresh, null) })
                                }
                            }
                            SettingsDestination.SigningKey -> {
                                when (val details = currentState.identityDetails) {
                                    WalletDemoIdentityDetailsState.Loading -> CircularProgressIndicator()
                                    WalletDemoIdentityDetailsState.Unsupported -> SigningProtectionSettings(currentState, currentState.session as? WalletSessionState.Ready, onRequestSigningProtectionChange)
                                    is WalletDemoIdentityDetailsState.Failed -> SettingsSection {
                                        SettingsNotice(details.message, error = true)
                                        SettingsActionRow(stringResource(Res.string.settings_try_again), onRefreshIdentityDetails)
                                    }
                                    is WalletDemoIdentityDetailsState.Available -> {
                                        val identity = details.details
                                        SettingsSection(stringResource(Res.string.settings_key_protection),
                                            footer = stringResource(Res.string.settings_change_key_notice)) {
                                            SettingsDetailRow(stringResource(Res.string.settings_storage_policy), identity.storage)
                                            SettingsDivider()
                                            SettingsDetailRow(stringResource(Res.string.settings_key_protection), identity.protection)
                                            SettingsDivider()
                                            SettingsDetailRow(stringResource(Res.string.settings_key_origin), identity.origin)
                                            SettingsDivider()
                                            SettingsDetailRow(stringResource(Res.string.settings_signing_approval), identity.authorization)
                                        }
                                        SettingsSection(stringResource(Res.string.settings_key_backup)) {
                                            SettingsDetailRow(stringResource(Res.string.settings_backup_status), identity.recovery)
                                            identity.choices.forEach { choice ->
                                                SettingsDivider()
                                                SettingsActionRow(choice.title, {
                                                    if (choice.destructive) deleteRecovery = choice.id else onIdentityAction(choice.id)
                                                }, detail = choice.detail, enabled = !currentState.identityBusy,
                                                    destructive = choice.destructive,
                                                    icon = { if (choice.destructive) Icon(Icons.Default.Delete, null) else SettingsSymbol(Res.drawable.settings_backup) })
                                            }
                                            currentState.identityProgress?.let { SettingsNotice(it); LinearProgressIndicator(Modifier.fillMaxWidth()) }
                                            currentState.identityError?.let { SettingsNotice(it, error = true) }
                                        }
                                        if (identity.providerFailures.isNotEmpty()) ProviderAvailability(identity.providerFailures, !currentState.identityBusy, onRefreshIdentityDetails)
                                    }
                                }
                            }
                            SettingsDestination.Technical -> {
                                SettingsCopyRow(stringResource(Res.string.settings_did), (currentState.session as? WalletSessionState.Ready)?.did,
                                    WalletUiTestTags.SettingsDid, WalletUiTestTags.SettingsDidCopy, stringResource(Res.string.settings_copy_did), stringResource(Res.string.settings_did_copied))
                                SettingsCopyRow(stringResource(Res.string.settings_key_id), (currentState.session as? WalletSessionState.Ready)?.keyId,
                                    WalletUiTestTags.SettingsKeyId, WalletUiTestTags.SettingsKeyIdCopy, stringResource(Res.string.settings_copy_key_id), stringResource(Res.string.settings_key_id_copied))
                                SettingsCopyRow(stringResource(Res.string.settings_public_key), (currentState.session as? WalletSessionState.Ready)?.publicJwk,
                                    WalletUiTestTags.SettingsPublicJwk, WalletUiTestTags.SettingsPublicJwkCopy,
                                    stringResource(Res.string.settings_copy_public_key), stringResource(Res.string.settings_public_key_copied),
                                    disclosureLabels = stringResource(Res.string.settings_show_public_key) to stringResource(Res.string.settings_hide_public_key), formatJson = true)
                            }
                            SettingsDestination.Nearby -> {
                                onProximityApprovalModeChange?.let { onChange ->
                                    SettingsSection(stringResource(Res.string.settings_approval)) {
                                        ProximityApprovalModeChoice(currentState.proximityApprovalMode, onChange)
                                    }
                                }
                                SettingsSection {
                                    SettingsNavigationRow(stringResource(Res.string.settings_connection),
                                        { open(SettingsDestination.Connection) }, Modifier.testTag(WalletUiTestTags.SettingsConnectionMethod), summary = stringResource(currentState.proximityTransportProfile.titleResource),
                                        icon = { SettingsSymbol(Res.drawable.settings_connection) })
                                    if (readerTrustSettingsContent != null) {
                                        SettingsDivider()
                                        SettingsNavigationRow(stringResource(Res.string.settings_reader_authentication),
                                            { open(SettingsDestination.ReaderAuthentication) },
                                            Modifier.testTag(WalletUiTestTags.SettingsReaderAuthentication),
                                            summary = currentReaderPolicy ?: stringResource(Res.string.settings_reader_subtitle),
                                            icon = { SettingsSymbol(Res.drawable.settings_shield) })
                                    }
                                }
                            }
                            SettingsDestination.Connection -> Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                val profiles = WalletDemoProximityTransportProfile.entries
                                for (group in listOf(profiles.take(3), profiles.drop(3))) {
                                    SettingsSection(title = if (group == profiles.take(3)) null else stringResource(Res.string.settings_provisional)) {
                                        group.forEachIndexed { index, profile ->
                                            if (index > 0) SettingsDivider()
                                            SettingsChoiceRow(stringResource(profile.titleResource), stringResource(profile.descriptionResource),
                                                profile == currentState.proximityTransportProfile, { onProximityTransportProfileChange?.invoke(profile) },
                                                Modifier.testTag(profile.settingsTag))
                                        }
                                    }
                                }
                                SettingsNotice(stringResource(Res.string.settings_connection_footer))
                            }
                            SettingsDestination.ReaderAuthentication -> readerTrustSettingsContent?.invoke()
                            SettingsDestination.DigitalCredentialsApi -> {
                                SettingsSection(footer = stringResource(Res.string.settings_review_description)) {
                                SettingsToggleRow(stringResource(Res.string.settings_show_review), currentState.showDcApiPresentationPreview,
                                    onShowDcApiPresentationPreviewChange, Modifier.testTag(WalletUiTestTags.SettingsShowDcApiPreview))
                                }
                                SettingsNotice(stringResource(digitalCredentialsRequirements))
                            }
                        }
                    }
                }
            }
        }
    }
    deleteRecovery?.let { id ->
        AlertDialog(onDismissRequest = { deleteRecovery = null }, title = { Text(stringResource(Res.string.settings_delete_backup_question)) },
            text = { Text(stringResource(Res.string.settings_delete_backup_notice,
                (currentState.identityDetails as? WalletDemoIdentityDetailsState.Available)?.details?.choices?.find { it.id == id }?.detail ?: "the backup provider")) },
            confirmButton = { TextButton(onClick = { deleteRecovery = null; onIdentityAction(id) }) { Text(stringResource(Res.string.settings_delete_backup)) } },
            dismissButton = { TextButton(onClick = { deleteRecovery = null }) { Text(stringResource(Res.string.settings_cancel)) } })
    }
    currentState.pendingSigningProtectionChange?.let { target ->
        AlertDialog(onDismissRequest = onCancelSigningProtectionChange, title = { Text("Change signing protection?") },
            text = { Text("Changing to ${target.title().lowercase()} creates a new wallet key and DID. Your current credentials will be removed and must be issued again.") },
            confirmButton = { TextButton(onConfirmSigningProtectionChange, Modifier.testTag(WalletUiTestTags.SigningProtectionConfirm)) { Text("Create new wallet") } },
            dismissButton = { TextButton(onCancelSigningProtectionChange) { Text(stringResource(Res.string.settings_cancel)) } })
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text(stringResource(Res.string.settings_reset_question)) }, text = { Text(resetWalletDescription ?: stringResource(Res.string.settings_reset_description)) },
        confirmButton = { TextButton({ confirmReset = false; onResetWallet() }, Modifier.testTag(WalletUiTestTags.SettingsResetConfirm)) { Text(stringResource(Res.string.settings_reset)) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text(stringResource(Res.string.settings_cancel)) } })
}

private val WalletDemoProximityTransportProfile.titleResource: StringResource get() = when (this) {
    WalletDemoProximityTransportProfile.Default -> Res.string.settings_automatic
    WalletDemoProximityTransportProfile.Bluetooth -> Res.string.settings_bluetooth
    WalletDemoProximityTransportProfile.WifiAware -> Res.string.settings_wifi_aware
    WalletDemoProximityTransportProfile.ProvisionalNfcV2Hybrid -> Res.string.settings_nfc_bluetooth
    WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct -> Res.string.settings_nfc_direct
    WalletDemoProximityTransportProfile.ProvisionalNfcV2WifiAware -> Res.string.settings_nfc_wifi
}
private val WalletDemoProximityTransportProfile.descriptionResource: StringResource get() = when (this) {
    WalletDemoProximityTransportProfile.Default -> Res.string.settings_automatic_detail
    WalletDemoProximityTransportProfile.Bluetooth -> Res.string.settings_bluetooth_detail
    WalletDemoProximityTransportProfile.WifiAware -> Res.string.settings_wifi_detail
    WalletDemoProximityTransportProfile.ProvisionalNfcV2Hybrid -> Res.string.settings_nfc_bluetooth_detail
    WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct -> Res.string.settings_nfc_direct_detail
    WalletDemoProximityTransportProfile.ProvisionalNfcV2WifiAware -> Res.string.settings_nfc_wifi_detail
}
private val WalletDemoProximityTransportProfile.settingsTag: String get() = when (this) {
    WalletDemoProximityTransportProfile.Default -> WalletUiTestTags.SettingsProximityDefault
    WalletDemoProximityTransportProfile.ProvisionalNfcV2Hybrid -> WalletUiTestTags.SettingsProximityNfcV2Hybrid
    WalletDemoProximityTransportProfile.ProvisionalNfcV2Direct -> WalletUiTestTags.SettingsProximityNfcV2Direct
    else -> "wallet.settingsProximity$name"
}
