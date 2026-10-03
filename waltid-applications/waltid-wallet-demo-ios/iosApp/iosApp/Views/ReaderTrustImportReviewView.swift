import SwiftUI
import WalletSDK
import WalletDemoSharingUI

struct ReaderTrustImportReviewView: View {
    let preview: ProximityReaderTrustImportPreview
    let confirm: () -> Void
    let cancel: () -> Void

    var body: some View {
        NavigationView {
            List {
                Section("Import") {
                    reviewDetail("File", preview.sourceName)
                    reviewDetail(
                        "Kind",
                        preview.kind == .readerCA ? String(localized: "Reader CA") : String(localized: "Trust bundle")
                    )
                    Text(preview.resultingSettings.readerPolicy == .requireTrusted
                        ? String(localized: "Only trusted readers can proceed to review.")
                        : String(localized: "Anonymous or untrusted readers may request credentials."))
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                if !preview.readerAuthorities.isEmpty {
                    Section("Reader CAs") {
                        ForEach(preview.readerAuthorities) { authority in
                            VStack(alignment: .leading, spacing: 5) {
                                Text(authority.displayName).font(.headline)
                                reviewDetail("Type", authority.profile)
                                reviewDetail("Role", String(localized: "Reader trust anchor"))
                                reviewDetail("Subject", authority.subject)
                                reviewDetail("Issuer", authority.issuer)
                                reviewDate("Valid from", authority.validFrom)
                                reviewDate("Valid until", authority.validUntil)
                                SettingsCopyContent(title: "SHA-256 fingerprint", value: authority.sha256Fingerprint,
                                                    copyLabel: "Copy fingerprint for \(authority.displayName)", copyAnnouncement: String(localized: "Fingerprint for \(authority.displayName) copied"), valueID: "reader-ca-fingerprint-\(authority.id)", copyID: "reader-ca-copy-\(authority.id)")
                            }
                        }
                    }
                }
                if !preview.ricalProviders.isEmpty {
                    Section("RICAL providers") {
                        ForEach(preview.ricalProviders) { provider in
                            VStack(alignment: .leading, spacing: 5) {
                                Text(provider.providerName).font(.headline)
                                reviewDetail("Provider ID", provider.providerID)
                                reviewDate("Issued at", provider.issuedAt)
                                if let nextUpdate = provider.nextUpdate { reviewDate("Next update", nextUpdate) }
                                else { reviewDetail("Next update", String(localized: "Not specified")) }
                                if let validUntil = provider.validUntil { reviewDate("Valid until", validUntil) }
                                else { reviewDetail("Valid until", String(localized: "Not specified")) }
                                reviewDetail("Type", provider.type)
                                reviewDetail(
                                    "Trust effect",
                                    provider.establishesReaderTrust
                                        ? String(localized: "Establishes reader trust")
                                        : String(localized: "Evidence only")
                                )
                            }
                        }
                    }
                }
            }
            .frame(maxWidth: 640)
            .frame(maxWidth: .infinity)
            .background(Color(uiColor: .systemGroupedBackground))
            .navigationTitle("Review import")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: cancel)
                        .accessibilityIdentifier(WalletAccessibilityID.readerTrustImportCancel)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Import", action: confirm)
                        .accessibilityIdentifier(WalletAccessibilityID.readerTrustImportConfirm)
                }
            }
            .accessibilityIdentifier(WalletAccessibilityID.readerTrustImportReview)
        }
    }

    private func reviewDetail(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.footnote)
        }
    }

    private func reviewDate(_ label: String, _ value: Date) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(label).font(.caption).foregroundStyle(.secondary)
            Text(value, style: .date).font(.footnote)
        }
    }
}
