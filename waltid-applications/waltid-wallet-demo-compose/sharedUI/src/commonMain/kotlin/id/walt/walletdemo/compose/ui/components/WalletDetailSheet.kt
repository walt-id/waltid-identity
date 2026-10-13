package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletReviewHost
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** A detail destination owns scrolling and Back; leaving it never changes the flow's selection. */
@Composable
internal fun WalletDetailSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    closeTag: String = "wallet-detail-close",
    onBack: (() -> Unit)? = null,
    pagePath: List<String> = listOf(title),
    content: @Composable ColumnScope.(String) -> Unit,
) {
    WalletReviewHost(dismissEnabled = true, onDismiss = onDismiss) {
        val sheetMotion = rememberWalletSheetNavigationMotion()
        // Back navigates within the information stack; X/outside dismissal closes the adapter.
        SystemBackHandler(enabled = onBack != null) { onBack?.invoke() }
        Column(modifier.fillMaxWidth()) {
            WalletScreenHeader(title,
                leading = {
                    onBack?.let { back ->
                        WalletHeaderButton(onClick = back, modifier = Modifier.testTag("wallet-detail-back")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.credential_back))
                        }
                    }
                },
                trailing = {
                    WalletHeaderButton(onClick = onDismiss, modifier = Modifier.testTag(closeTag)) {
                        WalletIcon(WalletSymbol.Decline, stringResource(Res.string.credential_close_details))
                    }
                })
            val savedPages = rememberSaveableStateHolder()
            NavDisplay(pagePath, modifier = Modifier.weight(1f, fill = false), onBack = { (onBack ?: onDismiss)() },
                entryDecorators = emptyList(),
                sizeTransform = walletNavigationSizeTransform(reduceMotion = true),
                transitionSpec = { sheetMotion(true) },
                popTransitionSpec = { sheetMotion(false) },
                predictivePopTransitionSpec = { _ -> sheetMotion(false) },
            ) { pageKey ->
                NavEntry(pageKey) {
                    savedPages.SaveableStateProvider(pageKey) {
                        Column(Modifier.fillMaxWidth().walletNavigationBackground().verticalScroll(rememberScrollState())
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)).padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)) { content(pageKey) }
                    }
                }
            }
        }
    }
}
