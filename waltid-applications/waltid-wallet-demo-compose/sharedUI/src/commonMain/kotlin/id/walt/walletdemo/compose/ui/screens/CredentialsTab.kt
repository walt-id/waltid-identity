package id.walt.walletdemo.compose.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.SystemBackHandler
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialCardStack
import id.walt.walletdemo.compose.ui.components.CredentialDetailsContent

@Composable
internal fun CredentialsTab(
    credentials: List<WalletDemoCredential>,
    modifier: Modifier = Modifier,
) {
    val details = remember(credentials) { credentials.map { it.toCredentialDetails() } }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var showDetailsBody by remember { mutableStateOf(false) }
    val expanded = details.firstOrNull { it.summary.id == expandedId }

    LaunchedEffect(expandedId) {
        if (expandedId == null) {
            showDetailsBody = false
        } else {
            delay(600)
            showDetailsBody = true
        }
    }

    SystemBackHandler(enabled = expanded != null) {
        expandedId = null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (expanded != null) Modifier.testTag(WalletUiTestTags.CredentialDetailsScreen)
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
            if (credentials.isEmpty()) {
                EmptyCredentialsState()
            } else {
                CredentialCardStack(
                    details = details,
                    expandedId = expandedId,
                    onOpenDetails = { id ->
                        expandedId = if (expandedId == id) null else id
                    },
                )
                AnimatedVisibility(
                    visible = showDetailsBody && expanded != null,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    expanded?.let { selected ->
                        CredentialDetailsContent(
                            details = selected,
                            showCard = false,
                            onCardClick = { expandedId = null },
                        )
                    }
                }
            }
        }
        if (expanded != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                    .clickable { expandedId = null }
                    .testTag(WalletUiTestTags.DetailsBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                )
            }
        }
    }
}

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
