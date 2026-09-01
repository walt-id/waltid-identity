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
import id.walt.wallet2.mobile.MobileWalletProximityReaderPolicy
import id.walt.walletdemo.compose.logic.DemoReaderTrustSettingsController

@Composable
internal fun DemoReaderTrustSettings(
    controller: DemoReaderTrustSettingsController,
) {
    val state by controller.state.collectAsState()
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
        Text("Reader Authentication", fontWeight = FontWeight.SemiBold)
        Text(
            "Choose which readers may reach disclosure review and manage public Reader CA or qualification RICAL trust material.",
        )
        ReaderPolicyChoice(
            title = "Allow anonymous or untrusted readers",
            selected = state.settings.readerPolicy ==
                MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted,
            tag = WalletUiTestTags.SettingsReaderPolicyAllowUntrusted,
            onSelect = {
                controller.setReaderPolicy(MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted)
            },
        )
        ReaderPolicyChoice(
            title = "Require a trusted reader",
            selected = state.settings.readerPolicy == MobileWalletProximityReaderPolicy.RequireTrusted,
            tag = WalletUiTestTags.SettingsReaderPolicyRequireTrusted,
            onSelect = { controller.setReaderPolicy(MobileWalletProximityReaderPolicy.RequireTrusted) },
        )
        if (state.settings.readerPolicy == MobileWalletProximityReaderPolicy.RequireTrusted &&
            state.settings.trustAnchors.isEmpty() && state.settings.ricalProviders.isEmpty()
        ) {
            Text("No trust material is configured, so all readers will be rejected.")
        }

        Text("Reader CA trust anchors", fontWeight = FontWeight.SemiBold)
        if (state.settings.trustAnchors.isEmpty()) Text("None configured")
        state.settings.trustAnchors.forEach { anchor ->
            TrustMaterialRow(
                title = anchor.displayName,
                detail = "Configured Reader CA",
                onRemove = { controller.removeReaderAuthority(anchor.certificateDerBase64Url) },
            )
        }
        Text("Qualification RICAL providers", fontWeight = FontWeight.SemiBold)
        if (state.settings.ricalProviders.isEmpty()) Text("None configured")
        state.settings.ricalProviders.forEach { provider ->
            TrustMaterialRow(
                title = provider.providerId,
                detail = if (provider.establishReaderTrust) "Establishes reader trust" else "Evidence only",
                onRemove = { controller.removeRicalProvider(provider.providerId) },
            )
        }

        Button(
            onClick = picker::launch,
            enabled = !state.importInProgress,
            modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.SettingsReaderTrustImport),
        ) {
            if (state.importInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics {
                        contentDescription = "Validating reader trust material"
                    }
                )
            }
            else Text("Import Reader CA or trust bundle")
        }
        OutlinedButton(
            onClick = controller::reset,
            enabled = state.settings.trustAnchors.isNotEmpty() ||
                state.settings.ricalProviders.isNotEmpty() ||
                state.settings.readerPolicy != MobileWalletProximityReaderPolicy.AllowAnonymousOrUntrusted,
            modifier = Modifier.fillMaxWidth().testTag(WalletUiTestTags.SettingsReaderTrustReset),
        ) {
            Text("Reset Reader Authentication settings")
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
            title = { Text("Review reader trust import") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(preview.sourceName)
                    preview.readerAuthorities.forEach { authority ->
                        Text(
                            "${authority.displayName}\nSubject: ${authority.subject}\n" +
                                "Issuer: ${authority.issuer}\nSHA-256: ${authority.sha256Fingerprint}\n" +
                                "Valid: ${authority.validFrom} – ${authority.validUntil}\n" +
                                "Profile: ${authority.profile}"
                        )
                    }
                    preview.ricalProviders.forEach { provider ->
                        Text(
                            "${provider.providerId}\nType: ${provider.type}\nIssued: ${provider.issuedAt}\n" +
                                "Next update: ${provider.nextUpdate ?: "Not specified"}\n" +
                                "Valid until: ${provider.validUntil ?: "Not specified"}"
                        )
                    }
                    Text(preview.policyEffect, fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = controller::confirmImport,
                    modifier = Modifier.testTag(WalletUiTestTags.SettingsReaderTrustImportConfirm),
                ) { Text("Import") }
            },
            dismissButton = {
                TextButton(
                    onClick = controller::cancelImport,
                    modifier = Modifier.testTag(WalletUiTestTags.SettingsReaderTrustImportCancel),
                ) { Text("Cancel") }
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
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}
