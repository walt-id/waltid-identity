package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toSystemInfoGroup

@Composable
internal fun CredentialTechnicalInformation(details: CredentialDetails) {
    (details.groups.filter { it.id == "technical" } + listOfNotNull(details.toSystemInfoGroup())).forEach { group ->
        key(group.id) { ClaimGroupSection(group, collapsible = false) }
    }
}
