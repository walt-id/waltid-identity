package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.selection.SelectionContainer
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun IdentitySetupScreen(
    setup: WalletDemoIdentitySetup,
    warning: String?,
    onChoose: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRefresh: () -> Unit,
    progress: String? = null,
) {
    val refreshing = progress != null
    val options = (setup as? WalletDemoIdentitySetup.Choose)?.options.orEmpty()
    // Save only semantic choices. Executable handles always come from the latest SDK options.
    var recoveryId by rememberSaveable { mutableStateOf<String?>(null) }
    var storageId by rememberSaveable { mutableStateOf<String?>(null) }
    var approvalId by rememberSaveable { mutableStateOf<String?>(null) }
    var stepName by rememberSaveable { mutableStateOf(WalletDemoKeySetupStep.Recovery.name) }
    val step = WalletDemoKeySetupStep.valueOf(stepName)
    fun select(option: WalletDemoKeySetupOption) {
        recoveryId = option.recovery.id
        storageId = option.storage.id
        approvalId = option.approval.id
    }
    val retainedSelection = options.find {
        it.recovery.id == recoveryId && it.storage.id == storageId && it.approval.id == approvalId
    }
    val selected = retainedSelection ?: options.firstOrNull()
    fun back() { stepName = WalletDemoKeySetupStep.entries[step.ordinal - 1].name }
    SystemBackHandler(enabled = step != WalletDemoKeySetupStep.Recovery) { back() }
    LaunchedEffect(options) {
        // Empty options during refresh must not discard the saved choice.
        if (options.isNotEmpty()) {
            if (recoveryId != null && retainedSelection == null) stepName = WalletDemoKeySetupStep.Recovery.name
            select(selected!!)
        }
    }
    val scroll = rememberScrollState()
    LaunchedEffect(step) { scroll.scrollTo(0) }

    Surface(Modifier.fillMaxSize().safeDrawingPadding().testTag(WalletUiTestTags.IdentitySetup)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).widthIn(max = 640.dp).fillMaxWidth().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Text(stringResource(Res.string.setup_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (step == WalletDemoKeySetupStep.Recovery) Text(stringResource(Res.string.setup_intro), style = MaterialTheme.typography.bodyMedium)
                warning?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                when (setup) {
                    is WalletDemoIdentitySetup.Pending -> {
                        Text(setup.explanation)
                        if (setup.canRetry) Button(enabled = !refreshing, onClick = { onResume(setup.identityId) }) { Text(stringResource(Res.string.setup_retry)) }
                        Text(stringResource(Res.string.setup_cancel_notice))
                        TextButton(enabled = !refreshing, onClick = { onCancel(setup.identityId) }) { Text(stringResource(Res.string.setup_cancel_pending)) }
                    }
                    is WalletDemoIdentitySetup.Choose -> {
                        setup.message?.let { Text(it) }
                        if (selected == null) {
                            if (!refreshing) Text(stringResource(Res.string.setup_no_options))
                        } else {
                            Text(stringResource(Res.string.setup_step, step.ordinal + 1, step.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(when (step) {
                                WalletDemoKeySetupStep.Recovery -> stringResource(Res.string.setup_recovery_description)
                                WalletDemoKeySetupStep.Storage -> stringResource(Res.string.setup_storage_description)
                                WalletDemoKeySetupStep.Approval -> stringResource(Res.string.setup_approval_description)
                            })
                            if (step == WalletDemoKeySetupStep.Storage && selected.recovery.id != "new") {
                                setup.recoveryStorageNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            }
                            val choices = step.options(options, selected).map(step::choice).distinctBy { it.id }
                            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                val groups = if (step == WalletDemoKeySetupStep.Recovery)
                                    choices.groupBy { it.id.startsWith("restore:") }.values.toList()
                                else listOf(choices)
                                groups.forEach { group ->
                                    SettingsSection(title = if (step == WalletDemoKeySetupStep.Recovery)
                                        stringResource(if (group.first().id.startsWith("restore:")) Res.string.setup_restore_group else Res.string.setup_create_group)
                                        else step.title,
                                        footer = if (choices.size == 1) stringResource(Res.string.setup_single_option) else null) {
                                        group.forEachIndexed { index, choice ->
                                            if (index > 0) SettingsDivider()
                                            SettingsChoiceRow(choice.title, choice.detail, step.choice(selected).id == choice.id,
                                                onSelect = { select(step.select(options, selected, choice.id)) },
                                                modifier = Modifier.testTag(WalletUiTestTags.keySetupChoice(step.name, choices.indexOf(choice))),
                                                enabled = !refreshing, selectable = choices.size > 1,
                                                extra = choice.identifier?.let { identifier -> { RecoveryIdentifier(identifier) } })
                                        }
                                    }
                                }
                            }
                            if (step == WalletDemoKeySetupStep.Approval) {
                                SettingsSection(stringResource(Res.string.setup_summary), stringResource(Res.string.setup_summary_footer)) {
                                    SettingsDetailRow(stringResource(Res.string.setup_recovery),
                                        if (selected.recovery.id == "new") stringResource(Res.string.setup_no_backup) else selected.recovery.title)
                                    SettingsDivider()
                                    SettingsDetailRow(stringResource(Res.string.setup_storage), selected.storage.title)
                                    SettingsDivider()
                                    SettingsDetailRow(stringResource(Res.string.setup_approval), selected.approval.title)
                                }
                            }
                        }
                    }
                }
                if (setup is WalletDemoIdentitySetup.Choose && step == WalletDemoKeySetupStep.Recovery && setup.recoveryUnavailableReasons.isNotEmpty()) {
                    ProviderAvailability(setup.recoveryUnavailableReasons, !refreshing, onRefresh)
                }
                if (refreshing) Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Text(progress.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
                if ((warning != null || (setup is WalletDemoIdentitySetup.Choose && options.isEmpty())) &&
                    !(setup is WalletDemoIdentitySetup.Choose && step == WalletDemoKeySetupStep.Recovery && setup.recoveryUnavailableReasons.isNotEmpty())) {
                    TextButton(onClick = onRefresh, enabled = !refreshing) { Text(stringResource(Res.string.settings_try_again)) }
                }
            }
            if (selected != null) {
                HorizontalDivider()
                Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (step != WalletDemoKeySetupStep.Recovery) {
                        TextButton(onClick = { back() }) { Text(stringResource(Res.string.settings_back)) }
                    }
                    Button(onClick = {
                        if (step == WalletDemoKeySetupStep.Approval) onChoose(selected.id)
                        else stepName = WalletDemoKeySetupStep.entries[step.ordinal + 1].name
                    }, enabled = !refreshing, modifier = Modifier.weight(1f).testTag(WalletUiTestTags.KeySetupContinue)) {
                        Text(if (step != WalletDemoKeySetupStep.Approval) stringResource(Res.string.setup_continue) else if (selected.restoring) stringResource(Res.string.setup_restore) else stringResource(Res.string.setup_create))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecoveryIdentifier(identifier: String) {
    var expanded by rememberSaveable(identifier) { mutableStateOf(false) }
    TextButton(onClick = { expanded = !expanded }) {
        Text(stringResource(if (expanded) Res.string.setup_hide_did else Res.string.setup_show_did))
    }
    if (expanded) SelectionContainer { Text(identifier, style = MaterialTheme.typography.bodySmall) }
}

@Composable
internal fun KeyDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ProviderAvailability(reasons: List<String>, enabled: Boolean, onRefresh: () -> Unit) {
    SettingsSection(stringResource(Res.string.setup_backup_availability)) {
        reasons.distinct().forEach { SettingsNotice(it) }
        SettingsActionRow(stringResource(Res.string.setup_check_again), onRefresh, enabled = enabled)
    }
}
