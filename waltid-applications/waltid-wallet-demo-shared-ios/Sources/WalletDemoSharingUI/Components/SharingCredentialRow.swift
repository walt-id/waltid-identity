import SwiftUI
import WalletSDK

/// A selected/requested credential and its independent, read-only information destination.
struct SharingCredentialRow: View {
    let option: PresentationCredentialOption
    let details: CredentialDetails
    let selection: SharingSelection
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleCredential: (PresentationCredentialSelection) -> Void
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    @State private var claimsOpen = false

    var body: some View {
        let requestedDisclosureItems = details.groups
            .first { $0.id == "requested" }?
            .items ?? []
        let credentialSelected = selection.credentials.contains(option.selection)

        HStack(alignment: .center, spacing: 12) {
            if !isReadOnly {
                Toggle(isOn: Binding(get: {
                    credentialSelected
                }, set: { _ in
                    onToggleCredential(option.selection)
                })) {
                    EmptyView()
                }
                .toggleStyle(ReviewCheckboxToggleStyle())
                .labelsHidden()
                .disabled(isLoading)
                .accessibilityLabel(details.cardSummary.title)
                .accessibilityIdentifier(WalletAccessibilityID.presentationCredentialToggle(option.selection.id))
            }

            CredentialCardButton(details: details, compact: true) {
                claimsOpen = true
            }
            .accessibilityIdentifier(WalletAccessibilityID.presentationClaimsToggle(option.selection.id))
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier(WalletAccessibilityID.presentationCredential(option.selection.id))
        .walletReviewDestination(isPresented: $claimsOpen) {
            SharingClaimsSheet(
                option: option,
                details: details,
                credentialSelected: credentialSelected,
                selectedDisclosureOptions: selection.disclosures,
                requestedDisclosureItems: requestedDisclosureItems,
                isLoading: isLoading,
                isReadOnly: isReadOnly,
                onToggleDisclosure: onToggleDisclosure,
                onDismiss: { claimsOpen = false }
            )
        }
    }
}

/// Scrollable claim review the user can leave without changing the Share decision.
struct SharingClaimsSheet: View {
    let option: PresentationCredentialOption
    let details: CredentialDetails
    let credentialSelected: Bool
    let selectedDisclosureOptions: Set<PresentationDisclosureSelection>
    let requestedDisclosureItems: [ClaimItem]
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void
    let onDismiss: () -> Void

    @State private var allInformationOpen = false
    @Environment(\.walletReviewNavigationAvailable) private var hasNavigation

    var body: some View {
        Group {
            if hasNavigation { WalletDetailPage(String(localized: "Credential information", bundle: .module)) { content } }
            else {
                WalletDetailSheet(String(localized: "Credential information", bundle: .module), onDismiss: onDismiss,
                    closeIdentifier: WalletAccessibilityID.presentationClaimsClose) { content }
            }
        }
        .accessibilityIdentifier(WalletAccessibilityID.presentationClaimsDialog)
    }

    private var content: some View {
            VStack(alignment: .leading, spacing: 16) {
                CredentialSummaryRow(summary: details.cardSummary, showsIssuer: false)
                SharingClaimsIssuerRow(details: details)
                if option.disclosures.isEmpty {
                    Text("No additional claims to review").font(.caption).foregroundStyle(.secondary)
                } else {
                    DisclosureList(option: option, credentialSelected: credentialSelected,
                        selectedDisclosureOptions: selectedDisclosureOptions,
                        requestedDisclosureItems: requestedDisclosureItems, isLoading: isLoading,
                        isReadOnly: isReadOnly, onToggleDisclosure: onToggleDisclosure)
                }
                if details.groups.contains(where: { $0.id != "requested" }) {
                    WalletSection {
                        WalletNavigationRow(String(localized: "All credential information", bundle: .module),
                            subtitle: String(localized: "Includes information outside this request.", bundle: .module)) {
                            allInformationOpen = true
                        }.accessibilityIdentifier("review-all-credential-information")
                    }
                }
            }
            .walletDetailDestination(isPresented: $allInformationOpen) {
                WalletDetailPage(String(localized: "All credential information", bundle: .module)) {
                    Text("Includes information outside this request.", bundle: .module).font(.body)
                    CredentialInformationContent(details: details)
                }
                .accessibilityIdentifier("review-all-information-details")
            }
    }
}

private struct SharingClaimsIssuerRow: View {
    let details: CredentialDetails

    var body: some View {
        if let issuerDisplay = details.issuerDisplay {
            let issuer = details.issuer?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            MetadataIdentityView(
                display: issuerDisplay,
                fallbackName: details.cardSummary.issuer,
                supportingText: issuer.isEmpty || issuer == issuerDisplay.name ? nil : issuer
            )
        } else {
            Text("Issuer: \(details.cardSummary.issuer)")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

/// What this credential would actually reveal, claim by claim.
struct DisclosureList: View {
    let option: PresentationCredentialOption
    let credentialSelected: Bool
    let selectedDisclosureOptions: Set<PresentationDisclosureSelection>
    let requestedDisclosureItems: [ClaimItem]
    let isLoading: Bool
    let isReadOnly: Bool
    let onToggleDisclosure: (PresentationDisclosureSelection) -> Void

    var body: some View {
        let disclosures = Array(option.disclosures.enumerated()).sorted {
                (requestedDisclosureItems.indices.contains($0.offset) ? requestedDisclosureItems[$0.offset].displayOrder ?? .max : .max)
                    < (requestedDisclosureItems.indices.contains($1.offset) ? requestedDisclosureItems[$1.offset].displayOrder ?? .max : .max)
        }
        return WalletSection(CredentialDisplayVocabulary.requestedDisclosuresTitle) {
          LazyVStack(alignment: .leading, spacing: 12) {
            ForEach(disclosures, id: \.element.id) { index, disclosure in
                let selection = PresentationDisclosureSelection(
                    queryID: option.queryID,
                    credentialID: option.credentialID,
                    path: disclosure.path
                )

                VStack(alignment: .leading, spacing: 4) {
                    if disclosure.selectable && !isReadOnly {
                        Toggle(isOn: Binding(get: {
                            selectedDisclosureOptions.contains(selection)
                        }, set: { _ in
                            onToggleDisclosure(selection)
                        })) {
                            disclosureContent(index: index, disclosure: disclosure)
                        }
                        .toggleStyle(ReviewCheckboxToggleStyle())
                        .disabled(isLoading || !credentialSelected)
                        .accessibilityIdentifier(WalletAccessibilityID.presentationDisclosureToggle(selection.id))
                    } else {
                        disclosureContent(index: index, disclosure: disclosure)
                    }

                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if disclosure.id != disclosures.last?.element.id { Divider() }
            }
          }.padding(16)
        }
    }

    private func disclosureContent(index: Int, disclosure: PresentationDisclosure) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            disclosureLabel(index: index, disclosure: disclosure)
            Text(disclosure.disclosureStatusText).font(.caption2).foregroundStyle(.secondary)
        }
    }

    @ViewBuilder
    private func disclosureLabel(index: Int, disclosure: PresentationDisclosure) -> some View {
        if requestedDisclosureItems.indices.contains(index) {
            ClaimValueRow(item: requestedDisclosureItems[index])
        } else {
            DisclosureTextView(disclosure: disclosure)
        }
    }
}

private extension PresentationDisclosure {
    /// Why this claim is in the request, in the user's terms rather than the format's.
    var disclosureStatusText: String {
        if selectable { return "Optional disclosure" }
        if required { return "Required by request" }
        if selectivelyDisclosable { return "Selective disclosure" }
        return "Required by credential format"
    }
}

/// Fallback rendering for a disclosure the display normalizer did not produce a claim row for.
private struct DisclosureTextView: View {
    let disclosure: PresentationDisclosure

    var body: some View {
        CredentialDataRow(disclosure.name ?? disclosure.path) {
            Text(disclosure.displayValue ?? disclosure.valueJSON)
                .font(.body)
        }
    }
}
