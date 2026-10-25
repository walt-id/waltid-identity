package id.walt.walletdemo.compose.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import id.walt.walletdemo.compose.ui.components.WalletMotion
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TransactionLogScreen(controller: WalletDemoController, state: WalletDemoTransactionLogState) {
    LaunchedEffect(controller) { controller.openTransactionLog() }
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= layoutInfo.totalItemsCount - 3
        }
    }
    LaunchedEffect(shouldLoadMore, state.canLoadMore) {
        if (shouldLoadMore && state.canLoadMore) controller.loadMoreTransactionLog()
    }

    var pendingDelete by remember { mutableStateOf<WalletDemoTransactionLogEntry?>(null) }

    Column(Modifier.fillMaxSize().testTag("transactionLog.screen")) {
        TransactionLogFilterRow(state.filter, controller::setTransactionLogFilter)
        val visible = state.visibleEntries
        when {
            state.error != null && state.entries.isEmpty() ->
                TransactionLogMessage(stringResource(Res.string.transaction_log_error)) {
                    Button(onClick = controller::refreshTransactionLog) {
                        Text(stringResource(Res.string.transaction_log_retry))
                    }
                }

            state.isLoading && state.entries.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

            visible.isEmpty() && state.loaded ->
                TransactionLogMessage(stringResource(Res.string.transaction_log_empty))

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visible, key = { it.transactionIdentifier }) { entry ->
                    TransactionLogEntryCard(
                        entry = entry,
                        expanded = entry.transactionIdentifier in state.expandedIds,
                        onToggle = { controller.toggleTransactionLogEntry(entry.transactionIdentifier) },
                        onDelete = { pendingDelete = entry },
                    )
                }
                if (state.isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.nextCursor != null) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                            TextButton(onClick = controller::loadMoreTransactionLog) {
                                Text(stringResource(Res.string.transaction_log_load_more))
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(Res.string.transaction_log_delete_question)) },
            text = { Text(stringResource(Res.string.transaction_log_delete_description)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        controller.deleteTransactionLogEntry(entry.transactionIdentifier)
                    },
                    modifier = Modifier.testTag("transactionLog.delete.confirm"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(Res.string.transaction_log_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(Res.string.transaction_log_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun TransactionLogFilterRow(
    selected: WalletDemoTransactionLogFilter,
    onSelect: (WalletDemoTransactionLogFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WalletDemoTransactionLogFilter.entries.forEach { filter ->
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelResource())) },
                modifier = Modifier.testTag("transactionLog.filter.${filter.name}"),
            )
        }
    }
}

@Composable
private fun TransactionLogEntryCard(
    entry: WalletDemoTransactionLogEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    val transition = updateTransition(expanded, label = "transactionLog-expansion")
    val chevronRotation by transition.animateFloat(
        transitionSpec = { WalletMotion.disclosure(reduceMotion) },
        label = "transactionLog-chevron",
    ) { if (it) 180f else 0f }
    val hasDetails = entry.details.isNotEmpty()

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().testTag("transactionLog.entry"),
    ) {
        Column(
            Modifier.clickable(onClick = onToggle),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TransactionLogTypeBadge(entry.type, entry.result)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(entry.type.labelResource()),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        entry.relyingPartyName ?: stringResource(Res.string.transaction_log_unknown_party),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        entry.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TransactionLogResultBadge(entry.result)
                    if (hasDetails) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(chevronRotation),
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (hasDetails) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        entry.details.forEach { detail ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    detail.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(detail.value, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth().testTag("transactionLog.entry.delete"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    ) {
                        Icon(Icons.Filled.Delete, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.transaction_log_delete))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionLogTypeBadge(
    type: WalletDemoTransactionLogType,
    result: WalletDemoTransactionLogResult,
) {
    val failed = result == WalletDemoTransactionLogResult.NotCompleted
    val container = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val content = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(type.iconVector(), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TransactionLogResultBadge(result: WalletDemoTransactionLogResult) {
    val completed = result == WalletDemoTransactionLogResult.Completed
    val container = if (completed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (completed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (completed) Icons.Filled.Check else Icons.Filled.Warning,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(12.dp),
            )
            Text(
                stringResource(result.labelResource()),
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
    }
}

@Composable
private fun TransactionLogMessage(message: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.invoke()
    }
}

private fun WalletDemoTransactionLogType.iconVector(): ImageVector = when (this) {
    WalletDemoTransactionLogType.Presentation -> Icons.Filled.Share
    WalletDemoTransactionLogType.Issuance -> Icons.Filled.Add
    WalletDemoTransactionLogType.Deletion -> Icons.Filled.Delete
}

private fun WalletDemoTransactionLogFilter.labelResource(): StringResource = when (this) {
    WalletDemoTransactionLogFilter.All -> Res.string.transaction_log_filter_all
    WalletDemoTransactionLogFilter.Presentation -> Res.string.transaction_log_filter_presentation
    WalletDemoTransactionLogFilter.Issuance -> Res.string.transaction_log_filter_issuance
    WalletDemoTransactionLogFilter.Deletion -> Res.string.transaction_log_filter_deletion
}

private fun WalletDemoTransactionLogType.labelResource(): StringResource = when (this) {
    WalletDemoTransactionLogType.Presentation -> Res.string.transaction_log_type_presentation
    WalletDemoTransactionLogType.Issuance -> Res.string.transaction_log_type_issuance
    WalletDemoTransactionLogType.Deletion -> Res.string.transaction_log_type_deletion
}

private fun WalletDemoTransactionLogResult.labelResource(): StringResource = when (this) {
    WalletDemoTransactionLogResult.Completed -> Res.string.transaction_log_result_completed
    WalletDemoTransactionLogResult.NotCompleted -> Res.string.transaction_log_result_not_completed
}
