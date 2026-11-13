package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.WalletUiTestTags

private class ClaimInteraction(val enabled: Boolean, val readOnly: Boolean,
    val toggle: (WalletDemoPresentationDisclosureSelection) -> Unit)

@Composable
internal fun EffectiveSharingInformationSection(
    review: WalletDemoClaimReview,
    enabled: Boolean,
    readOnly: Boolean,
    onToggleDisclosure: (WalletDemoPresentationDisclosureSelection) -> Unit,
    onRetry: () -> Unit,
) {
    val updating = review is WalletDemoClaimReview.Loading
    val visible = when (review) {
        is WalletDemoClaimReview.Ready -> review
        is WalletDemoClaimReview.Loading -> review.previous
        else -> null
    }
    val interaction = ClaimInteraction(enabled && !updating, readOnly, onToggleDisclosure)
    WalletSection(title = "Information to share", modifier = Modifier.testTag("review-information-to-share")) {
        if (updating) Text(if (visible == null) "Updating information…" else "Updating information… Previous review shown below.", Modifier.padding(16.dp))
        if (review is WalletDemoClaimReview.Failed) Column(Modifier.padding(16.dp)) {
            Text("Couldn't update the information to share.", color = MaterialTheme.colorScheme.error)
            Text(review.message, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRetry, enabled = enabled) { Text("Try again") }
        }
        // Keep the same composition and original source identities during recomputation.
        // The prior tree is explicitly labelled, controls are disabled, and Share remains blocked.
        if (visible != null) visible.groups.forEach { group -> key(group.credentialId) {
            ReviewInformationGroup(group.summary.title) {
                group.contributions.forEachIndexed { index, contribution -> key(contribution.id) {
                    if (group.contributions.size > 1) Text("Selected requirement ${index + 1}", style = MaterialTheme.typography.titleSmall)
                    val detachedChoices = group.choices.filter { it.selection.queryId in contribution.queries }
                        .filterNot { choice -> contribution.nodes.containsChoice(choice.selection) }
                    if (contribution.separatesAlwaysShared) {
                        val ordinary = contribution.nodes.filterNot { it.technical }
                        val always = ordinary.sharingSection(alwaysShared = true)
                        val requested = ordinary.sharingSection(alwaysShared = false)
                        if (always.isNotEmpty()) {
                            SharingCategory("Always shared", "review-always-shared") {
                                EffectiveClaimChildren(always, interaction)
                            }
                        }
                        if (requested.isNotEmpty() || detachedChoices.isNotEmpty()) {
                            SharingCategory("Requested information", "review-requested-information") {
                                detachedChoices.forEach { choice -> key(choice.selection.id) {
                                    ClaimChoice(choice, interaction, showValue = !choice.included)
                                } }
                                EffectiveClaimChildren(requested, interaction)
                            }
                        }
                        EffectiveClaimChildren(contribution.nodes.filter { it.technical }.map { it.withoutSharingReasons() }, interaction)
                    } else {
                        if (contribution.wholeCredential) Text("This credential is shared in full", style = MaterialTheme.typography.bodyMedium)
                        detachedChoices.forEach { choice -> key(choice.selection.id) {
                            ClaimChoice(choice, interaction, showValue = !choice.included)
                        } }
                        EffectiveClaimChildren(contribution.nodes, interaction)
                    }
                } }
            }
        } }
    }
}

@Composable
private fun SharingCategory(title: String, tag: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(tag)) {
        MetadataDisclosure(title, initiallyExpanded = true,
            indicatorColor = MaterialTheme.colorScheme.onSurfaceVariant) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

private fun List<SharingClaimNode>.containsChoice(selection: WalletDemoPresentationDisclosureSelection): Boolean =
    any { it.choice?.selection == selection || it.children.containsChoice(selection) }
private fun SharingClaimNode.withoutSharingReasons(): SharingClaimNode =
    copy(reason = null, children = children.map { it.withoutSharingReasons() })

private fun SharingClaimChoice.statusText(): String = forcedReason ?: when {
    selected -> "Included"
    included -> "Not selected; some information is already shared"
    else -> "Not shared"
}

@Composable
private fun ClaimChoice(choice: SharingClaimChoice, interaction: ClaimInteraction, showValue: Boolean = false,
                        showLabel: Boolean = true, showStatus: Boolean = true) {
    val status = choice.statusText()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag(WalletUiTestTags.presentationDisclosure(choice.selection.id))) {
        if (choice.forcedReason == null && !interaction.readOnly) Checkbox(choice.selected,
            enabled = interaction.enabled, onCheckedChange = { interaction.toggle(choice.selection) },
            modifier = Modifier.testTag(WalletUiTestTags.presentationDisclosureToggle(choice.selection.id)).semantics {
                contentDescription = choice.item.label
                stateDescription = status
            })
        Column(Modifier.weight(1f)) {
            Text(if (showLabel) choice.item.label else "Include this information", style = MaterialTheme.typography.titleSmall)
            if (showStatus) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showValue && !choice.selected) ClaimValue(choice.item.value, choice.item.path)
        }
    }
}

@Composable
private fun EffectiveClaimChildren(children: List<SharingClaimNode>, interaction: ClaimInteraction, technicalContext: Boolean = false) {
    val ordinary = if (technicalContext) children else children.filterNot { it.technical }
    val technical = if (technicalContext) emptyList() else children.filter { it.technical }
    var count by rememberSaveable(children.map { it.path.id }) { mutableStateOf(25) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ordinary.take(count).forEach { child -> key(child.path.id) { EffectiveClaim(child, interaction, technicalContext = technicalContext) } }
        if (ordinary.size > count) TextButton(onClick = { count += 25 }) { Text("Show more") }
        if (technical.isNotEmpty()) MetadataDisclosure("Technical details",
            initiallyExpanded = technical.any { it.initiallyExpanded || it.choice != null }) {
            technical.forEach { child -> key(child.path.id) { EffectiveClaim(child, interaction, technicalContext = true) } }
        }
    }
}

@Composable
private fun EffectiveClaim(node: SharingClaimNode, interaction: ClaimInteraction, showLabel: Boolean = true, technicalContext: Boolean = false) {
    val choice = node.choice
    Column(Modifier.testTag(WalletUiTestTags.claim(node.path.id)), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (node.kind) {
            SharingClaimNode.Kind.Scalar -> {
                if (choice != null) ClaimChoice(choice, interaction)
                else if (showLabel) Text(node.label, style = MaterialTheme.typography.titleSmall)
                ClaimValue(requireNotNull(node.value), node.path)
            }
            SharingClaimNode.Kind.Object -> {
                val body: @Composable () -> Unit = {
                    node.choice?.let { ClaimChoice(it, interaction, showLabel = false, showStatus = false) }
                    if (node.children.isEmpty() && !node.contextOnly) Text("No fields")
                    else EffectiveClaimChildren(node.children, interaction, technicalContext)
                }
                val status = choice?.statusText() ?: node.reason
                if (showLabel && node.label.isNotBlank()) MetadataDisclosure(node.label, initiallyExpanded = node.initiallyExpanded,
                    supportingText = status, content = body)
                else {
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    body()
                }
            }
            SharingClaimNode.Kind.Array -> {
                val body: @Composable () -> Unit = {
                    node.choice?.let { ClaimChoice(it, interaction, showLabel = false, showStatus = false) }
                    if (node.children.isEmpty() && !node.contextOnly) Text("No items")
                    val scalarOnly = !node.arrayUsesCards && node.children.all { it.kind == SharingClaimNode.Kind.Scalar }
                    var count by rememberSaveable(node.path.id) { mutableStateOf(25) }
                    node.children.take(count).forEach { child -> key(child.path.id) {
                        if (scalarOnly) EffectiveClaim(child, interaction,
                            showLabel = child.label.isNotBlank(), technicalContext = technicalContext)
                        else Column(Modifier.fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).padding(12.dp)) {
                            EffectiveClaim(child, interaction, showLabel = child.label.isNotBlank(), technicalContext = technicalContext)
                        }
                    } }
                    if (node.children.size > count) TextButton(onClick = { count += 25 }) { Text("Show more") }
                }
                val status = choice?.statusText() ?: node.reason
                if (showLabel && node.label.isNotBlank()) MetadataDisclosure(node.label, initiallyExpanded = node.initiallyExpanded,
                    supportingText = status, content = body)
                else {
                    status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    body()
                }
            }
        }
        if (node.kind == SharingClaimNode.Kind.Scalar && choice == null) node.reason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
