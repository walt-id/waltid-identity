package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The active container owns this header; content contributes sections and credential identity. */
@Composable
internal fun WalletScreenHeader(
    title: String?,
    modifier: Modifier = Modifier,
    titleTag: String = "wallet.screen.title",
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().testTag("wallet.screen.header")
        .padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        leading?.invoke()
        if (title != null) Text(title, Modifier.weight(1f).testTag(titleTag).semantics { heading() },
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        else Spacer(Modifier.weight(1f))
        trailing()
    }
}

/** Quiet utility actions share a circular surface; Material retains the minimum touch target. */
@Composable
internal fun WalletHeaderButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    FilledTonalIconButton(onClick, modifier.size(40.dp), enabled = enabled, shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ), content = content)
}
