package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoCredential
import id.walt.walletdemo.compose.logic.WalletDemoPinMode
import id.walt.walletdemo.compose.logic.WalletDemoUiState

@Composable
fun WalletDemoApp(controller: WalletDemoController) {
    val state by controller.state.collectAsState()

    MaterialTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .exportTestTagsForPlatformAutomation(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding(),
            ) {
                if (state.isUnlocked) {
                    WalletScreen(controller, state)
                } else {
                    PinScreen(controller, state)
                }
            }
        }
    }
}

@Composable
private fun PinScreen(controller: WalletDemoController, state: WalletDemoUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("walt.id Wallet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            if (state.pinMode == WalletDemoPinMode.Setup) "Create a PIN" else "Enter your PIN",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            if (state.pinMode == WalletDemoPinMode.Setup) {
                "Use 4 to 8 digits for this local demo unlock flow."
            } else {
                "Unlock the local demo wallet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.pin,
            onValueChange = controller::updatePin,
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = state.pinError != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wallet.pinInput"),
            singleLine = true,
        )

        if (state.pinMode == WalletDemoPinMode.Setup) {
            OutlinedTextField(
                value = state.pinConfirmation,
                onValueChange = controller::updatePinConfirmation,
                label = { Text("Confirm PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = state.pinError != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet.pinConfirmationInput"),
                singleLine = true,
            )
        }

        state.pinError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = controller::submitPin,
            enabled = !state.isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wallet.pinSubmitButton"),
        ) {
            Text(if (state.pinMode == WalletDemoPinMode.Setup) "Set PIN" else "Unlock")
        }
    }
}

@Composable
private fun WalletScreen(controller: WalletDemoController, state: WalletDemoUiState) {
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
            enabled = state.isReady && state.offerUrl.isNotBlank() && !state.isBusy,
            onClick = controller::receive,
        )

        HorizontalDivider()
        UrlActionSection(
            title = "Present",
            value = state.presentationRequestUrl,
            onValueChange = controller::updatePresentationRequestUrl,
            label = "OpenID4VP request URL",
            buttonText = "Present",
            enabled = state.isReady && state.presentationRequestUrl.isNotBlank() && state.credentials.isNotEmpty() && !state.isBusy,
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

@Composable
private fun StatusCard(state: WalletDemoUiState) {
    val containerColor = when {
        state.isError -> MaterialTheme.colorScheme.errorContainer
        state.isBusy -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color(0xFFD8E2FF)
    }
    val contentColor = when {
        state.isError -> MaterialTheme.colorScheme.onErrorContainer
        state.isBusy -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> Color(0xFF002E69)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
    ) {
        Text(
            text = state.status,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("wallet.status"),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun UrlActionSection(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(if (title == "Receive") "wallet.offerInput" else "wallet.presentationInput"),
            minLines = 3,
            maxLines = 3,
        )
        Button(
            enabled = enabled,
            onClick = onClick,
            modifier = Modifier.testTag(if (title == "Receive") "wallet.receiveButton" else "wallet.presentButton"),
        ) {
            Text(buttonText)
        }
    }
}

@Composable
private fun CredentialCard(credential: WalletDemoCredential) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(credential.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text("Issuer: ${credential.issuer}", style = MaterialTheme.typography.bodySmall)
            Text("Format: ${credential.format}", style = MaterialTheme.typography.bodySmall)
            Text("ID: ${credential.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
