package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CredentialDetailsContent(details: CredentialDetails, modifier: Modifier = Modifier, onTechnicalDetails: (() -> Unit)? = null) {
    var technicalOpen by remember(details.summary.id) { mutableStateOf(false) }
    CredentialDetailsBody(details, onTechnicalDetails = onTechnicalDetails ?: { technicalOpen = true }, modifier = modifier)
    if (technicalOpen) WalletDetailSheet(stringResource(Res.string.credential_technical_details), { technicalOpen = false }) {
        CredentialTechnicalInformation(details)
    }
}

@Composable
internal fun CredentialDetailsCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    WalletHeaderButton(onClick = onClose, modifier = modifier.testTag(WalletUiTestTags.DetailsBack)) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Close",
        )
    }
}

@Composable
internal fun CredentialDetailsOverflowMenu(
    onCopy: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        WalletHeaderButton(onClick = { expanded = true }, modifier = Modifier.testTag(WalletUiTestTags.DetailsMenu)) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "More",
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Copy") },
                onClick = {
                    expanded = false
                    onCopy()
                },
                modifier = Modifier.testTag(WalletUiTestTags.CopyRawCredential),
            )
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    modifier = Modifier.testTag(WalletUiTestTags.DeleteCredential),
                )
            }
        }
    }
}
