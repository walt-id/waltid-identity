package id.walt.walletdemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.ui.components.StatusBanner
import id.walt.walletdemo.viewmodel.WalletViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveScreen(
    viewModel: WalletViewModel,
    initialOfferUrl: String = "",
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var offerUrl by rememberSaveable { mutableStateOf(initialOfferUrl) }
    var txCode by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive Credential") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusBanner(status = state.status)

            Text(
                "Enter a credential offer URL or scan a QR code to receive a credential.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = offerUrl,
                onValueChange = { offerUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Credential Offer URL") },
                placeholder = { Text("openid-credential-offer://...") },
                singleLine = true,
            )

            OutlinedTextField(
                value = txCode,
                onValueChange = { txCode = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Transaction Code (optional)") },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.receiveCredential(offerUrl, txCode) },
                    enabled = offerUrl.isNotBlank() && !state.status.isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Receive (Native SDK)")
                }
                OutlinedButton(
                    onClick = { viewModel.enterpriseReceive(offerUrl) },
                    enabled = offerUrl.isNotBlank()
                            && state.environment.enterpriseBaseUrl.isNotBlank()
                            && state.environment.walletPath.isNotBlank()
                            && !state.status.isLoading,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Enterprise Receive")
                }
            }

            if (state.requireAttestation) {
                Text(
                    "Attestation required: ${state.attestationStatus}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}
