package id.walt.walletdemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private val viewModel: WalletViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            MaterialTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                ) { innerPadding ->
                    WalletScreen(viewModel, Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val url = intent?.data?.toString() ?: return
        when (intent.data?.scheme) {
            "openid-credential-offer" -> viewModel.setOfferUrl(url)
            "openid4vp" -> viewModel.setPresentationRequestUrl(url)
        }
    }
}

@Composable
private fun WalletScreen(viewModel: WalletViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("walt.id Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(state.status, color = if (state.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)

        HorizontalDivider()

        Text("Receive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.offerUrl,
            onValueChange = viewModel::setOfferUrl,
            label = { Text("Credential offer URL") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 3,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                enabled = state.isReady && state.offerUrl.isNotBlank() && !state.isBusy,
                onClick = viewModel::receive,
            ) {
                Text("Receive")
            }
        }

        HorizontalDivider()

        Text("Present", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = state.presentationRequestUrl,
            onValueChange = viewModel::setPresentationRequestUrl,
            label = { Text("OpenID4VP request URL") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 3,
        )
        Button(
            enabled = state.isReady && state.presentationRequestUrl.isNotBlank() && state.credentials.isNotEmpty() && !state.isBusy,
            onClick = viewModel::present,
        ) {
            Text("Present")
        }

        HorizontalDivider()

        Text("Credentials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (state.credentials.isEmpty()) {
            Text("No credentials")
        } else {
            state.credentials.forEach { credential ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(credential.label ?: credential.format, fontWeight = FontWeight.Medium)
                    Text("Issuer: ${credential.issuer ?: "Unknown"}", style = MaterialTheme.typography.bodySmall)
                    Text("ID: ${credential.id}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
