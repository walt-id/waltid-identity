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
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialCard

@Composable
internal fun WalletHomeScreen(
    credentials: List<WalletDemoCredential>,
    onReceive: () -> Unit,
    onPresent: () -> Unit,
    onCredentialClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag(WalletUiTestTags.WalletHome)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Your credentials",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Keep credentials ready to add or share when you choose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onReceive,
                modifier = Modifier
                    .weight(1f)
                    .testTag(WalletUiTestTags.HomeReceiveButton),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Icon(Icons.Outlined.AddCircle, contentDescription = null)
                Text("Receive", modifier = Modifier.padding(start = 8.dp))
            }
            OutlinedButton(
                onClick = onPresent,
                modifier = Modifier
                    .weight(1f)
                    .testTag(WalletUiTestTags.HomePresentButton),
            ) {
                Icon(Icons.Outlined.Share, contentDescription = null)
                Text("Present", modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (credentials.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(WalletUiTestTags.CredentialsEmpty),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("No credentials yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Receive a credential by scanning its QR code or entering a link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onReceive) { Text("Receive a credential") }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                credentials.forEach { credential ->
                    CredentialCard(
                        details = credential.toCredentialDetails(),
                        onClick = { onCredentialClick(credential.id) },
                    )
                }
            }
        }
    }
}
