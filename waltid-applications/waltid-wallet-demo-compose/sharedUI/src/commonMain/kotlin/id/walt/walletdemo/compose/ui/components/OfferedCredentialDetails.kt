package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** Advertised definitions use the stored-data vocabulary, but never pretend to be received values. */
@Composable
internal fun OfferedCredentialDetails(credential: WalletDemoOfferedCredentialMetadata, issuer: String, issuerIdentifier: String) {
    CredentialSummaryRow(credential.offerArt(), supportingText = issuer)
    credential.display?.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
    Text(stringResource(Res.string.issuance_not_received), style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (credential.claims.isEmpty()) {
        WalletSection(title = stringResource(Res.string.issuance_claim_definitions)) {
            Text(stringResource(Res.string.issuance_no_definitions), Modifier.padding(16.dp))
        }
    } else {
        listOf(true, false).forEach { required ->
            val definitions = credential.claims.filter { (it.mandatory == true) == required }
            if (definitions.isNotEmpty()) WalletSection(title = stringResource(
                if (required) Res.string.issuance_always_included else Res.string.issuance_optional)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    definitions.forEachIndexed { index, definition ->
                        if (index > 0) MetadataRowDivider()
                        Text(definition.displayLabel(credential.format), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
    Text(stringResource(Res.string.issuance_definitions_hint), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    WalletSection(title = stringResource(Res.string.credential_technical_details)) {
        SettingsDetailRow(stringResource(Res.string.issuance_issuer), issuerIdentifier)
        SettingsDetailRow(stringResource(Res.string.issuance_format), credential.format)
        SettingsDetailRow(stringResource(Res.string.issuance_configuration), credential.configurationId)
        (credential.vct ?: credential.doctype)?.let { SettingsDetailRow(stringResource(Res.string.issuance_type), it) }
    }
}

internal fun WalletDemoOfferedCredentialMetadata.offerArt(): CredentialCardArtModel {
    val title = resolvedCardTitle()
    return (display ?: WalletDemoMetadataDisplay(title, null, null)).toCardArt(configurationId, title)
}
