package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent

@Composable
internal fun CredentialDetailsScreen(
    details: CredentialDetails,
    onBack: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val clipboard = LocalClipboardManager.current
    var confirmDelete by remember { mutableStateOf(false) }
    val rawCredential = details.summary.credentialDataJson?.takeIf { it.isNotBlank() }
        ?: "No raw credential available"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WalletUiTestTags.CredentialDetailsScreen),
    ) {
        CredentialDetailsContent(
            details = details,
            onCardClick = onBack,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 20.dp)
                .padding(top = 36.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(rawCredential)) },
                modifier = Modifier.testTag(WalletUiTestTags.CopyRawCredential),
            ) {
                Text("Copy")
            }
            if (onDelete != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.testTag(WalletUiTestTags.DeleteCredential),
                ) {
                    Text("Delete")
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .clickable(onClick = onBack)
                .testTag(WalletUiTestTags.DetailsBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
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
                        confirmDelete = false
                        onDelete?.invoke()
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
