package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import id.walt.walletdemo.compose.ui.resources.*
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import org.jetbrains.compose.resources.stringResource

/** One accessible expansion target; animation never changes the underlying claim state. */
@Composable
internal fun MetadataDisclosure(
    title: String,
    initiallyExpanded: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val stateLabel = stringResource(if (expanded) Res.string.metadata_expanded else Res.string.metadata_collapsed)
    val duration = if (LocalWalletVisualPreferences.current.reduceMotion) 0 else 180
    val expandedRotation = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -90f else 90f
    val rotation by animateFloatAsState(if (expanded) expandedRotation else 0f, tween(duration), label = "disclosure-chevron")

    Column {
        Row(
            modifier = modifier.fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .semantics(mergeDescendants = true) {
                    heading()
                    text = AnnotatedString(title)
                    stateDescription = stateLabel
                }
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, Modifier.weight(1f).clearAndSetSemantics {}, style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                modifier = Modifier.rotate(rotation), tint = MaterialTheme.colorScheme.primary)
        }
        AnimatedVisibility(expanded,
            enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
            exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}
