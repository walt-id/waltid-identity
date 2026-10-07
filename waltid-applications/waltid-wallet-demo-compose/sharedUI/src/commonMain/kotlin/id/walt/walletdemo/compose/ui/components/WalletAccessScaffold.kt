package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One owner for system/keyboard insets. Short windows scroll without moving the editor on errors. */
@Composable
internal fun WalletAccessScaffold(
    header: @Composable () -> Unit,
    input: @Composable () -> Unit,
    feedback: @Composable () -> Unit,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight).wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp).padding(horizontal = 20.dp, vertical = 12.dp)) {
                header()
                Column(Modifier.weight(1f).fillMaxWidth().heightIn(min = 168.dp),
                    verticalArrangement = Arrangement.Center) {
                    input()
                    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(top = 12.dp), contentAlignment = Alignment.TopCenter) {
                        feedback()
                    }
                }
                actions()
            }
        }
    }
}
