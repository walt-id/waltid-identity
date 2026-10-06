package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
    var pageName by rememberSaveable { mutableStateOf(IdentitySetupPage.Summary.name) }
    val page = IdentitySetupPage.valueOf(pageName)
    fun select(option: WalletDemoKeySetupOption) {
        recoveryId = option.recovery.id
        storageId = option.storage.id
        approvalId = option.approval.id
    }
    val retainedSelection = options.find {
        it.recovery.id == recoveryId && it.storage.id == storageId && it.approval.id == approvalId
    }
    val selected = retainedSelection ?: options.firstOrNull()
    val latestSetup by rememberUpdatedState(setup)
    val latestOptions by rememberUpdatedState(options)
    val latestSelected by rememberUpdatedState(selected)
    val latestWarning by rememberUpdatedState(warning)
    val latestProgress by rememberUpdatedState(progress)
    fun back() { pageName = IdentitySetupPage.Summary.name }
    SystemBackHandler(enabled = page != IdentitySetupPage.Summary) { if (!refreshing) back() }
    LaunchedEffect(options) {
        // Empty options during refresh must not discard the saved choice.
        if (options.isNotEmpty()) {
            if (recoveryId != null && retainedSelection == null) pageName = IdentitySetupPage.Summary.name
            select(selected!!)
        }
    }
    // A failed operation belongs to the configuration that produced it, not every customization page.
    val selectedConfiguration = selected?.let { listOf(it.recovery.id, it.storage.id, it.approval.id) } ?: emptyList()
    val failedConfiguration = rememberSaveable(warning) { selectedConfiguration }
    val latestFailedConfiguration by rememberUpdatedState(failedConfiguration)
    val latestSelectedConfiguration by rememberUpdatedState(selectedConfiguration)
    val savedPages = rememberSaveableStateHolder()
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val pages = listOf(IdentitySetupPage.Summary.name) + if (page == IdentitySetupPage.Summary) emptyList() else listOf(page.name)
    Surface(Modifier.fillMaxSize().safeDrawingPadding().testTag(WalletUiTestTags.IdentitySetup)) {
        NavDisplay(pages, onBack = { if (!refreshing) back() }, entryDecorators = emptyList(),
            transitionSpec = { walletNavigationMotion(true, reduceMotion, rtl) },
            popTransitionSpec = { walletNavigationMotion(false, reduceMotion, rtl) },
            predictivePopTransitionSpec = { _ -> walletNavigationMotion(false, reduceMotion, rtl) },
        ) { key ->
            NavEntry(key) {
                val setup = latestSetup
                val options = latestOptions
                val selected = latestSelected
                val progress = latestProgress
                val refreshing = progress != null
                val displayedPage = IdentitySetupPage.valueOf(key)
                val displayedStep = displayedPage.choiceStep
                val summary = displayedPage == IdentitySetupPage.Summary
                val visibleWarning = latestWarning?.takeIf { summary && latestFailedConfiguration == latestSelectedConfiguration }
                savedPages.SaveableStateProvider(key) {
                    ReviewScaffold(
                        modifier = Modifier.fillMaxSize().walletNavigationBackground().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 640.dp),
                        header = { WalletScreenHeader(if (summary) stringResource(Res.string.setup_title) else displayedStep!!.title) },
                        feedback = if (summary && (refreshing || visibleWarning != null)) ({
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                Text(progress ?: visibleWarning.orEmpty(), style = MaterialTheme.typography.bodyMedium,
                                    color = if (!refreshing && visibleWarning != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }) else null,
                        actions = {
                            when (setup) {
                                is WalletDemoIdentitySetup.Pending -> WalletActions(
                                    primary = if (setup.canRetry) WalletAction(stringResource(Res.string.setup_retry),
                                        { onResume(setup.identityId) }, !refreshing) else null,
                                    secondary = WalletAction(stringResource(Res.string.setup_cancel_pending),
                                        { onCancel(setup.identityId) }, !refreshing),
                                )
                                is WalletDemoIdentitySetup.Choose -> WalletActions(
                                    primary = if (selected != null) WalletAction(
                                        label = when {
                                            !summary -> stringResource(Res.string.issuance_done)
                                            visibleWarning != null -> stringResource(Res.string.settings_try_again)
                                            selected.restoring -> stringResource(Res.string.setup_restore)
                                            else -> stringResource(Res.string.setup_create)
                                        },
                                        onClick = { if (summary) onChoose(selected.id) else back() },
                                        enabled = !refreshing, testTag = WalletUiTestTags.KeySetupContinue,
                                    ) else if (!refreshing) WalletAction(stringResource(Res.string.settings_try_again), onRefresh) else null,
                                    secondary = if (!summary) WalletAction(stringResource(Res.string.settings_back), ::back, !refreshing) else null,
                                )
                            }
                        },
                    ) {
                        when (setup) {
                            is WalletDemoIdentitySetup.Pending -> {
                                Text(setup.explanation)
                                Text(stringResource(Res.string.setup_cancel_notice), style = MaterialTheme.typography.bodyMedium)
                            }
                            is WalletDemoIdentitySetup.Choose -> {
                                if (summary) {
                                    Text(stringResource(Res.string.setup_intro), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    setup.message?.let { Text(it) }
                                    if (selected != null) WalletSection(stringResource(Res.string.setup_summary), selected.recovery.detail) {
                                        SigningKeySummary(
                                            if (selected.recovery.id == "new") stringResource(Res.string.setup_no_backup) else selected.recovery.title,
                                            selected.storage.title, selected.approval.title, enabled = !refreshing,
                                            onEdit = { selectedStep -> pageName = IdentitySetupPage.entries.first { it.choiceStep == selectedStep }.name },
                                        )
                                    } else if (!refreshing) {
                                        Text(stringResource(Res.string.setup_no_options))
                                        setup.recoveryUnavailableReasons.distinct().forEach { SettingsNotice(it) }
                                    }
                                } else if (selected != null && displayedStep != null) {
                                    Text(stringResource(when (displayedStep) {
                                        WalletDemoKeySetupStep.Recovery -> Res.string.setup_recovery_description
                                        WalletDemoKeySetupStep.Storage -> Res.string.setup_storage_description
                                        WalletDemoKeySetupStep.Approval -> Res.string.setup_approval_description
                                    }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (displayedStep == WalletDemoKeySetupStep.Storage && selected.recovery.id != "new") {
                                        setup.recoveryStorageNotice?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                                    }
                                    val choices = displayedStep.options(options, selected).map(displayedStep::choice).distinctBy { it.id }
                                    val groups = if (displayedStep == WalletDemoKeySetupStep.Recovery)
                                        choices.groupBy { it.id.startsWith("restore:") }.values.toList() else listOf(choices)
                                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                                        groups.forEach { group ->
                                            WalletSection(title = if (displayedStep == WalletDemoKeySetupStep.Recovery)
                                                stringResource(if (group.first().id.startsWith("restore:")) Res.string.setup_restore_group else Res.string.setup_create_group)
                                                else null,
                                                footer = if (choices.size == 1) stringResource(Res.string.setup_single_option) else null) {
                                                group.forEachIndexed { index, choice ->
                                                    if (index > 0) SettingsDivider()
                                                    SettingsChoiceRow(choice.title, choice.detail, displayedStep.choice(selected).id == choice.id,
                                                        onSelect = { select(displayedStep.select(options, selected, choice.id)) },
                                                        modifier = Modifier.testTag(WalletUiTestTags.keySetupChoice(displayedStep.name, choices.indexOf(choice))),
                                                        enabled = !refreshing, selectable = choices.size > 1,
                                                        extra = choice.identifier?.let { identifier -> { RecoveryIdentifier(identifier) } })
                                                }
                                            }
                                        }
                                    }
                                    if (displayedStep == WalletDemoKeySetupStep.Recovery && setup.recoveryUnavailableReasons.isNotEmpty()) {
                                        ProviderAvailability(setup.recoveryUnavailableReasons, !refreshing, onRefresh)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class IdentitySetupPage(val choiceStep: WalletDemoKeySetupStep?) {
    Summary(null), Recovery(WalletDemoKeySetupStep.Recovery), Storage(WalletDemoKeySetupStep.Storage),
    Approval(WalletDemoKeySetupStep.Approval),
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
    WalletSection(stringResource(Res.string.setup_backup_availability)) {
        reasons.distinct().forEach { SettingsNotice(it) }
        SettingsActionRow(stringResource(Res.string.setup_check_again), onRefresh, enabled = enabled)
    }
}
