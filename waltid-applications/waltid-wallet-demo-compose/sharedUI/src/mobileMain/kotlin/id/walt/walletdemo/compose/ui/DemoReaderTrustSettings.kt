package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.ProximityReaderPolicy
import id.walt.wallet2.mobile.ProximityReaderTrustImportKind
import id.walt.wallet2.mobile.ProximityReaderTrustImportPreview
import id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

private data class TrustRemoval(val name: String, val readerCa: Boolean, val id: String)

@Composable
internal fun DemoReaderTrustSettings(controller: DemoReaderTrustSettingsController) {
    val state by controller.state.collectAsState()
    var reset by rememberSaveable { mutableStateOf(false) }
    var removal by remember { mutableStateOf<TrustRemoval?>(null) }
    val enabled = !state.loading && !state.importInProgress
    val picker = rememberReaderTrustImportPicker { handleReaderTrustImportPickerResult(controller, it) }
    DisposableEffect(controller) { onDispose { controller.cancelImport() } }
    Column(Modifier.fillMaxWidth().testTag(WalletUiTestTags.SettingsReaderAuthentication),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        SettingsSection(stringResource(Res.string.reader_trust_policy)) {
            Column(Modifier.selectableGroup()) {
                SettingsChoiceRow(stringResource(Res.string.reader_trust_allow_anonymous_or_untrusted_readers),
                    stringResource(Res.string.reader_trust_allow_description),
                    state.settings.readerPolicy == ProximityReaderPolicy.AllowAnonymousOrUntrusted,
                    { controller.setReaderPolicy(ProximityReaderPolicy.AllowAnonymousOrUntrusted) },
                    Modifier.testTag(WalletUiTestTags.SettingsReaderPolicyAllowUntrusted), enabled = enabled)
                SettingsDivider()
                SettingsChoiceRow(stringResource(Res.string.reader_trust_require_a_trusted_reader),
                    stringResource(Res.string.reader_trust_require_description),
                    state.settings.readerPolicy == ProximityReaderPolicy.RequireTrusted,
                    { controller.setReaderPolicy(ProximityReaderPolicy.RequireTrusted) },
                    Modifier.testTag(WalletUiTestTags.SettingsReaderPolicyRequireTrusted), enabled = enabled)
            }
            if (state.settings.readerPolicy == ProximityReaderPolicy.RequireTrusted &&
                state.settings.trustAnchors.isEmpty() && state.settings.ricalProviders.none { it.establishReaderTrust }) {
                SettingsNotice(stringResource(Res.string.reader_trust_no_trust_material_is_configured_so_all_readers_will_be_rejected))
            }
        }
        SettingsSection(stringResource(Res.string.reader_trust_reader_ca_trust_anchors)) {
            if (state.settings.trustAnchors.isEmpty()) SettingsNotice(stringResource(Res.string.reader_trust_no_cas))
            state.settings.trustAnchors.forEachIndexed { index, anchor ->
                if (index > 0) SettingsDivider()
                TrustMaterialRow(anchor.displayName, stringResource(Res.string.reader_trust_configured_reader_ca), enabled) {
                    removal = TrustRemoval(anchor.displayName, true, anchor.certificateDerBase64Url)
                }
            }
        }
        SettingsSection(stringResource(Res.string.reader_trust_qualification_rical_providers)) {
            if (state.settings.ricalProviders.isEmpty()) SettingsNotice(stringResource(Res.string.reader_trust_no_providers))
            state.settings.ricalProviders.forEachIndexed { index, provider ->
                if (index > 0) SettingsDivider()
                TrustMaterialRow(provider.providerId,
                    stringResource(if (provider.establishReaderTrust) Res.string.reader_trust_provider_trusted else Res.string.reader_trust_provider_evidence), enabled) {
                    removal = TrustRemoval(provider.providerId, false, provider.providerId)
                }
            }
        }
        SettingsSection(footer = stringResource(Res.string.reader_trust_formats)) {
            SettingsActionRow(stringResource(Res.string.reader_trust_import_reader_ca_or_trust_bundle), picker::launch,
                Modifier.testTag(WalletUiTestTags.SettingsReaderTrustImport), enabled = enabled,
                icon = { SettingsSymbol(Res.drawable.settings_import) })
            SettingsDivider()
            SettingsActionRow(stringResource(Res.string.reader_trust_reset_reader_authentication_settings), { reset = true },
                Modifier.testTag(WalletUiTestTags.SettingsReaderTrustReset), destructive = true,
                enabled = enabled && (state.settings.trustAnchors.isNotEmpty() || state.settings.ricalProviders.isNotEmpty() ||
                    state.settings.readerPolicy != ProximityReaderPolicy.AllowAnonymousOrUntrusted),
                icon = { Icon(Icons.Default.Refresh, null) })
            if (!enabled) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { SettingsNotice(it, error = true, modifier = Modifier.testTag(WalletUiTestTags.SettingsReaderTrustError)) }
        }
    }
    if (reset) AlertDialog(onDismissRequest = { reset = false },
        title = { Text(stringResource(Res.string.reader_trust_reset_question)) },
        text = { Text(stringResource(Res.string.reader_trust_reset_notice)) },
        confirmButton = { TextButton(onClick = { reset = false; controller.reset() },
            modifier = Modifier.testTag("reader-trust-reset-confirm")) { Text(stringResource(Res.string.reader_trust_reset_reader_authentication_settings)) } },
        dismissButton = { TextButton(onClick = { reset = false }) { Text(stringResource(Res.string.reader_trust_cancel)) } })
    removal?.let { item ->
        AlertDialog(onDismissRequest = { removal = null },
            title = { Text(stringResource(Res.string.reader_trust_remove_question, item.name)) },
            text = { Text(stringResource(if (item.readerCa) Res.string.reader_trust_remove_ca_notice else Res.string.reader_trust_remove_provider_notice)) },
            confirmButton = { TextButton(onClick = {
                removal = null
                if (item.readerCa) controller.removeReaderAuthority(item.id) else controller.removeRicalProvider(item.id)
            }, modifier = Modifier.testTag("reader-trust-remove-confirm")) { Text(stringResource(Res.string.reader_trust_remove)) } },
            dismissButton = { TextButton(onClick = { removal = null }) { Text(stringResource(Res.string.reader_trust_cancel)) } })
    }
    state.pendingImport?.let { ReaderTrustImportReview(it, controller::confirmImport, controller::cancelImport) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTrustImportReview(preview: ProximityReaderTrustImportPreview, onImport: () -> Unit, onCancel: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.exportTestTagsForPlatformAutomation().testTag(WalletUiTestTags.SettingsReaderTrustImportReview).fillMaxWidth().fillMaxHeight(.9f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(Res.string.reader_trust_review_reader_trust_import), style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                SettingsSection {
                    SettingsDetailRow(stringResource(Res.string.reader_trust_file), preview.sourceName)
                    SettingsDetailRow(stringResource(Res.string.reader_trust_kind), stringResource(
                        if (preview.kind == ProximityReaderTrustImportKind.ReaderCa) Res.string.reader_trust_configured_reader_ca
                        else Res.string.reader_trust_bundle))
                    SettingsDetailRow(stringResource(Res.string.reader_trust_policy), stringResource(
                        if (preview.resultingSettings.readerPolicy == ProximityReaderPolicy.RequireTrusted)
                            Res.string.reader_trust_require_a_trusted_reader else Res.string.reader_trust_allow_anonymous_or_untrusted_readers))
                }
                preview.readerAuthorities.forEach { authority ->
                    SettingsSection(authority.displayName) {
                        SettingsDetailRow(stringResource(Res.string.reader_trust_type), authority.profile)
                        SettingsDetailRow(stringResource(Res.string.reader_trust_role), stringResource(Res.string.reader_trust_anchor))
                        SettingsDetailRow(stringResource(Res.string.reader_trust_subject), authority.subject)
                        SettingsDetailRow(stringResource(Res.string.reader_trust_issuer), authority.issuer)
                        SettingsDetailRow(stringResource(Res.string.reader_trust_valid_from), authority.validFrom.toString())
                        SettingsDetailRow(stringResource(Res.string.reader_trust_valid_until), authority.validUntil.toString())
                    }
                    SettingsCopyRow(stringResource(Res.string.reader_trust_fingerprint), authority.sha256Fingerprint,
                        "reader-ca-fingerprint-${authority.sha256Fingerprint}", "reader-ca-copy-${authority.sha256Fingerprint}",
                        stringResource(Res.string.reader_trust_copy_fingerprint, authority.displayName),
                        stringResource(Res.string.reader_trust_fingerprint_copied, authority.displayName))
                }
                preview.ricalProviders.forEach { provider ->
                    SettingsSection(provider.providerName) {
                        SettingsDetailRow(stringResource(Res.string.reader_trust_provider_id), provider.providerId)
                        SettingsDetailRow(stringResource(Res.string.reader_trust_type), provider.type)
                        SettingsDetailRow(stringResource(Res.string.reader_trust_issued), provider.issuedAt.toString())
                        SettingsDetailRow(stringResource(Res.string.reader_trust_next_update), provider.nextUpdate?.toString() ?: stringResource(Res.string.reader_trust_unspecified))
                        SettingsDetailRow(stringResource(Res.string.reader_trust_valid_until), provider.validUntil?.toString() ?: stringResource(Res.string.reader_trust_unspecified))
                        SettingsNotice(stringResource(if (provider.establishesReaderTrust) Res.string.reader_trust_establishes_reader_trust else Res.string.reader_trust_evidence_only))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onCancel, Modifier.weight(1f).testTag(WalletUiTestTags.SettingsReaderTrustImportCancel)) { Text(stringResource(Res.string.reader_trust_cancel)) }
                Button(onImport, Modifier.weight(1f).testTag(WalletUiTestTags.SettingsReaderTrustImportConfirm)) { Text(stringResource(Res.string.reader_trust_import)) }
            }
        }
    }
}

internal fun handleReaderTrustImportPickerResult(controller: DemoReaderTrustSettingsController, result: ReaderTrustImportPickerResult) {
    when (result) {
        is ReaderTrustImportPickerResult.Selected -> controller.prepareImport(result.file.name, result.file.bytes)
        ReaderTrustImportPickerResult.Cancelled -> Unit
        is ReaderTrustImportPickerResult.Failed -> controller.reportImportError(result.error.message ?: "The selected file could not be read.")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrustMaterialRow(title: String, detail: String, enabled: Boolean, onRemove: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(detail) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        trailingContent = {
            val removeLabel = stringResource(Res.string.reader_trust_remove_named, title)
            TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(removeLabel) } }, state = rememberTooltipState()) {
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(Icons.Default.Delete, removeLabel, tint = MaterialTheme.colorScheme.error)
                }
            }
        })
}
