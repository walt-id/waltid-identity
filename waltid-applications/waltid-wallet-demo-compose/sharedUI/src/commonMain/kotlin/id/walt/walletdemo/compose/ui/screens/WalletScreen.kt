package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.ui.components.CredentialCard
import id.walt.walletdemo.compose.ui.components.StatusCard
import id.walt.walletdemo.compose.ui.components.UrlActionSection

@Composable
internal fun WalletScreen(controller: WalletDemoController, state: WalletDemoUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("walt.id Wallet", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (state.did.isNotBlank()) {
                    Text(
                        state.did,
                        modifier = Modifier.testTag("wallet.did"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(onClick = controller::lock) {
                Text("Lock")
            }
        }

        StatusCard(state)

        HorizontalDivider()
        UrlActionSection(
            title = "Receive",
            value = state.offerUrl,
            onValueChange = controller::updateOfferUrl,
            label = "Credential offer URL",
            buttonText = "Receive",
            enabled = state.isReady &&
                state.offerUrl.isNotBlank() &&
                !state.isBusy,
            inputTestTag = "wallet.offerInput",
            buttonTestTag = "wallet.receiveButton",
            onClick = controller::receive,
        )

        HorizontalDivider()
        UrlActionSection(
            title = "Present",
            value = state.presentationRequestUrl,
            onValueChange = controller::updatePresentationRequestUrl,
            label = "OpenID4VP request URL",
            buttonText = "Present",
            enabled = state.isReady &&
                state.presentationRequestUrl.isNotBlank() &&
                state.credentials.isNotEmpty() &&
                !state.isBusy,
            inputTestTag = "wallet.presentationInput",
            buttonTestTag = "wallet.presentButton",
            onClick = controller::present,
        )

        HorizontalDivider()
        Text("Credentials", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (state.credentials.isEmpty()) {
            Text("No credentials", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.credentials.forEach { credential ->
                CredentialCard(credential)
            }
        }
    }
}
