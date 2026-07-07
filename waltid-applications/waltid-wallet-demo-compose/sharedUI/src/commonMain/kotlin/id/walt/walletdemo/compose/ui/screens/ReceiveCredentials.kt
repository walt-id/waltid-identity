package id.walt.walletdemo.compose.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletOperationState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.ui.components.CredentialCard
import id.walt.walletdemo.compose.ui.components.StatusCard

private enum class ReceiveMode { SCAN, MANUAL }

@Composable
fun ReceiveCredential(
    controller: WalletDemoController,
    state: WalletDemoUiState,
    onBack: () -> Unit,
    onReceived: () -> Unit,
) {
    var mode by remember { mutableStateOf(ReceiveMode.SCAN) }
    var receiveRequested by remember { mutableStateOf(false) }
    val ready = state.session as? WalletSessionState.Ready
    val isReady = ready != null
    val canReceive = isReady && !state.isBusy
    val canSubmitManualOffer = canReceive && state.requestDrafts.offerUrl.isNotBlank()

    LaunchedEffect(state.operation) {
        when (state.operation) {
            is WalletOperationState.Succeeded -> {
                if (receiveRequested) {
                    receiveRequested = false
                    onReceived()
                }
            }
            is WalletOperationState.Failed,
            WalletOperationState.Idle,
            WalletOperationState.Presenting,
            -> receiveRequested = false
            WalletOperationState.Receiving -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = "Receive Credential",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Scan a QR code or paste the offer link below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        StatusCard(state)

        SegmentedModeToggle(mode = mode, onModeChange = { mode = it })

        AnimatedContent(targetState = mode, label = "receive-mode") { current ->
            when (current) {
                ReceiveMode.SCAN -> QrScanner(
                    enabled = canReceive,
                    onCodeScanned = { rawValue ->
                        val offerUrl = rawValue.trim()
                        if (offerUrl.isNotEmpty() && canReceive) {
                            controller.updateOfferUrl(offerUrl)
                            receiveRequested = true
                            controller.receive()
                        }
                    },
                )
                ReceiveMode.MANUAL -> ManualEntrySection(
                    value = state.requestDrafts.offerUrl,
                    onValueChange = controller::updateOfferUrl,
                    onSubmit = {
                        receiveRequested = true
                        controller.receive()
                    },
                    isBusy = state.isBusy,
                    enabled = canSubmitManualOffer,
                )
            }
        }

        val succeeded = state.operation as? WalletOperationState.Succeeded
        if (succeeded != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = succeeded.message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        val credentials = ready?.credentials.orEmpty()
        if (credentials.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = "Stored Credentials",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            credentials.forEach { credential ->
                CredentialCard(credential)
            }
        }
    }
}

@Composable
private fun SegmentedModeToggle(
    mode: ReceiveMode,
    onModeChange: (ReceiveMode) -> Unit,
) {
    val options = listOf(
        ReceiveMode.SCAN to "Scan QR",
        ReceiveMode.MANUAL to "Enter link",
    )

    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onModeChange(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                icon = {},
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun ManualEntrySection(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    isBusy: Boolean,
    enabled: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wallet.offerInput"),
            label = { Text("Credential offer link") },
            placeholder = { Text("openid-credential-offer://...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (enabled) onSubmit() }),
            enabled = !isBusy,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSubmit,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wallet.receiveButton"),
        ) {
            if (isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("Receive")
            }
        }
    }
}


@Composable
expect fun QrScanner(
    enabled: Boolean,
    onCodeScanned: (String) -> Unit,
)
