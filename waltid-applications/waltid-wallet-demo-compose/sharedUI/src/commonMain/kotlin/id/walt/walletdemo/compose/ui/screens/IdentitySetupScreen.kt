package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun IdentitySetupScreen(
    setup: WalletDemoIdentitySetup,
    warning: String?,
    onChoose: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val options = (setup as? WalletDemoIdentitySetup.Choose)?.options.orEmpty()
    var selectedId by remember(setup) { mutableStateOf(options.firstOrNull()?.id) }
    var step by remember(setup) { mutableStateOf(WalletDemoKeySetupStep.Recovery) }
    val selected = options.find { it.id == selectedId }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }

    Surface(Modifier.fillMaxSize().testTag(WalletUiTestTags.IdentitySetup)) {
        Column {
            Column(Modifier.weight(1f).verticalScroll(scroll).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Set up your wallet", style = MaterialTheme.typography.headlineMedium)
                Text("Your wallet uses a signing key to prove that you hold your credentials.", style = MaterialTheme.typography.bodyMedium)
                warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when (setup) {
                    is WalletDemoIdentitySetup.Pending -> {
                        Text("Key setup was interrupted. Retry to continue with the same key and DID.")
                        Button(onClick = { onResume(setup.identityId) }) { Text("Retry setup") }
                        Text("Cancelling removes pending local setup. Any submitted recovery record is retained.")
                        TextButton(onClick = { onCancel(setup.identityId) }) { Text("Cancel pending setup") }
                    }
                    is WalletDemoIdentitySetup.Choose -> {
                        setup.message?.let { Text(it) }
                        if (selected == null) {
                            Text("No configured option is available. Check device authorization and backup settings, then refresh.")
                        } else {
                            Text("${step.ordinal + 1} of 3 · ${step.title}", style = MaterialTheme.typography.titleLarge)
                            Text(when (step) {
                                WalletDemoKeySetupStep.Recovery -> "Choose whether to back up a new signing key or restore an existing one."
                                WalletDemoKeySetupStep.Storage -> "Choose where signing happens. Only storage compatible with your recovery choice is shown."
                                WalletDemoKeySetupStep.Approval -> "Choose when the system asks you to approve signing. This is separate from unlocking the app."
                            })
                            if (step == WalletDemoKeySetupStep.Storage && selected.recovery.id != "new") {
                                setup.recoveryStorageNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            }
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                step.options(options, selected).map(step::choice).distinctBy { it.id }.forEachIndexed { index, choice ->
                                    KeyChoiceCard(choice, step.choice(selected).id == choice.id,
                                        Modifier.testTag(WalletUiTestTags.keySetupChoice(step.name, index))) {
                                        selectedId = step.select(options, selected, choice.id).id
                                    }
                                }
                            }
                            if (step == WalletDemoKeySetupStep.Approval) {
                                HorizontalDivider()
                                Text("${selected.recovery.title}\n${selected.storage.title}", style = MaterialTheme.typography.bodyMedium)
                                Text("${if (selected.restoring) "Restores" else "Creates"} a signing key and its wallet identifier (DID). Credentials are not restored by key recovery.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                TextButton(onClick = onRefresh) { Text("Refresh available options") }
            }
            if (selected != null) {
                HorizontalDivider()
                Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step != WalletDemoKeySetupStep.Recovery) {
                        TextButton(onClick = { step = WalletDemoKeySetupStep.entries[step.ordinal - 1] }) { Text("Back") }
                    }
                    Button(onClick = {
                        if (step == WalletDemoKeySetupStep.Approval) onChoose(selected.id)
                        else step = WalletDemoKeySetupStep.entries[step.ordinal + 1]
                    }, modifier = Modifier.weight(1f).testTag(WalletUiTestTags.KeySetupContinue)) {
                        Text(if (step != WalletDemoKeySetupStep.Approval) "Continue" else if (selected.restoring) "Restore signing key" else "Create signing key")
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyChoiceCard(choice: WalletDemoKeyChoice, selected: Boolean, modifier: Modifier, onSelect: () -> Unit) {
    OutlinedCard(
        modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            else Surface(Modifier.size(24.dp), shape = CircleShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant)) {}
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(choice.title, style = MaterialTheme.typography.titleMedium)
                Text(choice.detail, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
