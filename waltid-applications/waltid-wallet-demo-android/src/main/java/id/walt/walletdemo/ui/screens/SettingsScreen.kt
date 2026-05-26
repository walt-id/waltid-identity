package id.walt.walletdemo.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.walt.wallet2.client.WalletClientEnvironment
import id.walt.wallet2.client.WalletClientEnvironmentProfile
import id.walt.walletdemo.ui.components.StatusBanner
import id.walt.walletdemo.viewmodel.WalletViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: WalletViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val env = state.environment

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusBanner(status = state.status)

            Text("Environment", style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WalletClientEnvironmentProfile.entries.forEach { profile ->
                    FilterChip(
                        selected = state.profile == profile,
                        onClick = { viewModel.applyProfile(profile) },
                        label = { Text(profile.name) },
                    )
                }
            }

            OutlinedTextField(
                value = env.enterpriseBaseUrl,
                onValueChange = { viewModel.updateEnvironment(env.copy(enterpriseBaseUrl = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enterprise Base URL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = env.walletPath,
                onValueChange = { viewModel.updateEnvironment(env.copy(walletPath = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Wallet Path") },
                singleLine = true,
            )
            OutlinedTextField(
                value = env.enterpriseHostHeader,
                onValueChange = { viewModel.updateEnvironment(env.copy(enterpriseHostHeader = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Host Header (local only)") },
                singleLine = true,
            )
            OutlinedTextField(
                value = env.bearerToken,
                onValueChange = { viewModel.updateEnvironment(env.copy(bearerToken = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Bearer Token (optional)") },
                singleLine = true,
            )

            OutlinedButton(
                onClick = { viewModel.deriveRefsFromWalletPath() },
                enabled = env.walletPath.isNotBlank(),
            ) {
                Text("Derive Refs from Wallet Path")
            }

            OutlinedTextField(
                value = env.attesterServiceRef,
                onValueChange = { viewModel.updateEnvironment(env.copy(attesterServiceRef = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Client Attester Service Ref") },
                singleLine = true,
            )
            OutlinedTextField(
                value = env.instanceKeyReference,
                onValueChange = { viewModel.updateEnvironment(env.copy(instanceKeyReference = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Instance Key Reference") },
                singleLine = true,
            )

            HorizontalDivider()

            Text("Client Attestation", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.requireAttestation,
                    onCheckedChange = { viewModel.setRequireAttestation(it) },
                )
                Text("Require attestation before receive")
            }

            OutlinedTextField(
                value = state.expirationSeconds,
                onValueChange = { viewModel.setExpirationSeconds(it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Expiration Seconds (optional)") },
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.obtainAttestation() },
                    enabled = env.enterpriseBaseUrl.isNotBlank()
                            && env.walletPath.isNotBlank()
                            && env.attesterServiceRef.isNotBlank()
                            && env.instanceKeyReference.isNotBlank()
                            && !state.status.isLoading,
                ) {
                    Text("Obtain")
                }
                OutlinedButton(
                    onClick = { viewModel.checkAttestation() },
                    enabled = env.enterpriseBaseUrl.isNotBlank()
                            && env.walletPath.isNotBlank()
                            && !state.status.isLoading,
                ) {
                    Text("Check")
                }
            }

            Text(
                "Attestation: ${state.attestationStatus}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
