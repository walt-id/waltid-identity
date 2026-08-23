package id.walt.walletdemo.compose.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
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
    islandHeaderContent: @Composable (WalletDemoReviewIsland) -> Unit = {},
    hasCustomExpandedContent: (WalletDemoReviewIsland) -> Boolean = { false },
    technicalBackSignal: Int = 0,
    onRouteChanged: (WalletDemoReviewRoute, WalletDemoReviewIsland?) -> Unit = { _, _ -> },
    showTechnicalHeader: Boolean = true,
    islandExpandedContent: @Composable (WalletDemoReviewIsland) -> Unit = {},
) {
    var technicalIslandId by rememberSaveable(reviewKey) { mutableStateOf<String?>(null) }
    val route: WalletDemoReviewRoute = technicalIslandId
        ?.let { WalletDemoReviewRoute.TechnicalDetails(WalletDemoReviewIslandId(it)) }
        ?: WalletDemoReviewRoute.Summary
    val summaryScrollState = rememberScrollState()
    val technicalScrollState = rememberScrollState()

    LaunchedEffect(route, islands) {
        onRouteChanged(
            route,
            (route as? WalletDemoReviewRoute.TechnicalDetails)
                ?.let { details -> islands.firstOrNull { it.id == details.islandId } },
        )
    }
    LaunchedEffect(technicalBackSignal) {
        if (technicalBackSignal > 0) technicalIslandId = null
    }

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
                            hasCustomExpandedContent = hasCustomExpandedContent(island),
                            headerContent = { islandHeaderContent(island) },
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
                        showHeader = showTechnicalHeader,
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
    hasCustomExpandedContent: Boolean = false,
    headerContent: @Composable () -> Unit = {},
    expandedContent: @Composable () -> Unit = {},
) {
    var expanded by rememberSaveable(island.id.value) { mutableStateOf(island.initiallyExpanded) }
    val islandColors = reviewIslandColors(island.kind)
    val hasModelExpandedContent =
        (showModelExpandedValues && island.visibleExpandedValues.isNotEmpty()) ||
            island.status?.isVisible == true || island.warning != null
    val hasContentBeforeTechnicalDetails = hasModelExpandedContent || hasCustomExpandedContent
    val hasExpandedContent = hasContentBeforeTechnicalDetails || island.hasTechnicalDetails
    val effectiveExpanded = expanded && hasExpandedContent
    val chevronRotation by animateFloatAsState(
        targetValue = if (effectiveExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "Island chevron",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 220)),
        shape = RoundedCornerShape(12.dp),
        color = reviewIslandSurfaceColor(),
        border = BorderStroke(
            width = 1.dp,
            color = if (effectiveExpanded) islandColors.accent.copy(alpha = 0.42f)
            else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.testTag(WalletUiTestTags.reviewIsland(island.id.value))) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                headerContent()
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = hasExpandedContent, role = Role.Button) {
                            expanded = !expanded
                        }
                        .semantics {
                            stateDescription = when {
                                !hasExpandedContent -> "No additional details"
                                effectiveExpanded -> "Expanded"
                                else -> "Collapsed"
                            }
                        }
                        .testTag(WalletUiTestTags.reviewIslandToggle(island.id.value)),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    island.visual?.let { visual ->
                        ReviewIslandVisual(
                            island = island,
                            visual = visual,
                            colors = islandColors,
                        )
                    }
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
                    if (hasExpandedContent) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (effectiveExpanded) {
                                "Collapse ${island.title}"
                            } else {
                                "Expand ${island.title}"
                            },
                            tint = islandColors.accent,
                            modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                        )
                    }
                }
            }

            if (island.visibleSummaryValues.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ReviewValueList(
                    values = island.visibleSummaryValues,
                    modifier = Modifier.padding(12.dp),
                )
            }

            AnimatedVisibility(
                visible = effectiveExpanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(160)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(animationSpec = tween(140)),
            ) {
                Column {
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
                            if (hasContentBeforeTechnicalDetails) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
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
}

@Composable
private fun ReviewIslandVisual(
    island: WalletDemoReviewIsland,
    visual: id.walt.walletdemo.compose.logic.WalletDemoReviewVisual,
    colors: ReviewIslandColors,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.container),
        contentAlignment = Alignment.Center,
    ) {
        val imageUri = visual.imageUri?.takeIf(::isSafeReviewImage)
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
            ReviewVisualFallback(visual.fallbackText, colors.accent)
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
private fun reviewIslandSurfaceColor(): Color =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else Color.White

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
    val groups = values
        .filter(id.walt.walletdemo.compose.logic.WalletDemoReviewValue::isVisible)
        .fold(mutableListOf<ReviewValueGroup>()) { result, value ->
            val groupTitle = value.supportingText?.takeIf(String::isNotBlank)
            val item = MetadataDetailItem(
                label = value.label,
                value = value.value,
                linkUri = value.linkUri,
                sourcePath = value.sourcePath,
            )
            val current = result.lastOrNull()
            if (current != null && current.title == groupTitle) {
                current.items += item
            } else {
                result += ReviewValueGroup(groupTitle, mutableListOf(item))
            }
            result
        }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEachIndexed { index, group ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            group.title?.let { title ->
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            MetadataDetailList(items = group.items)
        }
    }
}

private data class ReviewValueGroup(
    val title: String?,
    val items: MutableList<MetadataDetailItem>,
)

@Composable
private fun ReviewIslandTechnicalPage(
    island: WalletDemoReviewIsland,
    onBack: () -> Unit,
    showHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.testTag(WalletUiTestTags.ReviewTechnicalDetailsPage),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showHeader) Row(
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
                    color = reviewIslandSurfaceColor(),
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
    modifier: Modifier = Modifier,
    tertiaryLabel: String? = null,
    tertiaryEnabled: Boolean = true,
    onTertiary: (() -> Unit)? = null,
    tertiaryTestTag: String? = null,
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
                    val hasTertiary = tertiaryLabel != null && onTertiary != null
                    WalletPrimaryButton(
                        onClick = onPrimary,
                        enabled = primaryEnabled,
                        contentPadding = PaddingValues(horizontal = if (compact) 12.dp else 20.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(primaryTestTag),
                    ) {
                        Text(
                            text = if (compact || hasTertiary) primaryCompactLabel else primaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    ReviewCompactAction(
                        label = secondaryLabel,
                        enabled = secondaryEnabled,
                        onClick = onSecondary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(secondaryTestTag),
                    )

                    if (hasTertiary) {
                        ReviewCompactAction(
                            label = tertiaryLabel,
                            enabled = tertiaryEnabled,
                            onClick = onTertiary,
                            modifier = (tertiaryTestTag?.let { Modifier.testTag(it) } ?: Modifier).weight(1f),
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
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WalletSecondaryButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
