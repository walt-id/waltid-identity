package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import id.walt.walletdemo.compose.ui.exportTestTagsForPlatformAutomation
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
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val predictivePop = defaultPredictivePopTransitionSpec<String>()
    Dialog(onDismissRequest = { (onBack ?: onDismiss)() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight(.9f)
                .padding(12.dp).exportTestTagsForPlatformAutomation(),
            color = MaterialTheme.colorScheme.background,
            shape = MaterialTheme.shapes.large,
        ) {
            Column {
                WalletScreenHeader(title,
                    leading = {
                        onBack?.let { back ->
                            IconButton(onClick = back, modifier = Modifier.testTag("wallet-detail-back")) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.credential_back))
                            }
                        }
                    },
                    trailing = {
                        IconButton(onClick = onDismiss, modifier = Modifier.testTag(closeTag)) {
                            WalletIcon(WalletSymbol.Decline, stringResource(Res.string.credential_close_details))
                        }
                    })
                val savedPages = rememberSaveableStateHolder()
                NavDisplay(pagePath, modifier = Modifier.weight(1f), onBack = { (onBack ?: onDismiss)() },
                    entryDecorators = emptyList(),
                    transitionSpec = { walletNavigationMotion(true, reduceMotion, rtl) },
                    popTransitionSpec = { walletNavigationMotion(false, reduceMotion, rtl) },
                    predictivePopTransitionSpec = if (reduceMotion) ({ _ -> walletNavigationMotion(false, true, rtl) }) else predictivePop,
                ) { pageKey ->
                    NavEntry(pageKey) {
                        savedPages.SaveableStateProvider(pageKey) {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)) { content(pageKey) }
                        }
                    }
                }
            }
        }
    }
}
