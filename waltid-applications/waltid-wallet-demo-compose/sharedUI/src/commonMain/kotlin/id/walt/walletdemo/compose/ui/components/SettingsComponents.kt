package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.ui.resources.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val readableJson = Json { prettyPrint = true }

@Composable
internal fun SettingsSection(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        title?.let {
            Text(it, Modifier.padding(horizontal = 16.dp).semantics { heading() },
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.fillMaxWidth(), content = content)
        }
        footer?.let {
            Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun SettingsDivider() = HorizontalDivider(
    Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
internal fun SettingsSymbol(resource: DrawableResource) {
    Icon(painterResource(resource), contentDescription = null, modifier = Modifier.size(24.dp))
}

@Composable
internal fun SettingsNavigationRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        leadingContent = icon,
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick),
    )
}

@Composable
internal fun SettingsActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    val tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        leadingContent = icon,
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            headlineColor = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
            leadingIconColor = if (enabled) tint else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f),
        ),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    )
}

@Composable
internal fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = detail?.let { { Text(it) } },
        trailingContent = { Switch(checked, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    )
}

@Composable
internal fun SettingsChoiceRow(
    title: String,
    detail: String?,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectable: Boolean = true,
    extra: (@Composable () -> Unit)? = null,
) {
    val selection = if (selectable) Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect) else Modifier
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (detail != null || extra != null) ({
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                detail?.let { Text(it) }
                extra?.invoke()
            }
        }) else null,
        trailingContent = if (selectable) ({ RadioButton(selected, onClick = null, enabled = enabled) }) else null,
        colors = ListItemDefaults.colors(containerColor = if (selected && selectable)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f) else MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).then(selection),
    )
}

@Composable
internal fun SettingsDetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun SettingsNotice(message: String, error: Boolean = false, modifier: Modifier = Modifier) {
    Text(message, modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    val showTooltip = stringResource(Res.string.settings_show_tooltip)
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = tooltip,
        // Expose one action: Material's separate wrapper otherwise becomes an empty iOS focus target.
        modifier = modifier.semantics(mergeDescendants = true) {}.clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            if (enabled) this.onClick { onClick(); true } else disabled()
            onLongClick(label = showTooltip) { scope.launch { tooltip.show() }; true }
        },
    ) {
        IconButton(onClick = onClick, enabled = enabled, content = content)
    }
}

@Composable
internal fun SettingsCopyRow(
    title: String,
    value: String?,
    valueTag: String,
    copyTag: String,
    copyLabel: String,
    copyAnnouncement: String,
    disclosureLabels: Pair<String, String>? = null,
    formatJson: Boolean = false,
) {
    val clipboard = LocalClipboardManager.current
    val displayValue = remember(value, formatJson) {
        if (formatJson && value != null) runCatching {
            readableJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(value))
        }.getOrDefault(value) else value
    }
    var expanded by rememberSaveable { mutableStateOf(disclosureLabels == null) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(2_000); copied = false } }
    SettingsSection {
        ListItem(
            headlineContent = { Text(title) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            trailingContent = {
                Row {
                    if (disclosureLabels != null) IconButton(onClick = { expanded = !expanded }) {
                        Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            if (expanded) disclosureLabels.second else disclosureLabels.first)
                    }
                    SettingsIconButton(copyLabel, enabled = !value.isNullOrBlank(), onClick = {
                        value?.let { clipboard.setText(AnnotatedString(it)); copied = true }
                    }, modifier = Modifier.testTag(copyTag)) {
                        Icon(painterResource(Res.drawable.settings_copy), contentDescription = null)
                    }
                }
            },
        )
        if (expanded) SelectionContainer {
            Text(displayValue?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.settings_unavailable),
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp).testTag(valueTag),
                style = MaterialTheme.typography.bodySmall)
        }
        if (copied) Text(stringResource(Res.string.settings_copied),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite; contentDescription = copyAnnouncement },
            style = MaterialTheme.typography.labelMedium)
    }
}
