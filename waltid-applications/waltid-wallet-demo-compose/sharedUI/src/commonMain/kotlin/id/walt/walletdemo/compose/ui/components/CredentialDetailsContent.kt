package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onCardClick: (() -> Unit)? = null,
    showCard: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(WalletUiTestTags.credentialDetails(details.summary.id)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CredentialOverviewSection(details, onCardClick = onCardClick, showCard = showCard)
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
internal fun CredentialDetailsActionButtons(
    onCopy: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onCopy,
            modifier = Modifier
                .weight(1f)
                .testTag(WalletUiTestTags.CopyRawCredential),
        ) {
            Text("Copy")
        }
        if (onDelete != null) {
            Button(
                onClick = onDelete,
                modifier = Modifier
                    .weight(1f)
                    .testTag(WalletUiTestTags.DeleteCredential),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        }
    }
}
