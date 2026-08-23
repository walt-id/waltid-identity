package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.WalletDemoReviewIsland
import id.walt.walletdemo.compose.logic.WalletDemoReviewRoute
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent
import id.walt.walletdemo.compose.ui.components.StoredCredentialDetailsContent
import kotlinx.coroutines.delay

@Composable
internal fun CredentialDetailsScreen(
    details: CredentialDetails,
    onBack: () -> Unit,
    storedCredentialActions: Boolean = false,
    onDeleteCredential: (String) -> Unit = {},
    showHeader: Boolean = true,
    technicalBackSignal: Int = 0,
    onReviewRouteChanged: (WalletDemoReviewRoute, WalletDemoReviewIsland?) -> Unit = { _, _ -> },
) {
    val clipboard = LocalClipboardManager.current
    var confirmDelete by remember(details.summary.id) { mutableStateOf(false) }
    var copiedRawData by remember(details.summary.id) { mutableStateOf(false) }
    LaunchedEffect(copiedRawData) {
        if (copiedRawData) {
            delay(2_000)
            copiedRawData = false
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WalletUiTestTags.CredentialDetailsScreen),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(WalletUiTestTags.DetailsBack),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
                Text(
                    "Credential details",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (storedCredentialActions) {
            StoredCredentialDetailsContent(
                details = details,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                technicalBackSignal = technicalBackSignal,
                onRouteChanged = onReviewRouteChanged,
            )
        } else {
            CredentialDetailsContent(
                details = details,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
        if (storedCredentialActions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        details.summary.credentialDataJson?.let {
                            clipboard.setText(AnnotatedString(it))
                            copiedRawData = true
                        }
                    },
                    enabled = !details.summary.credentialDataJson.isNullOrBlank(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(WalletUiTestTags.CredentialCopyRawData),
                ) {
                    Text(if (copiedRawData) "Copied" else "Copy raw data")
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(WalletUiTestTags.CredentialDelete),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete credential?") },
            text = { Text("This removes the credential from this wallet.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteCredential(details.summary.id)
                    },
                    modifier = Modifier.testTag(WalletUiTestTags.CredentialDeleteConfirm),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}
