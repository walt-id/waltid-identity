package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.walt.walletdemo.compose.logic.WalletDemoStatusReadState
import id.walt.walletdemo.compose.logic.displayStatus
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.credential_status_checking
import id.walt.walletdemo.compose.ui.resources.credential_status_details
import id.walt.walletdemo.compose.ui.resources.credential_status_refresh
import id.walt.walletdemo.compose.ui.resources.credential_status_title
import id.walt.walletdemo.compose.ui.resources.metadata_collapsed
import id.walt.walletdemo.compose.ui.resources.metadata_expanded
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CredentialStatusSection(credentialId: String, compact: Boolean = false) {
    val context = LocalCredentialStatusUi.current
    val retained = context.snapshot.credentials[credentialId]
    val status = context.snapshot.displayStatus(credentialId)
    val unavailable = context.snapshot.readState == WalletDemoStatusReadState.Unavailable || context.snapshot.available && retained == null
    val facts = remember(retained, status, unavailable) { (retained ?: status).facts(unavailable) }
    var expanded by rememberSaveable(credentialId, context.contextKey) { mutableStateOf(false) }
    val refreshing = credentialId in context.snapshot.refreshing
    val refreshVisible = context.check != null && (status.action in setOf("CHECK", "RETRY") || refreshing || status.references.any { it.eligibleAt != null })
    val disclosure = facts.extra.isNotEmpty() || compact && (facts.main.isNotEmpty() || refreshVisible)
    val disclosureEnabled = context.snapshot.readState != WalletDemoStatusReadState.Loading || retained != null
    val expandedVisible = expanded && disclosure
    val rotation by animateFloatAsState(
        if (expandedVisible) { if (LocalLayoutDirection.current == LayoutDirection.Rtl) -90f else 90f } else 0f,
        WalletMotion.disclosure(LocalWalletVisualPreferences.current.reduceMotion), label = "credential-status-chevron",
    )
    val disclosureLabel = stringResource(Res.string.credential_status_details)
    val disclosureState = stringResource(if (expandedVisible) Res.string.metadata_expanded else Res.string.metadata_collapsed)
    val glyphSize = with(LocalDensity.current) { (if (compact) 22.sp else 26.sp).toDp() }
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().then(if (compact) Modifier.testTag("credential-status-$credentialId") else Modifier)) {
            if (compact) SettingsDivider()
            val header = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(
                if (compact && disclosure) Modifier.testTag("credential-status-details-$credentialId")
                    .semantics { stateDescription = disclosureState }
                    .clickable(enabled = disclosureEnabled, role = Role.Button, onClickLabel = disclosureLabel) { expanded = !expanded }
                else Modifier,
            )
            Row(header.padding(start = 16.dp, end = if (disclosure || !compact && refreshVisible) 4.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CredentialStatusIcon(status, size = glyphSize)
                Text(status.title(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!compact && refreshVisible) StatusRefreshButton(credentialId)
                    if (disclosure) {
                        if (compact) Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.primary)
                        } else IconButton(onClick = { expanded = !expanded }, enabled = disclosureEnabled,
                            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.testTag("credential-status-details-$credentialId").semantics { stateDescription = disclosureState }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, disclosureLabel, Modifier.rotate(rotation))
                        }
                    }
                }
            }
            if (!compact) CredentialStatusFactsContent(credentialId, main = facts.main)
            WalletExpandableContent(expandedVisible) {
                Column {
                    if (compact && facts.main.isNotEmpty()) SettingsDivider()
                    CredentialStatusFactsContent(credentialId, main = if (compact) facts.main else emptyList(), extra = facts.extra)
                    if (compact && refreshVisible) Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
                        StatusRefreshButton(credentialId)
                    }
                }
            }
        }
    }
    if (compact) content()
    else WalletSection(stringResource(Res.string.credential_status_title), modifier = Modifier.testTag("credential-status-$credentialId")) { content() }
}

@Composable
private fun StatusRefreshButton(credentialId: String) {
    val context = LocalCredentialStatusUi.current
    val status = context.snapshot.displayStatus(credentialId)
    val refreshing = credentialId in context.snapshot.refreshing
    val label = stringResource(if (refreshing) Res.string.credential_status_checking else Res.string.credential_status_refresh)
    IconButton(onClick = { context.check?.invoke(credentialId) },
        enabled = status.action in setOf("CHECK", "RETRY") && !refreshing && !context.checking && context.snapshot.readState != WalletDemoStatusReadState.Loading,
        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        modifier = Modifier.testTag("credential-status-check-$credentialId").semantics { contentDescription = label }) {
        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else WalletIcon(WalletSymbol.Retry, null)
    }
}
