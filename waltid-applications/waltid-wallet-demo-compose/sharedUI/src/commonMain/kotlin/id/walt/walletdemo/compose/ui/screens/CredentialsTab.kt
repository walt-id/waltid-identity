package id.walt.walletdemo.compose.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialCardDisplayData
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.toCardDisplayData
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialCardStack
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var showDetailsBody by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val selectedCredential = credentials.firstOrNull { it.id == expandedId }
    val expanded by produceState<CredentialDetails?>(null, selectedCredential) {
        value = null
        value = selectedCredential?.let { credential ->
            withContext(Dispatchers.Default) { credential.toCredentialDetails() }
        }
    }
    val rawCredential = selectedCredential?.credentialDataJson?.takeIf { it.isNotBlank() }
        ?: "No raw credential available"
    val showingDetails = selectedCredential != null || closing

    fun requestClose() {
        if (expandedId == null || closing) return
        closing = true
        showDetailsBody = false
    }

    fun toggleCard(id: String) {
        if (closing) return
        if (expandedId == id) {
            requestClose()
        } else {
            expandedId = id
            showDetailsBody = false
        }
    }

    LaunchedEffect(expandedId) {
        if (expandedId != null && !closing) {
            delay(DetailsRevealDelayMillis)
            if (expandedId != null && !closing) showDetailsBody = true
        }
    }

    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        delay(220)
        expandedId = null
        closing = false
    }

    LaunchedEffect(showingDetails, rawCredential, onDeleteCredential) {
        onDetailsChromeChange(
            if (showingDetails) {
                CredentialDetailsChrome(
                    onClose = ::requestClose,
                    onCopy = { clipboard.setText(AnnotatedString(rawCredential)) },
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
        requestClose()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (showingDetails) Modifier.testTag(WalletUiTestTags.CredentialDetailsScreen)
                else Modifier,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (session is WalletSessionState.NotBootstrapped || session is WalletSessionState.Bootstrapping) {
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
                    expandedId = expandedId,
                    onOpenDetails = ::toggleCard,
                )
                if (showDetailsBody && selectedCredential != null && expanded == null) {
                    CircularProgressIndicator()
                }
                AnimatedVisibility(
                    visible = showDetailsBody && expanded != null,
                    enter = fadeIn(tween(160)),
                    exit = fadeOut(tween(180)),
                ) {
                    expanded?.let { selected ->
                        CredentialDetailsContent(
                            details = selected,
                        )
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
                            closing = false
                            showDetailsBody = false
                            onDeleteCredential?.invoke(id)
                        }
                    },
                    modifier = Modifier.testTag(WalletUiTestTags.DeleteCredentialConfirm),
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

private const val DetailsRevealDelayMillis = 220L

@Composable
private fun EmptyCredentialsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.CredentialsEmpty)
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("No credentials yet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Text(
            "Receive a credential to see it here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
