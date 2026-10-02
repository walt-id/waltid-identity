package id.walt.walletdemo.compose.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toCardDisplayData

@Composable
internal fun CredentialCard(
    details: CredentialDetails,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val display = remember(details) { details.toCardDisplayData() }
    if (compact) {
        Row(modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = "View credential details", onClick = onClick),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            CredentialSummaryRow(display.toCardArt(), display.issuer, Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    } else {
        CredentialCardArt(art = display.toCardArt(), modifier = modifier.fillMaxWidth().clickable(onClick = onClick))
    }
}
