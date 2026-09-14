package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Review chrome for receive and share: details scroll, actions stay pinned.
 *
 * @param fillViewport When true, the scaffold occupies the host (in-app tabs). When false, it wraps
 * the review and only grows as tall as the heading, credential, and actions — the Digital Credentials
 * trays — while still scrolling if that content would overflow the screen.
 */
@Composable
internal fun ReviewScaffold(
    modifier: Modifier = Modifier,
    fillViewport: Boolean = true,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val body: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .then(
                    if (fillViewport) {
                        Modifier.weight(1f, fill = true).heightIn(min = 240.dp)
                    } else {
                        Modifier.weight(1f, fill = false)
                    },
                )
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
        if (actions != null) {
            HorizontalDivider()
            Surface(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    actions()
                }
            }
        }
    }

    if (fillViewport) {
        Column(modifier = modifier.fillMaxSize(), content = body)
    } else {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
                content = body,
            )
        }
    }
}
