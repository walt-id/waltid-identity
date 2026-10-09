import SwiftUI
import WalletSDK

public struct IssuanceResultContent: View {
    private let receipt: IssuanceReceipt?
    private let saved: [Credential]
    private let pending: [DeferredCredential]
    private let busy: Bool
    private let onResume: (DeferredCredential) -> Void

    public init(receipt: IssuanceReceipt?, saved: [Credential], pending: [DeferredCredential], busy: Bool,
                onResume: @escaping (DeferredCredential) -> Void) {
        self.receipt = receipt
        self.saved = saved
        self.pending = pending
        self.busy = busy
        self.onResume = onResume
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            if let issuer = receipt?.issuer { IssuanceIssuerSection(issuer: issuer) }
            if !saved.isEmpty {
                WalletSection(String(format: String(localized: "Saved · %d", bundle: .module), saved.count)) {
                    VStack(spacing: 0) {
                        ForEach(saved, id: \.id) { credential in
                            SavedCredentialRow(credential: credential)
                            if credential.id != saved.last?.id { Divider() }
                        }
                    }
                }
            }
            if !pending.isEmpty {
                WalletSection(String(format: String(localized: "Pending · %d", bundle: .module), pending.count)) {
                    VStack(spacing: 0) {
                        ForEach(pending, id: \.id) { credential in
                            PendingCredentialRow(credential: credential, busy: busy, onResume: onResume)
                            if credential.id != pending.last?.id { Divider() }
                        }
                    }
                }
            }
            if let problem = receipt?.problem {
                WalletSection(String(localized: "Original request", bundle: .module)) {
                    VStack(alignment: .leading, spacing: 8) {
                        if let failure = problem.targetFailure {
                            Text(String(format: String(localized: "Failed targets: %d", bundle: .module), 1))
                            if !failure.notAttempted.isEmpty {
                                Text(String(format: String(localized: "Not attempted: %d", bundle: .module), failure.notAttempted.count))
                            }
                        }
                        Text(problem.message)
                        Text(String(localized: "Saved credentials remain in your wallet. Ask the issuer for a new offer for any targets that could not be completed.", bundle: .module))
                            .font(.footnote).foregroundStyle(.secondary)
                    }.padding(16).frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            if busy { ProgressView().accessibilityIdentifier("issuance-progress") }
        }
    }
}

private struct SavedCredentialRow: View {
    let credential: Credential
    @State private var detailsOpen = false
    @Environment(\.walletReviewNavigationAvailable) private var hasNavigation
    var body: some View {
        Button { detailsOpen = true } label: {
            HStack {
                CredentialSummaryRow(summary: .stored(from: credential))
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.secondary)
            }.padding(16).contentShape(Rectangle())
        }.buttonStyle(.plain).accessibilityIdentifier("issuance-saved-\(credential.id)")
        .walletReviewDestination(isPresented: $detailsOpen) {
            if hasNavigation {
                WalletDetailPage(String(localized: "Credential information", bundle: .module)) {
                    CredentialInformationContent(details: CredentialDisplayNormalizer.details(for: credential))
                }
            } else {
                WalletDetailSheet(String(localized: "Credential information", bundle: .module), onDismiss: { detailsOpen = false }) {
                    CredentialInformationContent(details: CredentialDisplayNormalizer.details(for: credential))
                }
            }
        }
    }
}

private struct PendingCredentialRow: View {
    let credential: DeferredCredential
    let busy: Bool
    let onResume: (DeferredCredential) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            CredentialSummaryRow(summary: summary)
            if credential.status == .awaitingIssuer, let interval = credential.intervalSeconds, interval > 0 {
                Text(String(format: String(localized: "Allow %lld seconds between checks.", bundle: .module), interval))
                    .font(.footnote).foregroundStyle(.secondary)
            }
            if credential.canResume {
                Button(actionLabel) { onResume(credential) }
                    .buttonStyle(.plain).foregroundStyle(Color.accentColor)
                    .frame(minHeight: 44).frame(maxWidth: .infinity, alignment: .trailing)
                    .disabled(busy).accessibilityIdentifier("issuance-resume-\(credential.id)")
            }
        }.padding(16).accessibilityIdentifier("issuance-pending-\(credential.id)")
    }

    private var summary: CredentialCardSummary {
        let display = StoredCredentialMetadataParser.credentialDisplay(from: credential.displayMetadataJSON, preferredLocales: Locale.preferredLanguages)
        return CredentialCardSummary(title: display?.name ?? String(localized: "Pending credential", bundle: .module),
            issuer: statusText, backgroundColor: display?.backgroundColor, backgroundImageURI: display?.backgroundImageURI,
            textColor: display?.textColor, logoURI: display?.logoURI, logoAltText: display?.logoAltText)
    }

    private var statusText: String {
        switch credential.status {
        case .awaitingIssuer: return String(localized: "The issuer is preparing your credential.", bundle: .module)
        case .awaitingLocalSave: return String(localized: "Received. Finish saving it to this wallet.", bundle: .module)
        case .remoteOutcomeUncertain: return String(localized: "The request is still running or its response was lost. It cannot safely be repeated.", bundle: .module)
        case .storageOutcomeUncertain: return String(localized: "Saving is in progress or was interrupted. Another operation cannot safely take over.", bundle: .module)
        case .unresolved: return String(localized: "Receiving was paused. The wallet will check how to continue.", bundle: .module)
        }
    }

    private var actionLabel: String {
        switch credential.status {
        case .awaitingIssuer: return String(localized: "Check with issuer", bundle: .module)
        case .awaitingLocalSave: return String(localized: "Finish saving", bundle: .module)
        default: return String(localized: "Continue receiving", bundle: .module)
        }
    }
}
