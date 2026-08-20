package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import id.walt.walletdemo.compose.logic.WalletDemoReviewIsland
import id.walt.walletdemo.compose.logic.WalletDemoReviewIslandId
import id.walt.walletdemo.compose.logic.WalletDemoReviewIslandKind
import id.walt.walletdemo.compose.logic.WalletDemoReviewRoute
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags

/**
 * Horizontally navigates between a surface summary and an island-specific technical page.
 *
 * The route is local to [reviewKey], so starting a different interaction clears technical
 * navigation. Summary and technical scroll positions are independent; returning from details
 * therefore restores the exact summary position and each island's saveable expansion state.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun ReviewIslandNavigationHost(
    reviewKey: Any,
    islands: List<WalletDemoReviewIsland>,
    modifier: Modifier = Modifier,
    scrollContent: Boolean = false,
    contentPadding: androidx.compose.foundation.layout.PaddingValues =
        androidx.compose.foundation.layout.PaddingValues(0.dp),
    islandModifier: (WalletDemoReviewIsland) -> Modifier = { Modifier },
    showModelExpandedValues: (WalletDemoReviewIsland) -> Boolean = { true },
    islandExpandedContent: @Composable (WalletDemoReviewIsland) -> Unit = {},
) {
    var technicalIslandId by rememberSaveable(reviewKey) { mutableStateOf<String?>(null) }
    val route: WalletDemoReviewRoute = technicalIslandId
        ?.let { WalletDemoReviewRoute.TechnicalDetails(WalletDemoReviewIslandId(it)) }
        ?: WalletDemoReviewRoute.Summary
    val summaryScrollState = rememberScrollState()
    val technicalScrollState = rememberScrollState()

    SystemBackHandler(enabled = route is WalletDemoReviewRoute.TechnicalDetails) {
        technicalIslandId = null
    }

    AnimatedContent(
        targetState = route,
        transitionSpec = { reviewRouteTransition(initialState, targetState) },
        contentKey = { currentRoute -> currentRoute::class },
        modifier = modifier,
        label = "Review technical navigation",
    ) { currentRoute ->
        when (currentRoute) {
            WalletDemoReviewRoute.Summary -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (scrollContent) Modifier.verticalScroll(summaryScrollState) else Modifier)
                        .padding(contentPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    islands.forEach { island ->
                        ReviewIslandCard(
                            island = island,
                            onTechnicalDetails = { technicalIslandId = island.id.value },
                            modifier = islandModifier(island),
                            showModelExpandedValues = showModelExpandedValues(island),
                        ) {
                            islandExpandedContent(island)
                        }
                    }
                }
            }

            is WalletDemoReviewRoute.TechnicalDetails -> {
                val island = islands.firstOrNull { it.id == currentRoute.islandId }
                if (island == null) {
                    technicalIslandId = null
                } else {
                    ReviewIslandTechnicalPage(
                        island = island,
                        onBack = { technicalIslandId = null },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (scrollContent) Modifier.verticalScroll(technicalScrollState) else Modifier)
                            .padding(contentPadding),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ReviewIslandCard(
    island: WalletDemoReviewIsland,
    onTechnicalDetails: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showModelExpandedValues: Boolean = true,
    expandedContent: @Composable () -> Unit = {},
) {
    var expanded by rememberSaveable(island.id.value) { mutableStateOf(island.initiallyExpanded) }
    val islandColors = reviewIslandColors(island.kind)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) islandColors.accent.copy(alpha = 0.42f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.testTag(WalletUiTestTags.reviewIsland(island.id.value))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { expanded = !expanded }
                    .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                    .testTag(WalletUiTestTags.reviewIslandToggle(island.id.value))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReviewIslandVisual(island, islandColors)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = island.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    island.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse ${island.title}" else "Expand ${island.title}",
                    tint = islandColors.accent,
                )
            }

            if (island.visibleSummaryValues.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReviewValueList(
                    values = island.visibleSummaryValues,
                    modifier = Modifier.padding(12.dp),
                )
            }

            if (expanded) {
                val hasExpandedContent = (showModelExpandedValues && island.visibleExpandedValues.isNotEmpty()) ||
                    island.status?.isVisible == true || island.warning != null || island.hasTechnicalDetails
                if (hasExpandedContent) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    island.warning?.let { warning ->
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    island.status?.takeIf { it.isVisible }?.let { status ->
                        ReviewValueList(values = listOf(status))
                    }
                    if (showModelExpandedValues && island.visibleExpandedValues.isNotEmpty()) {
                        ReviewValueList(values = island.visibleExpandedValues)
                    }
                    expandedContent()
                    if (island.hasTechnicalDetails && onTechnicalDetails != null) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(role = Role.Button, onClick = onTechnicalDetails)
                                .testTag(WalletUiTestTags.reviewIslandTechnicalDetails(island.id.value))
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Technical details",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewIslandVisual(island: WalletDemoReviewIsland, colors: ReviewIslandColors) {
    val visual = island.visual
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        val imageUri = visual?.imageUri?.takeIf(::isSafeReviewImage)
        if (imageUri != null) {
            SubcomposeAsyncImage(
                model = imageUri,
                contentDescription = visual.contentDescription ?: "${island.title} image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                loading = { ReviewVisualFallback(visual.fallbackText, colors.accent) },
                error = { ReviewVisualFallback(visual.fallbackText, colors.accent) },
            )
        } else {
            ReviewVisualFallback(visual?.fallbackText ?: island.title.take(1), colors.accent)
        }
    }
}

@Composable
private fun ReviewVisualFallback(text: String, color: Color) {
    Text(
        text = text.take(2),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

private data class ReviewIslandColors(val accent: Color, val container: Color)

@Composable
private fun reviewIslandColors(kind: WalletDemoReviewIslandKind): ReviewIslandColors {
    val accent = when (kind) {
        WalletDemoReviewIslandKind.Issuer,
        WalletDemoReviewIslandKind.Verifier,
        -> if (isSystemInDarkTheme()) Color(0xFF93C5FD) else Color(0xFF2563EB)

        WalletDemoReviewIslandKind.Credential ->
            if (isSystemInDarkTheme()) Color(0xFFC4B5FD) else Color(0xFF7C3AED)

        WalletDemoReviewIslandKind.Information ->
            if (isSystemInDarkTheme()) Color(0xFF67E8F9) else Color(0xFF0E7490)

        WalletDemoReviewIslandKind.ValidityAndStatus ->
            if (isSystemInDarkTheme()) Color(0xFF6EE7B7) else Color(0xFF047857)

        WalletDemoReviewIslandKind.PurposeAndTransaction ->
            if (isSystemInDarkTheme()) Color(0xFFFCD34D) else Color(0xFFB45309)

        WalletDemoReviewIslandKind.RequiredAction ->
            if (isSystemInDarkTheme()) Color(0xFFA5B4FC) else Color(0xFF4F46E5)
    }
    return ReviewIslandColors(accent = accent, container = accent.copy(alpha = 0.12f))
}

@Composable
private fun ReviewValueList(
    values: List<id.walt.walletdemo.compose.logic.WalletDemoReviewValue>,
    modifier: Modifier = Modifier,
) {
    MetadataDetailList(
        items = values.map { value ->
            MetadataDetailItem(
                label = value.label,
                value = value.value,
                supportingText = value.supportingText,
                linkUri = value.linkUri,
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun ReviewIslandTechnicalPage(
    island: WalletDemoReviewIsland,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(WalletUiTestTags.ReviewTechnicalDetailsPage),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag(WalletUiTestTags.ReviewTechnicalDetailsBack),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to review")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = island.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = "Technical details",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        island.visibleTechnicalSections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ReviewValueList(
                        values = section.visibleValues,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }
    }
}

/** A surface-owned, always-reachable consent and refusal region. */
@Composable
internal fun ReviewActionBar(
    primaryLabel: String,
    primaryCompactLabel: String = primaryLabel,
    primaryEnabled: Boolean,
    onPrimary: () -> Unit,
    primaryTestTag: String,
    secondaryLabel: String,
    secondaryEnabled: Boolean,
    onSecondary: () -> Unit,
    secondaryTestTag: String,
    secondaryCompactIcon: ImageVector = Icons.Default.Close,
    modifier: Modifier = Modifier,
    tertiaryLabel: String? = null,
    tertiaryEnabled: Boolean = true,
    onTertiary: (() -> Unit)? = null,
    tertiaryTestTag: String? = null,
    tertiaryCompactIcon: ImageVector = Icons.Default.Close,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                val compact = maxWidth < 360.dp || LocalDensity.current.fontScale > 1.25f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onPrimary,
                        enabled = primaryEnabled,
                        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 20.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(primaryTestTag),
                    ) {
                        Text(
                            text = if (compact) primaryCompactLabel else primaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    ReviewCompactAction(
                        label = secondaryLabel,
                        icon = secondaryCompactIcon,
                        enabled = secondaryEnabled,
                        compact = compact && tertiaryLabel != null,
                        onClick = onSecondary,
                        modifier = Modifier.testTag(secondaryTestTag),
                    )

                    if (tertiaryLabel != null && onTertiary != null) {
                        ReviewCompactAction(
                            label = tertiaryLabel,
                            icon = tertiaryCompactIcon,
                            enabled = tertiaryEnabled,
                            compact = compact,
                            onClick = onTertiary,
                            modifier = tertiaryTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCompactAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (compact) {
        OutlinedIconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.size(48.dp),
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = modifier,
        ) {
            Text(label, maxLines = 1)
        }
    }
}

private fun reviewRouteTransition(
    initial: WalletDemoReviewRoute,
    target: WalletDemoReviewRoute,
): ContentTransform {
    val movingForward = initial is WalletDemoReviewRoute.Summary &&
        target is WalletDemoReviewRoute.TechnicalDetails
    val direction = if (movingForward) 1 else -1
    return slideInHorizontally(
        animationSpec = tween(220),
        initialOffsetX = { fullWidth -> direction * fullWidth },
    ) togetherWith slideOutHorizontally(
        animationSpec = tween(220),
        targetOffsetX = { fullWidth -> -direction * fullWidth },
    )
}

private fun isSafeReviewImage(value: String): Boolean {
    val normalized = value.trim()
    return normalized.startsWith("https://", ignoreCase = true) ||
        normalized.startsWith("data:image/", ignoreCase = true)
}
