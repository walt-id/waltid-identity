package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoOfferedCredentialMetadata
import id.walt.walletdemo.compose.logic.resolvedCardTitle
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Selection, copy count and navigation are separate actions, including for assistive technology. */
@Composable
internal fun OfferedCredentialRow(
    credential: WalletDemoOfferedCredentialMetadata,
    issuer: String,
    issuerIdentifier: String,
    count: Int,
    limit: Int,
    enabled: Boolean,
    onCountChange: ((Int) -> Unit)?,
) {
    var showDetails by rememberSaveable(credential.configurationId) { mutableStateOf(false) }
    val title = credential.resolvedCardTitle()
    val receiveLabel = stringResource(Res.string.issuance_receive_named, title)
    Column {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CredentialSummaryRow(credential.offerArt(), modifier = Modifier.weight(1f)
                .testTag("issuance-identity-${credential.configurationId}"))
            if (onCountChange != null) {
                Switch(checked = count > 0, onCheckedChange = { onCountChange(if (it) 1 else 0) }, enabled = enabled && (count > 0 || limit > 0),
                    modifier = Modifier.testTag("issuance-select-${credential.configurationId}")
                        .semantics { contentDescription = receiveLabel })
            }
        }
        if (count > 0) {
            if (limit > 1 && onCountChange != null) {
                CopySelection(credential.configurationId, title, count, limit, enabled, onCountChange)
            } else {
                Text(stringResource(Res.string.issuance_one_copy), Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        WalletNavigationRow(
            title = stringResource(Res.string.issuance_information), onClick = { showDetails = true },
            icon = { WalletIcon(WalletSymbol.Info, null) },
            modifier = Modifier.testTag("issuance-details-${credential.configurationId}"),
        )
    }
    if (showDetails) {
        WalletDetailSheet(stringResource(Res.string.issuance_information), onDismiss = { showDetails = false },
            modifier = Modifier.testTag("issuance-credential-details")) {
            OfferedCredentialDetails(credential, issuer, issuerIdentifier)
        }
    }
}

@Composable
private fun CopySelection(id: String, title: String, count: Int, limit: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val fewer = stringResource(Res.string.issuance_fewer_copies, title)
    val more = stringResource(Res.string.issuance_more_copies, title)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(Res.string.issuance_copies, count), Modifier.weight(1f).testTag("issuance-copies-$id"),
            style = MaterialTheme.typography.bodyMedium)
        WalletCountControl(count, 1..limit, enabled, fewer, more, "issuance-fewer-$id", "issuance-more-$id", onChange)
    }
}
