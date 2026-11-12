package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

internal data class CredentialStatusUiContext(
    val snapshot: WalletDemoCredentialStatuses = WalletDemoCredentialStatuses(),
    val checking: Boolean = false,
    val check: ((String) -> Unit)? = null,
    val automatic: Boolean = true,
    val setAutomatic: ((Boolean) -> Unit)? = null,
    val checkAll: (() -> Unit)? = null,
    val summary: WalletDemoStatusRefreshSummary? = null,
    val error: String? = null,
)
internal val LocalCredentialStatusUi = staticCompositionLocalOf { CredentialStatusUiContext() }

@Composable
internal fun WalletDemoCredentialStatus.title(): String = stringResource(when (kind) {
    "NOT_PROVIDED" -> Res.string.credential_status_not_provided
    "NOT_CHECKED" -> Res.string.credential_status_not_checked
    "NOT_REVOKED" -> Res.string.credential_status_clear
    "NOT_SUSPENDED" -> Res.string.credential_status_not_suspended
    "CHECKED" -> Res.string.credential_status_checked
    "REVOKED" -> Res.string.credential_status_revoked
    "INVALID" -> Res.string.credential_status_invalid
    "SUSPENDED" -> Res.string.credential_status_suspended
    "OUT_OF_DATE" -> Res.string.credential_status_stale
    "UNVERIFIED" -> Res.string.credential_status_unverified
    "UNSUPPORTED" -> Res.string.credential_status_unsupported
    "PARTIAL" -> Res.string.credential_status_partial
    "NEEDS_REVIEW" -> Res.string.credential_status_review
    "SERVER_UNSUPPORTED" -> Res.string.credential_status_server_unsupported
    else -> Res.string.credential_status_unavailable
})

@Composable
internal fun CredentialStatusIcon(status: WalletDemoCredentialStatus, modifier: Modifier = Modifier, description: String? = null) {
    val color = if (status.red) MaterialTheme.colorScheme.error else if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFFFFCC80) else Color(0xFF946000)
    Canvas(modifier.size(28.dp).background(MaterialTheme.colorScheme.surface, CircleShape).padding(4.dp)
        .semantics { if (description != null) contentDescription = description }) {
        val stroke = 2.dp.toPx()
        val center = center
        if (status.issue in setOf("REVOKED", "INVALID", "SUSPENDED", "OUT_OF_DATE")) {
            drawCircle(color, size.minDimension / 2 - stroke, style = Stroke(stroke))
            when (status.issue) {
                "REVOKED", "INVALID" -> {
                    drawLine(color, Offset(size.width * .34f, size.height * .34f), Offset(size.width * .66f, size.height * .66f), stroke, StrokeCap.Round)
                    drawLine(color, Offset(size.width * .66f, size.height * .34f), Offset(size.width * .34f, size.height * .66f), stroke, StrokeCap.Round)
                }
                "SUSPENDED" -> {
                    for (x in listOf(.4f, .6f)) drawLine(color, Offset(size.width*x, size.height*.32f), Offset(size.width*x, size.height*.68f), stroke, StrokeCap.Round)
                }
                else -> {
                    drawLine(color, center, Offset(center.x, size.height*.25f), stroke, StrokeCap.Round)
                    drawLine(color, center, Offset(size.width*.7f, center.y), stroke, StrokeCap.Round)
                }
            }
        } else {
            val path = Path().apply { moveTo(center.x, stroke); lineTo(size.width-stroke, size.height-stroke); lineTo(stroke, size.height-stroke); close() }
            drawPath(path, color, style = Stroke(stroke))
            drawLine(color, Offset(center.x, size.height*.4f), Offset(center.x, size.height*.6f), stroke, StrokeCap.Round)
            drawCircle(color, stroke/2, Offset(center.x, size.height*.78f))
        }
    }
}

@Composable
internal fun CredentialStatusSection(credentialId: String, compact: Boolean = false) {
    val context = LocalCredentialStatusUi.current
    val status = context.snapshot.credentials[credentialId] ?: return
    var expanded by remember(credentialId) { mutableStateOf(false) }
    val refreshing = credentialId in context.snapshot.refreshing || context.checking
    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().padding(if (compact) 12.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (status.hasIssue) CredentialStatusIcon(status)
                val stale = if (compact && status.freshness == "STALE" && status.kind != "OUT_OF_DATE") " · " + stringResource(Res.string.credential_status_stale_short) else ""
                val partial = if (compact && status.coverage == "PARTIAL" && status.kind != "PARTIAL") " · " + stringResource(Res.string.credential_status_partial_short) else ""
                Text(status.title() + stale + partial, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                if (compact) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("credential-status-details-$credentialId")) {
                    Text(stringResource(Res.string.credential_status_details))
                }
                if (!compact && status.action != "NONE" && context.check != null) TextButton(
                    onClick = { context.check.invoke(credentialId) }, enabled = !refreshing,
                    modifier = Modifier.testTag("credential-status-check-$credentialId"),
                ) { Text(stringResource(if (refreshing) Res.string.credential_status_checking else if (status.action == "RETRY") Res.string.credential_status_retry else Res.string.credential_status_check)) }
            }
            if (!compact) Text(status.lastCheckedAt?.let { stringResource(Res.string.credential_status_checked_at, statusDate(it)) }
                ?: stringResource(Res.string.credential_status_no_check), style = MaterialTheme.typography.bodySmall)
            if (!compact && status.freshness == "STALE") Text(stringResource(Res.string.credential_status_stale_detail), style = MaterialTheme.typography.bodySmall)
            if (!compact && status.coverage == "PARTIAL") Text(stringResource(Res.string.credential_status_partial_detail), style = MaterialTheme.typography.bodySmall)
            if (!compact && status.adverse) Text(stringResource(Res.string.credential_status_adverse_detail), style = MaterialTheme.typography.bodySmall)
            if (!compact) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("credential-status-details-$credentialId")) {
                Text(stringResource(Res.string.credential_status_details))
            }
            if (expanded) {
                val explanation = when (status.kind) {
                    "NOT_PROVIDED" -> Res.string.credential_status_not_provided_detail
                    "NOT_CHECKED" -> Res.string.credential_status_not_checked_detail
                    "UNSUPPORTED" -> Res.string.credential_status_unsupported_detail
                    "SERVER_UNSUPPORTED" -> Res.string.credential_status_server_unsupported_detail
                    "UNVERIFIED" -> Res.string.credential_status_unverified_detail
                    else -> Res.string.credential_status_explanation
                }
                Text(stringResource(explanation), style = MaterialTheme.typography.bodySmall)
                if (compact) Text(status.lastCheckedAt?.let { stringResource(Res.string.credential_status_checked_at, statusDate(it)) }
                    ?: stringResource(Res.string.credential_status_no_check), style = MaterialTheme.typography.bodySmall)
                status.lastAttemptAt?.let { Text(stringResource(Res.string.credential_status_attempt_at, statusDate(it)), style = MaterialTheme.typography.bodySmall) }
                status.failure?.let { Text(statusFailureExplanation(it), style = MaterialTheme.typography.bodySmall) }
                Text(stringResource(Res.string.credential_status_privacy), style = MaterialTheme.typography.bodySmall)
                if (compact) Text(stringResource(Res.string.credential_status_local_only), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (compact) content() else WalletSection(stringResource(Res.string.credential_status_title), modifier = Modifier.testTag("credential-status-$credentialId")) { content() }
}

@Composable
private fun statusFailureExplanation(reason: String): String = stringResource(when (reason) {
    "CREDENTIAL_EXPIRED" -> Res.string.credential_status_credential_time_detail
    "REFERENCE_UNAUTHENTICATED" -> Res.string.credential_status_reference_auth_detail
    "REFERENCE_MALFORMED" -> Res.string.credential_status_reference_invalid_detail
    "RATE_LIMITED" -> Res.string.credential_status_rate_limited_detail
    "RESOURCE_LIMIT" -> Res.string.credential_status_limit_detail
    "AMBIGUOUS_CREDENTIAL_ID" -> Res.string.credential_status_ambiguous_detail
    "CONFLICTING_IRREVERSIBLE_STATUS" -> Res.string.credential_status_conflict_detail
    "UNSUPPORTED_METHOD", "UNSUPPORTED_PROOF" -> Res.string.credential_status_unsupported_detail
    "INVALID_SIGNATURE", "UNAUTHORIZED_SIGNER", "INVALID_LIST", "INVALID_VALIDITY" -> Res.string.credential_status_unverified_detail
    "DISABLED" -> Res.string.credential_status_disabled_detail
    else -> Res.string.credential_status_transport_detail
})

private fun statusDate(value: String): String = runCatching {
    val text = WalletDateTime.format(kotlin.time.Instant.parse(value))
    text.fullText
}.getOrDefault(value)

@Composable
internal fun CredentialStatusAdvisory(statuses: List<Pair<String, WalletDemoCredentialStatus>>) {
    val issues = statuses.filter { it.second.hasIssue }
    if (issues.isEmpty()) return
    Column(Modifier.fillMaxWidth().testTag("sharing-credential-status-advisory"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.credential_status_share_advisory), style = MaterialTheme.typography.bodySmall)
        issues.forEach { (name, status) -> Text("$name: ${status.title()}", style = MaterialTheme.typography.bodySmall) }
    }
}
