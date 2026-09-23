package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toSystemInfoGroup
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun CredentialDetailsContent(
    details: CredentialDetails,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.credentialDetails(details.summary.id)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CredentialOverviewSection(details)
        val systemInfoGroup = details.toSystemInfoGroup()
        if (details.groups.isEmpty() && systemInfoGroup == null) {
            Text(
                "No credential details available",
            )
        }
        details.groups.forEach { group ->
            ClaimGroupSection(group)
        }
        systemInfoGroup?.let { ClaimGroupSection(it) }
    }
}

@Composable
internal fun CredentialDetailsCloseButton(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClose)
            .testTag(WalletUiTestTags.DetailsBack),
        contentAlignment = Alignment.Center,
    ) {
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
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .clickable { expanded = true }
                .testTag(WalletUiTestTags.DetailsMenu),
            contentAlignment = Alignment.Center,
        ) {
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
