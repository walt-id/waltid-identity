package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.credential_status_auto
import id.walt.walletdemo.compose.ui.resources.credential_status_auto_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_auto_signed_in
import id.walt.walletdemo.compose.ui.resources.credential_status_check_all
import id.walt.walletdemo.compose.ui.resources.credential_status_checking
import id.walt.walletdemo.compose.ui.resources.credential_status_deferred
import id.walt.walletdemo.compose.ui.resources.credential_status_deferred_count
import id.walt.walletdemo.compose.ui.resources.credential_status_error_count
import id.walt.walletdemo.compose.ui.resources.credential_status_error_counts_detail
import id.walt.walletdemo.compose.ui.resources.credential_status_how
import id.walt.walletdemo.compose.ui.resources.credential_status_how_network
import id.walt.walletdemo.compose.ui.resources.credential_status_how_privacy
import id.walt.walletdemo.compose.ui.resources.credential_status_how_verifier
import id.walt.walletdemo.compose.ui.resources.credential_status_not_refreshed
import id.walt.walletdemo.compose.ui.resources.credential_status_privacy
import id.walt.walletdemo.compose.ui.resources.credential_status_processed
import id.walt.walletdemo.compose.ui.resources.credential_status_server_unsupported
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CredentialStatusSettings(custodial: Boolean) {
    val status = LocalCredentialStatusUi.current
    val supported = !status.snapshot.legacySubmission
    WalletSection(footer = stringResource(if (custodial) Res.string.credential_status_auto_signed_in else Res.string.credential_status_auto_detail) +
        "\n" + stringResource(Res.string.credential_status_privacy)) {
        SettingsToggleRow(stringResource(Res.string.credential_status_auto), status.automatic,
            { status.setAutomatic?.invoke(it) }, Modifier.testTag("settings-credential-status-automatic"),
            enabled = supported && status.setAutomatic != null)
    }
    WalletSection(footer = status.summary?.takeIf { it.deferred > 0 }?.let { stringResource(Res.string.credential_status_deferred) }) {
        val checking = stringResource(Res.string.credential_status_checking)
        SettingsActionRow(stringResource(Res.string.credential_status_check_all),
            { status.checkAll?.invoke() }, Modifier.testTag("settings-credential-status-check-all")
                .semantics { if (status.checking) stateDescription = checking },
            enabled = supported && !status.checking && status.checkAll != null,
            icon = {
                if (status.checking) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else WalletIcon(WalletSymbol.Retry, null)
            })
        status.summary?.let { summary ->
            SettingsDivider()
            StatusCountRow(stringResource(Res.string.credential_status_processed), summary.completed)
            if (summary.unchanged > 0) StatusCountRow(stringResource(Res.string.credential_status_not_refreshed), summary.unchanged)
            if (summary.deferred > 0) StatusCountRow(stringResource(Res.string.credential_status_deferred_count), summary.deferred)
            if (summary.checkErrors > 0) {
                SettingsDivider()
                StatusCountRow(stringResource(Res.string.credential_status_error_count), summary.checkErrors)
            }
        }
        status.error?.let { SettingsNotice(it, error = true) }
        if (!supported) SettingsNotice(stringResource(Res.string.credential_status_server_unsupported))
    }
    WalletSection {
        MetadataDisclosure(stringResource(Res.string.credential_status_how), initiallyExpanded = false,
            modifier = Modifier.padding(horizontal = 16.dp)) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (text in listOf(Res.string.credential_status_how_network, Res.string.credential_status_how_privacy,
                    Res.string.credential_status_how_verifier, Res.string.credential_status_error_counts_detail)) {
                    Text(stringResource(text), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun StatusCountRow(label: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(count.toString(), style = MaterialTheme.typography.bodyMedium)
    }
}
