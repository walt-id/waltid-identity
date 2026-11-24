package id.walt.walletdemo.compose.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialCardDisplayData
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoCredentialStatusNotice
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.WalletStatusKind
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.logic.statusBanner
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import id.walt.walletdemo.compose.ui.LocalWalletTimeFormatter
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialCardMaxWidth
import id.walt.walletdemo.compose.ui.components.CredentialCardStack
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent
import id.walt.walletdemo.compose.ui.components.CredentialStatusMessage
import id.walt.walletdemo.compose.ui.components.CredentialTechnicalInformation
import id.walt.walletdemo.compose.ui.components.Id1AspectRatio
import id.walt.walletdemo.compose.ui.components.walletNavigationMotion
import id.walt.walletdemo.compose.ui.components.WalletMotion
import id.walt.walletdemo.compose.ui.components.WalletCredentialContentMaxWidth
import id.walt.walletdemo.compose.ui.components.StatusCard
import id.walt.walletdemo.compose.ui.components.WalletFooter
import id.walt.walletdemo.compose.ui.components.WalletNoticeCard
import id.walt.walletdemo.compose.ui.components.WalletNoticeClose
import id.walt.walletdemo.compose.ui.components.WalletNoticeTone
import id.walt.walletdemo.compose.ui.components.walletNavigationBackground
import id.walt.walletdemo.compose.ui.plainTextClipEntry
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Instant

@Composable
internal fun CredentialsTab(
    state: WalletDemoUiState,
    modifier: Modifier = Modifier,
    onDeleteCredential: ((String) -> Unit)? = null,
    onDetailsChromeChange: (CredentialDetailsChrome?) -> Unit = {},
    onDetailsVisible: suspend (String) -> Unit = {},
    isVisible: Boolean = false,
    onDismissStatus: () -> Unit = {},
    onToggleStatusExpanded: () -> Unit = {},
    onDismissStatusMessage: (String) -> Unit = {},
    onAcknowledgeStatusNotice: (WalletDemoCredentialStatusNotice) -> Unit = {},
    openStatusNoticeOnHome: Boolean = false,
    onStatusNoticeOpenedOnHome: () -> Unit = {},
) {
    val session = state.session
    val credentials = (session as? WalletSessionState.Ready)?.credentials.orEmpty()
    val cards by produceState<List<CredentialCardDisplayData>>(emptyList(), credentials) {
        value = emptyList()
        value = withContext(Dispatchers.Default) { credentials.map { it.toCardDisplayData() } }
    }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var technicalOpen by remember { mutableStateOf(false) }
    val savedPages = rememberSaveableStateHolder()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val selectedCredential = credentials.firstOrNull { it.id == expandedId }
    val expanded by produceState<CredentialDetails?>(null, selectedCredential) {
        value = null
        value = selectedCredential?.let { credential ->
            withContext(Dispatchers.Default) { credential.toCredentialDetails() }
        }
    }
    val rawCredential = selectedCredential?.credentialDataJson?.takeIf { it.isNotBlank() }
        ?: "No raw credential available"
    val showingDetails = selectedCredential != null
    val reduceMotion = LocalWalletVisualPreferences.current.reduceMotion
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    fun requestClose() { technicalOpen = false; expandedId = null }
    fun toggleCard(id: String) { expandedId = id.takeUnless { it == expandedId } }

    val visibleDetailsId = selectedCredential?.id?.takeIf { isVisible }
    LaunchedEffect(visibleDetailsId) {
        visibleDetailsId?.let { onDetailsVisible(it) }
    }
    LaunchedEffect(openStatusNoticeOnHome) {
        if (openStatusNoticeOnHome) {
            requestClose()
            onStatusNoticeOpenedOnHome()
        }
    }
    LaunchedEffect(expandedId) { technicalOpen = false }
    LaunchedEffect(showingDetails, technicalOpen, rawCredential, onDeleteCredential, clipboard) {
        onDetailsChromeChange(
            if (showingDetails) {
                CredentialDetailsChrome(
                    onClose = ::requestClose,
                    title = "Technical details".takeIf { technicalOpen },
                    onBack = ({ technicalOpen = false }).takeIf { technicalOpen },
                    onCopy = {
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            clipboard.setClipEntry(plainTextClipEntry(rawCredential))
                        }
                    },
                    onDelete = if (onDeleteCredential != null) {
                        { confirmDelete = true }
                    } else {
                        null
                    },
                )
            } else {
                null
            },
        )
    }
    DisposableEffect(Unit) {
        onDispose { onDetailsChromeChange(null) }
    }

    SystemBackHandler(enabled = showingDetails) {
        if (technicalOpen) technicalOpen = false else requestClose()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (showingDetails) Modifier.testTag(WalletUiTestTags.CredentialDetailsScreen)
                else Modifier,
            ),
    ) {
        AnimatedContent(
            technicalOpen, modifier = Modifier.weight(1f),
            transitionSpec = { walletNavigationMotion(targetState, reduceMotion, rtl) }, label = "stored-credential-page",
        ) { technical ->
            savedPages.SaveableStateProvider("${expandedId ?: "collection"}:$technical") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .walletNavigationBackground()
                        .verticalScroll(rememberScrollState())
                        .wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = WalletCredentialContentMaxWidth)
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (technical && expanded != null) {
                        CredentialTechnicalInformation(expanded!!)
                    } else if (session is WalletSessionState.NotBootstrapped || session is WalletSessionState.Bootstrapping) {
                        CircularProgressIndicator(Modifier.testTag(WalletUiTestTags.CredentialsLoading))
                    } else if (session is WalletSessionState.Failed) {
                        Text(session.message)
                    } else if (credentials.isEmpty()) {
                        EmptyCredentialsState()
                    } else if (cards.isEmpty()) {
                        CircularProgressIndicator(Modifier.testTag(WalletUiTestTags.CredentialsLoading))
                    } else {
                        CredentialCardStack(
                            cards = cards,
                            expandedId = expandedId.takeIf { showingDetails },
                            onOpenDetails = ::toggleCard,
                        )
                        if (selectedCredential != null && expanded == null) {
                            CircularProgressIndicator()
                        }
                        AnimatedContent(
                            targetState = expanded.takeIf { showingDetails },
                            transitionSpec = { fadeIn(WalletMotion.feedback(reduceMotion)) togetherWith fadeOut(WalletMotion.feedback(reduceMotion)) },
                            label = "stored-credential-information",
                        ) { details ->
                            details?.let { selected ->
                                CredentialDetailsContent(
                                    details = selected,
                                    onTechnicalDetails = { technicalOpen = true },
                                )
                            }
                        }
                    }
                }
            }
        }
        if (isVisible && !confirmDelete) {
            CredentialScreenFeedback(
                state, cards, showingDetails, onDismissStatus, onToggleStatusExpanded, onDismissStatusMessage,
                onAcknowledgeStatusNotice, onOpenDetails = { expandedId = it },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete credential?") },
            text = { Text("This removes the credential from the wallet. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val id = expandedId
                        confirmDelete = false
                        if (id != null) {
                            expandedId = null
                            onDeleteCredential?.invoke(id)
                        }
                    },
                    modifier = Modifier.testTag(WalletUiTestTags.DeleteCredentialConfirm),
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun CredentialScreenFeedback(
    state: WalletDemoUiState,
    cards: List<CredentialCardDisplayData>,
    showingDetails: Boolean,
    onDismissStatus: () -> Unit,
    onToggleStatusExpanded: () -> Unit,
    onDismissStatusMessage: (String) -> Unit,
    onAcknowledgeStatusNotice: (WalletDemoCredentialStatusNotice) -> Unit,
    onOpenDetails: (String) -> Unit,
) {
    val operation = state.statusBanner()?.takeUnless { showingDetails || it.key == state.statusDismissedKey }
    val allowStatus = state.externalFlow == null && state.presentationReview == null && state.offerPreview == null && !state.isBusy
    val credentialNotices = if (!showingDetails && allowStatus && state.credentialStatuses.available) {
        state.credentialStatuses.pendingNotices.orEmpty().mapNotNull { notice ->
            cards.firstOrNull { it.id == notice.credentialId }?.let { notice to it.title }
        }
    } else emptyList()
    val credentialNotice = credentialNotices.firstOrNull()
    val statusMessage = state.credentialStatusNotice.takeIf { allowStatus }
    if (operation == null && credentialNotice == null && statusMessage == null) return

    WalletFooter(feedback = {
        Box(Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = CredentialCardMaxWidth)) {
            when {
                operation?.kind == WalletStatusKind.Busy || operation?.kind == WalletStatusKind.Error ->
                    StatusCard(state, onDismissStatus, onToggleStatusExpanded)
                credentialNotice != null -> {
                    val (notice, title) = credentialNotice
                    key(notice.credentialId, notice.version) {
                        CredentialStatusHomeNotice(
                            title = title,
                            notice = notice,
                            remaining = credentialNotices.size - 1,
                            onOpen = {
                                onOpenDetails(notice.credentialId)
                                onAcknowledgeStatusNotice(notice)
                            },
                            onDismiss = { onAcknowledgeStatusNotice(notice) },
                        )
                    }
                }
                statusMessage != null -> CredentialStatusMessage(statusMessage, onDismissStatusMessage)
                else -> StatusCard(state, onDismissStatus, onToggleStatusExpanded)
            }
        }
    })
}

@Composable
private fun CredentialStatusHomeNotice(
    title: String,
    notice: WalletDemoCredentialStatusNotice,
    remaining: Int,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val kind = stringResource(when (notice.kind) {
        "REVOKED" -> Res.string.credential_status_revoked
        "SUSPENDED" -> Res.string.credential_status_suspended
        else -> Res.string.credential_status_invalid
    })
    val instant = Instant.parse(notice.checkedAt)
    val minute = Instant.fromEpochSeconds(instant.epochSeconds - instant.epochSeconds.mod(60))
    val checked = LocalWalletTimeFormatter.current(minute).fullText
    WalletNoticeCard(
        modifier = Modifier.testTag(WalletUiTestTags.CredentialStatusNotice),
        tone = WalletNoticeTone.Warning,
        onDismiss = onDismiss,
    ) {
        Box {
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 50.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(Res.string.credential_status_notice_title, title, kind),
                    style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(stringResource(if (notice.historical) Res.string.credential_status_notice_historical
                    else Res.string.credential_status_notice_checked, checked),
                    style = MaterialTheme.typography.bodySmall)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    val foreground = LocalContentColor.current
                    TextButton(
                        onClick = onOpen,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = foreground.copy(alpha = 0.12f), contentColor = foreground,
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                    ) {
                        Text(stringResource(Res.string.credential_status_notice_view), fontWeight = FontWeight.SemiBold)
                    }
                    if (remaining > 0) {
                        Text(stringResource(Res.string.credential_status_notice_remaining, remaining),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            WalletNoticeClose(stringResource(Res.string.credential_status_notice_dismiss), onDismiss,
                Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun EmptyCredentialsState() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).aspectRatio(Id1AspectRatio).testTag(WalletUiTestTags.CredentialsEmpty),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            Text("No credentials yet", style = MaterialTheme.typography.titleMedium)
            Text("Scan a credential offer to add your first credential.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
