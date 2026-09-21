package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import id.walt.walletdemo.compose.ui.resources.*
import id.walt.wallet2.mobile.ProximityReaderPolicy
import id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController

@Composable
internal fun DemoReaderTrustSettings(
    controller: DemoReaderTrustSettingsController,
) {
    val state by controller.state.collectAsState()
    val validatingLabel = stringResource(Res.string.reader_trust_validating)
    val picker = rememberReaderTrustImportPicker { result ->
        handleReaderTrustImportPickerResult(controller, result)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.SettingsReaderAuthentication),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalDivider()
        Text(stringResource(Res.string.reader_trust_reader_authentication), fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(Res.string.reader_trust_choose_which_readers_may_reach_disclosure_review_and_manage_public_rea),
        )
        ReaderPolicyChoice(
            title = stringResource(Res.string.reader_trust_allow_anonymous_or_untrusted_readers),
            selected = state.settings.readerPolicy ==
                ProximityReaderPolicy.AllowAnonymousOrUntrusted,
            tag = WalletUiTestTags.SettingsReaderPolicyAllowUntrusted,
            onSelect = {
                controller.setReaderPolicy(ProximityReaderPolicy.AllowAnonymousOrUntrusted)
            },
        )
        ReaderPolicyChoice(
            title = stringResource(Res.string.reader_trust_require_a_trusted_reader),
            selected = state.settings.readerPolicy == ProximityReaderPolicy.RequireTrusted,
            tag = WalletUiTestTags.SettingsReaderPolicyRequireTrusted,
            onSelect = { controller.setReaderPolicy(ProximityReaderPolicy.RequireTrusted) },
        )
        if (state.settings.readerPolicy == ProximityReaderPolicy.RequireTrusted &&
            state.settings.trustAnchors.isEmpty() && state.settings.ricalProviders.isEmpty()
        ) {
            Text(stringResource(Res.string.reader_trust_no_trust_material_is_configured_so_all_readers_will_be_rejected))
        }

        Text(stringResource(Res.string.reader_trust_reader_ca_trust_anchors), fontWeight = FontWeight.SemiBold)
        if (state.settings.trustAnchors.isEmpty()) Text(stringResource(Res.string.reader_trust_none_configured))
        state.settings.trustAnchors.forEach { anchor ->
            TrustMaterialRow(
                title = anchor.displayName,
                detail = stringResource(Res.string.reader_trust_configured_reader_ca),
                onRemove = { controller.removeReaderAuthority(anchor.certificateDerBase64Url) },
            )
        }
        Text(stringResource(Res.string.reader_trust_qualification_rical_providers), fontWeight = FontWeight.SemiBold)
        if (state.settings.ricalProviders.isEmpty()) Text(stringResource(Res.string.reader_trust_none_configured))
        state.settings.ricalProviders.forEach { provider ->
            TrustMaterialRow(
                title = provider.providerId,
                detail = if (provider.establishReaderTrust) stringResource(Res.string.reader_trust_establishes_reader_trust) else stringResource(Res.string.reader_trust_evidence_only),
                onRemove = { controller.removeRicalProvider(provider.providerId) },
            )
        }

        Button(
            onClick = picker::launch,
            enabled = !state.importInProgress && !state.loading,
            modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.SettingsReaderTrustImport),
        ) {
            if (state.importInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = validatingLabel
                    }
                )
            }
            else Text(stringResource(Res.string.reader_trust_import_reader_ca_or_trust_bundle))
        }
        OutlinedButton(
            onClick = controller::reset,
            enabled = state.settings.trustAnchors.isNotEmpty() ||
                state.settings.ricalProviders.isNotEmpty() ||
                state.settings.readerPolicy != ProximityReaderPolicy.AllowAnonymousOrUntrusted,
            modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.SettingsReaderTrustReset),
        ) {
            Text(stringResource(Res.string.reader_trust_reset_reader_authentication_settings))
        }
        state.error?.let { error ->
            Text(
                error,
                modifier = Modifier
                    .testTag(WalletUiTestTags.SettingsReaderTrustError)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }

    state.pendingImport?.let { preview ->
        AlertDialog(
            modifier = Modifier.testTag(WalletUiTestTags.SettingsReaderTrustImportReview),
            onDismissRequest = controller::cancelImport,
            title = { Text(stringResource(Res.string.reader_trust_review_reader_trust_import)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(preview.sourceName)
                    preview.readerAuthorities.forEach { authority ->
                        Text(
                            stringResource(Res.string.reader_trust_certificate_details, authority.displayName,
                                authority.subject, authority.issuer, authority.sha256Fingerprint,
                                authority.validFrom.toString(), authority.validUntil.toString())
                        )
                    }
                    preview.ricalProviders.forEach { provider ->
                        Text(
                            stringResource(Res.string.reader_trust_rical_details, provider.providerId, provider.type,
                                provider.issuedAt.toString(), provider.nextUpdate?.toString() ?: stringResource(Res.string.reader_trust_unspecified),
                                provider.validUntil?.toString() ?: stringResource(Res.string.reader_trust_unspecified))
                        )
                    }
                    Text(stringResource(if (preview.resultingSettings.readerPolicy == ProximityReaderPolicy.RequireTrusted)
                        Res.string.reader_trust_effect_require else Res.string.reader_trust_effect_allow), fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = controller::confirmImport,
                    modifier = Modifier.testTag(WalletUiTestTags.SettingsReaderTrustImportConfirm),
                ) { Text(stringResource(Res.string.reader_trust_import)) }
            },
            dismissButton = {
                TextButton(
                    onClick = controller::cancelImport,
                    modifier = Modifier.testTag(WalletUiTestTags.SettingsReaderTrustImportCancel),
                ) { Text(stringResource(Res.string.reader_trust_cancel)) }
            },
        )
    }
}

@Composable
private fun ReaderPolicyChoice(
    title: String,
    selected: Boolean,
    tag: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.RadioButton,
            )
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(title, modifier = Modifier.padding(start = 8.dp))
    }
}

internal fun handleReaderTrustImportPickerResult(
    controller: DemoReaderTrustSettingsController,
    result: ReaderTrustImportPickerResult,
) {
    when (result) {
        is ReaderTrustImportPickerResult.Selected ->
            controller.prepareImport(result.file.name, result.file.bytes)
        ReaderTrustImportPickerResult.Cancelled -> Unit
        is ReaderTrustImportPickerResult.Failed -> controller.reportImportError(
            result.error.message ?: "The selected file could not be read"
        )
    }
}

@Composable
private fun TrustMaterialRow(
    title: String,
    detail: String,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(detail)
        }
        TextButton(onClick = onRemove) { Text(stringResource(Res.string.reader_trust_remove)) }
    }
}
