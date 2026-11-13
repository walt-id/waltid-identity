package id.walt.walletdemo.compose.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import id.walt.walletdemo.compose.logic.CredentialDetails
import id.walt.walletdemo.compose.logic.toSystemInfoGroup
import id.walt.walletdemo.compose.ui.resources.Res
import id.walt.walletdemo.compose.ui.resources.credential_status_title
import id.walt.walletdemo.compose.ui.resources.credential_status_reference
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CredentialTechnicalInformation(details: CredentialDetails) {
    LocalCredentialStatusUi.current.snapshot.credentials[details.summary.id]?.takeIf { it.references.isNotEmpty() }?.let { status ->
        WalletSection(stringResource(Res.string.credential_status_title)) {
            status.references.forEach { reference ->
                SettingsDetailRow(reference.protocolName ?: reference.method ?: stringResource(Res.string.credential_status_reference),
                    listOfNotNull(reference.purpose, reference.result, reference.failure, reference.problem)
                        .joinToString(" · ").ifEmpty { reference.referenceId })
            }
        }
    }
    (details.groups.filter { it.id == "technical" } + listOfNotNull(details.toSystemInfoGroup())).forEach { group ->
        key(group.id) { ClaimGroupSection(group, collapsible = false) }
    }
}
