package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
    largeArt: Boolean = false,
) {
    val navigation = LocalWalletReviewNavigation.current
    var showDetails by rememberSaveable(credential.configurationId) { mutableStateOf(false) }
    val title = credential.resolvedCardTitle()
    val receiveLabel = stringResource(Res.string.issuance_receive_named, title)
    val canSelect = enabled && (count > 0 || limit > 0)
    val art = credential.offerArt()
    val textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val widestWord = remember(title, textStyle, measurer) {
        title.split(Regex("\\s+")).maxOfOrNull { measurer.measure(AnnotatedString(it), textStyle, maxLines = 1).size.width } ?: 0
    }
    val density = LocalDensity.current
    Column {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(16.dp)) {
            val summaryWidth = maxWidth - 64.dp // 52 dp switch + 12 dp row gap.
            val thumbnailWidth = credentialThumbnailWidth(summaryWidth, density.fontScale)
            val needsSeparateSelection = summaryWidth < thumbnailWidth + 12.dp + with(density) { widestWord.toDp() }
            if (largeArt) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CredentialCardArt(art, Modifier.fillMaxWidth().testTag("issuance-large-art-${credential.configurationId}"))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(title, Modifier.weight(1f).testTag("issuance-identity-${credential.configurationId}"),
                            style = MaterialTheme.typography.titleMedium)
                        if (onCountChange != null) OfferSelection(credential.configurationId, receiveLabel, count, canSelect, onCountChange)
                    }
                }
            } else if (onCountChange != null && needsSeparateSelection) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CredentialSummaryRow(art, modifier = Modifier.fillMaxWidth()
                        .testTag("issuance-identity-${credential.configurationId}"))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(Res.string.issuance_receive), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        OfferSelection(credential.configurationId, receiveLabel, count, canSelect, onCountChange)
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CredentialSummaryRow(art, modifier = Modifier.weight(1f)
                        .testTag("issuance-identity-${credential.configurationId}"))
                    if (onCountChange != null) OfferSelection(credential.configurationId, receiveLabel, count, canSelect, onCountChange)
                }
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
            title = stringResource(Res.string.issuance_information), onClick = { if (navigation != null) navigation.openOffered(credential.configurationId) else showDetails = true },
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
private fun OfferSelection(id: String, label: String, count: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Switch(checked = count > 0, onCheckedChange = { onChange(if (it) 1 else 0) }, enabled = enabled,
        modifier = Modifier.testTag("issuance-select-$id").semantics { contentDescription = label })
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
