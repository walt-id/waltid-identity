package id.walt.walletdemo.compose.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import id.walt.wallet2.mobile.MobileWalletProximityConnectedRoute
import id.walt.wallet2.mobile.MobileWalletProximityEngagementMethod
import id.walt.wallet2.mobile.MobileWalletProximityTransport
import id.walt.walletdemo.compose.ui.components.MetadataDisclosure
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ProximityEngagementChoice(
    method: MobileWalletProximityEngagementMethod,
    onClick: () -> Unit,
) {
    val nfc = method == MobileWalletProximityEngagementMethod.Nfc
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("proximity-show-${method.name}")) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(if (nfc) Res.drawable.proximity_nfc else Res.drawable.proximity_qr),
                contentDescription = null, modifier = Modifier.size(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(if (nfc) Res.string.proximity_tap_reader else Res.string.proximity_show_qr),
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (nfc) Res.string.proximity_tap_description else Res.string.proximity_qr_description),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    HorizontalDivider()
}

@Composable
internal fun ProximityConnectionDetails(route: MobileWalletProximityConnectedRoute) {
    MetadataDisclosure(title = stringResource(Res.string.proximity_connection_details), initiallyExpanded = false, modifier = Modifier.heightIn(min = 48.dp)) {
        Text(stringResource(Res.string.proximity_started_with, stringResource(
            if (route.engagement == MobileWalletProximityEngagementMethod.Qr) Res.string.proximity_show_qr
            else Res.string.proximity_tap_reader,
        )))
        Text(stringResource(Res.string.proximity_used_connection, when (route.transport) {
            MobileWalletProximityTransport.BluetoothLowEnergy -> stringResource(Res.string.proximity_method_bluetooth)
            MobileWalletProximityTransport.Nfc -> stringResource(Res.string.proximity_method_nfc)
            MobileWalletProximityTransport.WifiAware -> stringResource(Res.string.proximity_method_wifi)
        }))
    }
}
