package id.walt.walletdemo.compose.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.LocalWalletVisualPreferences
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialCardStack
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent
import id.walt.walletdemo.compose.ui.components.CredentialTechnicalInformation
import id.walt.walletdemo.compose.ui.components.Id1AspectRatio
import id.walt.walletdemo.compose.ui.components.walletNavigationMotion
import id.walt.walletdemo.compose.ui.plainTextClipEntry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CredentialsTab(
    session: WalletSessionState,
    modifier: Modifier = Modifier,
    onDeleteCredential: ((String) -> Unit)? = null,
    onDetailsChromeChange: (CredentialDetailsChrome?) -> Unit = {},
) {
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
    val motionDuration = if (reduceMotion) 0 else 160

    fun requestClose() { technicalOpen = false; expandedId = null }
    fun toggleCard(id: String) { expandedId = id.takeUnless { it == expandedId } }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (showingDetails) Modifier.testTag(WalletUiTestTags.CredentialDetailsScreen)
                else Modifier,
            ),
    ) {
        AnimatedContent(technicalOpen, transitionSpec = { walletNavigationMotion(targetState, reduceMotion, rtl) },
            label = "stored-credential-page") { technical ->
            savedPages.SaveableStateProvider("${expandedId ?: "collection"}:$technical") {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
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
                            transitionSpec = { fadeIn(tween(motionDuration)) togetherWith fadeOut(tween(motionDuration)) },
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
