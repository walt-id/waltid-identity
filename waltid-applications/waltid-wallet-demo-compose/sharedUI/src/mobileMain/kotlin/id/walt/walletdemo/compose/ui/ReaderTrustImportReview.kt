package id.walt.walletdemo.compose.ui

import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import id.walt.wallet2.mobile.ProximityReaderPolicy
import id.walt.wallet2.mobile.ProximityReaderTrustImportKind
import id.walt.wallet2.mobile.ProximityReaderTrustImportPreview
import id.walt.walletdemo.compose.ui.components.*
import id.walt.walletdemo.compose.ui.resources.*
import org.jetbrains.compose.resources.stringResource

/** The same scroll/action layout as credential review, without changing trust decisions. */
@Composable
internal fun ReaderTrustImportReview(
    preview: ProximityReaderTrustImportPreview,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    presentation: WalletReviewPresentation = WalletReviewPresentation.Sheet,
) {
    WalletReviewHost(presentation, dismissEnabled = true, onDismiss = onCancel) { fillViewport ->
        ReviewScaffold(
            modifier = Modifier.testTag(WalletUiTestTags.SettingsReaderTrustImportReview),
            fillViewport = fillViewport,
            header = {
                WalletScreenHeader(stringResource(Res.string.reader_trust_review_reader_trust_import)) {
                    IconButton(onCancel, modifier = Modifier.testTag(WalletUiTestTags.SettingsReaderTrustImportCancel)) {
                        WalletIcon(WalletSymbol.Decline, stringResource(Res.string.reader_trust_cancel))
                    }
                }
            },
            actions = {
                WalletActions(
                    WalletAction(stringResource(Res.string.reader_trust_import), onImport,
                        testTag = WalletUiTestTags.SettingsReaderTrustImportConfirm),
                )
            },
        ) {
            WalletSection {
                SettingsDetailRow(stringResource(Res.string.reader_trust_file), preview.sourceName)
                SettingsDetailRow(stringResource(Res.string.reader_trust_kind), stringResource(
                    if (preview.kind == ProximityReaderTrustImportKind.ReaderCa) Res.string.reader_trust_configured_reader_ca
                    else Res.string.reader_trust_bundle))
                SettingsDetailRow(stringResource(Res.string.reader_trust_policy), stringResource(
                    if (preview.resultingSettings.readerPolicy == ProximityReaderPolicy.RequireTrusted)
                        Res.string.reader_trust_require_a_trusted_reader else Res.string.reader_trust_allow_anonymous_or_untrusted_readers))
            }
            preview.readerAuthorities.forEach { authority ->
                WalletSection(authority.displayName) {
                    SettingsDetailRow(stringResource(Res.string.reader_trust_type), authority.profile)
                    SettingsDetailRow(stringResource(Res.string.reader_trust_role), stringResource(Res.string.reader_trust_anchor))
                    SettingsDetailRow(stringResource(Res.string.reader_trust_subject), authority.subject)
                    SettingsDetailRow(stringResource(Res.string.reader_trust_issuer), authority.issuer)
                    SettingsDetailRow(stringResource(Res.string.reader_trust_valid_from), authority.validFrom.toString())
                    SettingsDetailRow(stringResource(Res.string.reader_trust_valid_until), authority.validUntil.toString())
                }
                SettingsCopyRow(stringResource(Res.string.reader_trust_fingerprint), authority.sha256Fingerprint,
                    "reader-ca-fingerprint-${authority.sha256Fingerprint}", "reader-ca-copy-${authority.sha256Fingerprint}",
                    stringResource(Res.string.reader_trust_copy_fingerprint, authority.displayName),
                    stringResource(Res.string.reader_trust_fingerprint_copied, authority.displayName))
            }
            preview.ricalProviders.forEach { provider ->
                WalletSection(provider.providerName) {
                    SettingsDetailRow(stringResource(Res.string.reader_trust_provider_id), provider.providerId)
                    SettingsDetailRow(stringResource(Res.string.reader_trust_type), provider.type)
                    SettingsDetailRow(stringResource(Res.string.reader_trust_issued), provider.issuedAt.toString())
                    SettingsDetailRow(stringResource(Res.string.reader_trust_next_update), provider.nextUpdate?.toString() ?: stringResource(Res.string.reader_trust_unspecified))
                    SettingsDetailRow(stringResource(Res.string.reader_trust_valid_until), provider.validUntil?.toString() ?: stringResource(Res.string.reader_trust_unspecified))
                    SettingsNotice(stringResource(if (provider.establishesReaderTrust) Res.string.reader_trust_establishes_reader_trust else Res.string.reader_trust_evidence_only))
                }
            }
        }
    }
}
