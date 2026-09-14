package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoIdentitySetup

@Composable
internal fun IdentitySetupScreen(
    setup: WalletDemoIdentitySetup,
    warning: String?,
    onChoose: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Your signing identity", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text("Choose where your key lives and whether it can be recovered.")
            }
            if (warning != null) item { Text(warning, color = MaterialTheme.colorScheme.error) }
            item {
                OutlinedCard {
                    Text("On iOS, Secure Enclave keys cannot be restored on another device. Recoverable identities use ordinary Keychain signing instead.",
                        Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            when (setup) {
                is WalletDemoIdentitySetup.Pending -> item {
                    Text("Identity setup was interrupted. Retry resumes the recorded operation without selecting a replacement identity.")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onResume(setup.identityId) }) { Text("Retry identity setup") }
                    Text("Cancelling removes pending local setup. Any submitted recovery record is retained.")
                    TextButton(onClick = { onCancel(setup.identityId) }) { Text("Cancel pending setup") }
                }
                is WalletDemoIdentitySetup.Choose -> {
                    setup.message?.let { message -> item { Text(message) } }
                    items(setup.choices, key = { it.id }) { choice ->
                        OutlinedCard {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(choice.title, style = MaterialTheme.typography.titleMedium)
                                Text(choice.detail, style = MaterialTheme.typography.bodyMedium)
                                if (choice.recoverable) Text("Cloud delivery and availability on another device depend on the recovery provider.",
                                    style = MaterialTheme.typography.bodySmall)
                                Button(onClick = { onChoose(choice.id) }) { Text("Use this option") }
                            }
                        }
                    }
                    if (setup.choices.isEmpty()) item { Text("No configured option is available. Check device authorization and backup settings, then refresh.") }
                }
            }
            item { TextButton(onClick = onRefresh) { Text("Refresh available options") } }
        }
    }
}
