package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
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
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.transactionIdentifier }) { entry ->
                    TransactionLogEntryCard(
                        entry = entry,
                        expanded = entry.transactionIdentifier in state.expandedIds,
                        onToggle = { controller.toggleTransactionLogEntry(entry.transactionIdentifier) },
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
}

@Composable
private fun TransactionLogFilterRow(
    selected: WalletDemoTransactionLogFilter,
    onSelect: (WalletDemoTransactionLogFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScrollPadding(),
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
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("transactionLog.entry"),
    ) {
        Column(
            Modifier.clickable(onClick = onToggle).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(entry.type.labelResource()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TransactionLogResultBadge(entry.result)
            }
            Text(
                entry.relyingPartyName ?: stringResource(Res.string.transaction_log_unknown_party),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                entry.time,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded && entry.details.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                entry.details.forEach { detail ->
                    Column {
                        Text(
                            detail.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(detail.value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionLogResultBadge(result: WalletDemoTransactionLogResult) {
    val completed = result == WalletDemoTransactionLogResult.Completed
    val container = if (completed) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (completed) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(
            stringResource(result.labelResource()),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
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

private fun Modifier.horizontalScrollPadding(): Modifier = this.padding(horizontal = 16.dp, vertical = 8.dp)

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
