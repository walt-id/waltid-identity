package id.walt.walletdemo.compose.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletDemoTxCodeInputMode
import id.walt.walletdemo.compose.logic.WalletRequestDrafts
import id.walt.walletdemo.compose.logic.receivedCredentials
import id.walt.walletdemo.compose.logic.receiveActionEnabled
import id.walt.walletdemo.compose.logic.receiveUrlEntryEnabled
import id.walt.walletdemo.compose.logic.toCredentialDetails
import id.walt.walletdemo.compose.ui.WalletUiTestTags
import id.walt.walletdemo.compose.ui.components.CredentialCard
import id.walt.walletdemo.compose.ui.components.UrlActionSection

@Composable
internal fun ReceiveTab(
    state: WalletDemoUiState,
    requestDrafts: WalletRequestDrafts,
    onOfferUrlChange: (String) -> Unit,
    onTxCodeChange: (String) -> Unit,
    onReceive: () -> Unit,
    onStartNew: () -> Unit,
    onCredentialClick: (String) -> Unit,
) {
    val receivedCredentials = state.receivedCredentials()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag(WalletUiTestTags.ReceiveTabContent)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UrlActionSection(
            title = "Receive",
            value = requestDrafts.offerUrl,
            onValueChange = onOfferUrlChange,
            label = "Credential offer URL",
            buttonText = "Receive",
            enabled = state.receiveActionEnabled,
            inputEnabled = state.receiveUrlEntryEnabled,
            inputTestTag = WalletUiTestTags.OfferInput,
            buttonTestTag = WalletUiTestTags.ReceiveButton,
            scanButtonTestTag = WalletUiTestTags.OfferScanButton,
            contentBeforeActions = {
                requestDrafts.txCodeRequirement?.let { requirement ->
                    Text(
                        text = requirement.description?.takeIf(String::isNotBlank)
                            ?: "This offer requires a transaction code.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = requestDrafts.txCode,
                        onValueChange = { value ->
                            val accepted = when (requirement.inputMode) {
                                WalletDemoTxCodeInputMode.Numeric -> value.filter(Char::isDigit)
                                WalletDemoTxCodeInputMode.Text -> value
                            }
                            onTxCodeChange(requirement.length?.let(accepted::take) ?: accepted)
                        },
                        label = { Text("Transaction code") },
                        singleLine = true,
                        enabled = state.receiveUrlEntryEnabled,
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = when (requirement.inputMode) {
                                WalletDemoTxCodeInputMode.Numeric -> KeyboardType.NumberPassword
                                WalletDemoTxCodeInputMode.Text -> KeyboardType.Password
                            },
                        ),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(WalletUiTestTags.TxCodeInput),
                    )
                }
            },
            onClick = onReceive,
        )

        if (state.receiveCompleted) {
            OutlinedButton(
                onClick = onStartNew,
                modifier = Modifier.testTag(WalletUiTestTags.ReceiveNewButton),
            ) {
                Text("New receive")
            }
            Text("Received credentials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            receivedCredentials.forEach { credential ->
                CredentialCard(
                    details = credential.toCredentialDetails(),
                    onClick = { onCredentialClick(credential.id) },
                )
            }
        }
    }
}
