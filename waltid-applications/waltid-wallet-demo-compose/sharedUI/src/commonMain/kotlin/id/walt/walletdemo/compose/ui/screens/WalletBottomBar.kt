package id.walt.walletdemo.compose.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import id.walt.walletdemo.compose.logic.WalletDemoTab
import id.walt.walletdemo.compose.ui.WalletUiTestTags

@Composable
internal fun WalletBottomBar(selectedTab: WalletDemoTab, onSelectedTab: (WalletDemoTab) -> Unit) {
    NavigationBar {
        WalletDemoTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selectedTab == tab,
                onClick = { onSelectedTab(tab) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                    )
                },
                label = { Text(tab.title) },
                modifier = Modifier
                    .testTag(tab.testTag)
                    .semantics {
                        contentDescription = "${tab.title} tab"
                    },
            )
        }
    }
}

private val WalletDemoTab.title: String
    get() = when (this) {
        WalletDemoTab.Credentials -> "Credentials"
        WalletDemoTab.Receive -> "Receive"
        WalletDemoTab.Present -> "Present"
    }

private val WalletDemoTab.icon: ImageVector
    get() = when (this) {
        WalletDemoTab.Credentials -> Icons.Filled.AccountBox
        WalletDemoTab.Receive -> Icons.Filled.AddCircle
        WalletDemoTab.Present -> Icons.AutoMirrored.Filled.Send
    }

private val WalletDemoTab.testTag: String
    get() = when (this) {
        WalletDemoTab.Credentials -> WalletUiTestTags.CredentialsTab
        WalletDemoTab.Receive -> WalletUiTestTags.ReceiveTab
        WalletDemoTab.Present -> WalletUiTestTags.PresentTab
    }
