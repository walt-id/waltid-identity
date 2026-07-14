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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.WalletDemoController
import id.walt.walletdemo.compose.logic.WalletDemoUiState
import id.walt.walletdemo.compose.logic.WalletOperationState
import id.walt.walletdemo.compose.logic.WalletSessionState
import id.walt.walletdemo.compose.logic.isBusy
import id.walt.walletdemo.compose.logic.toCredentialDetails
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
    val ready = state.session as? WalletSessionState.Ready
    val selectedCredential = ready?.credentials?.firstOrNull { it.id == state.selectedCredentialId }
    if (selectedCredential != null) {
        CredentialDetailsScreen(
            details = selectedCredential.toCredentialDetails(),
            onBack = controller::clearSelectedCredential,
        )
        return
    }

    var mode by remember { mutableStateOf(if (state.requestDrafts.offerUrl.isNotBlank()) ReceiveMode.MANUAL else ReceiveMode.SCAN) }
    var receiveRequested by remember { mutableStateOf(false) }
    val isReady = ready != null
    val canReceive = isReady && !state.isBusy
    val canSubmitManualOffer = canReceive && state.requestDrafts.offerUrl.isNotBlank()
    val canSubmitScannedOffer = canReceive && state.requestDrafts.txCodeRequired

    LaunchedEffect(state.requestDrafts.offerUrl) {
        if (state.requestDrafts.offerUrl.isNotBlank()) mode = ReceiveMode.MANUAL
    }

    LaunchedEffect(state.operation, state.requestDrafts.txCodeRequired) {
        when (state.operation) {
            is WalletOperationState.Succeeded -> {
                if (receiveRequested) {
                    receiveRequested = false
                    onReceived()
                }
            }
            is WalletOperationState.Failed,
            WalletOperationState.Presenting,
            -> receiveRequested = false
            WalletOperationState.Idle -> {
                if (!state.requestDrafts.txCodeRequired) receiveRequested = false
            }
            WalletOperationState.ResolvingOffer,
            WalletOperationState.Receiving,
            -> Unit
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
                ReceiveMode.SCAN -> QrScannerSection(
                    txCodeRequired = state.requestDrafts.txCodeRequired,
                    txCode = state.requestDrafts.txCode,
                    onTxCodeChange = controller::updateTxCode,
                    canScan = canReceive,
                    isBusy = state.isBusy,
                    canSubmit = canSubmitScannedOffer,
                    onCodeScanned = { rawValue ->
                        val offerUrl = rawValue.trim()
                        if (offerUrl.isNotEmpty() && canReceive) {
                            receiveRequested = true
                            controller.resolveAndReceive(offerUrl)
                        }
                    },
                    onSubmit = {
                        receiveRequested = true
                        controller.receive()
                    },
                )
                ReceiveMode.MANUAL -> ManualEntrySection(
                    offerUrl = state.requestDrafts.offerUrl,
                    onOfferUrlChange = controller::updateOfferUrl,
                    txCode = state.requestDrafts.txCode,
                    onTxCodeChange = controller::updateTxCode,
                    txCodeRequired = state.requestDrafts.txCodeRequired,
                    onResolve = {
                        receiveRequested = true
                        controller.resolveAndReceive(state.requestDrafts.offerUrl)
                    },
                    onReceive = {
                        receiveRequested = true
                        controller.receive()
                    },
                    isBusy = state.isBusy,
                    canResolve = canSubmitManualOffer && !state.requestDrafts.txCodeRequired,
                    canReceive = canSubmitScannedOffer,
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
                CredentialCard(
                    details = credential.toCredentialDetails(),
                    onClick =  { controller.selectCredential(credential.id) }
                )
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
private fun TxCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    isBusy: Boolean,
    onGo: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wallet.txCodeInput"),
        label = { Text("Pincode (optional)") },
        placeholder = { Text("Enter pincode if required") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = if (onGo != null) ImeAction.Go else ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onGo = { onGo?.invoke() }),
        enabled = !isBusy,
    )
}

@Composable
private fun ManualEntrySection(
    offerUrl: String,
    onOfferUrlChange: (String) -> Unit,
    txCode: String,
    onTxCodeChange: (String) -> Unit,
    txCodeRequired: Boolean,
    onResolve: () -> Unit,
    onReceive: () -> Unit,
    isBusy: Boolean,
    canResolve: Boolean,
    canReceive: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = offerUrl,
            onValueChange = onOfferUrlChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wallet.offerInput"),
            label = { Text("Credential offer link") },
            placeholder = { Text("openid-credential-offer://...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = if (txCodeRequired) ImeAction.Done else ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { if (canResolve) onResolve() }),
            enabled = !isBusy,
        )
        if (txCodeRequired) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "This offer requires a pincode. Enter it below and tap Receive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            TxCodeField(
                value = txCode,
                onValueChange = onTxCodeChange,
                isBusy = isBusy,
                onGo = { if (canReceive) onReceive() },
            )
        }
        Spacer(Modifier.height(12.dp))
        if (txCodeRequired) {
            Button(
                onClick = onReceive,
                enabled = canReceive,
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
        } else {
            Button(
                onClick = onResolve,
                enabled = canResolve,
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
}

@Composable
private fun QrScannerSection(
    txCodeRequired: Boolean,
    txCode: String,
    onTxCodeChange: (String) -> Unit,
    canScan: Boolean,
    isBusy: Boolean,
    canSubmit: Boolean,
    onCodeScanned: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (txCodeRequired) {
            Text(
                text = "This offer requires a pincode. Enter it below and tap Receive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TxCodeField(
                value = txCode,
                onValueChange = onTxCodeChange,
                isBusy = isBusy,
                onGo = { if (canSubmit) onSubmit() },
            )
            Button(
                onClick = onSubmit,
                enabled = canSubmit,
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
        } else {
            QrScanner(enabled = canScan, onCodeScanned = onCodeScanned)
        }
    }
}


@Composable
expect fun QrScanner(
    enabled: Boolean,
    onCodeScanned: (String) -> Unit,
)
