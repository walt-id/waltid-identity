package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
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
        WalletSection(stringResource(Res.string.reader_trust_policy)) {
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
        WalletSection(stringResource(Res.string.reader_trust_reader_ca_trust_anchors)) {
            if (state.settings.trustAnchors.isEmpty()) SettingsNotice(stringResource(Res.string.reader_trust_no_cas))
            state.settings.trustAnchors.forEachIndexed { index, anchor ->
                if (index > 0) SettingsDivider()
                TrustMaterialRow(anchor.displayName, stringResource(Res.string.reader_trust_configured_reader_ca), enabled) {
                    removal = TrustRemoval(anchor.displayName, true, anchor.certificateDerBase64Url)
                }
            }
        }
        WalletSection(stringResource(Res.string.reader_trust_qualification_rical_providers)) {
            if (state.settings.ricalProviders.isEmpty()) SettingsNotice(stringResource(Res.string.reader_trust_no_providers))
            state.settings.ricalProviders.forEachIndexed { index, provider ->
                if (index > 0) SettingsDivider()
                TrustMaterialRow(provider.providerId,
                    stringResource(if (provider.establishReaderTrust) Res.string.reader_trust_provider_trusted else Res.string.reader_trust_provider_evidence), enabled) {
                    removal = TrustRemoval(provider.providerId, false, provider.providerId)
                }
            }
        }
        WalletSection(footer = stringResource(Res.string.reader_trust_formats)) {
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

internal fun handleReaderTrustImportPickerResult(controller: DemoReaderTrustSettingsController, result: ReaderTrustImportPickerResult) {
    when (result) {
        is ReaderTrustImportPickerResult.Selected -> controller.prepareImport(result.file.name, result.file.bytes)
        ReaderTrustImportPickerResult.Cancelled -> Unit
        is ReaderTrustImportPickerResult.Failed -> controller.reportImportError(result.error.message ?: "The selected file could not be read.")
    }
}

@Composable
private fun TrustMaterialRow(title: String, detail: String, enabled: Boolean, onRemove: () -> Unit) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(detail) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        trailingContent = {
            val removeLabel = stringResource(Res.string.reader_trust_remove_named, title)
            SettingsIconButton(removeLabel, onClick = onRemove, enabled = enabled) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        })
}
