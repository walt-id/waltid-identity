import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct ProximityReaderMetadataCard: View {
    let authentications: [ProximityReaderAuthentication]
    let summary: ProximityReaderAuthenticationSummary
    let documents: [ProximityDocumentReview]
    let credentialDetailsByID: [String: CredentialDetails]
    @State private var isExpanded = false

    private var suppliedAuthentications: [ProximityReaderAuthentication] {
        authentications.filter { $0.validity != .absent }
    }

    private var readerDisplayName: String {
        let displayNames: Set<String> = Set(suppliedAuthentications.compactMap { authentication -> String? in
            guard let displayName = authentication.displayName?
                .trimmingCharacters(in: .whitespacesAndNewlines),
                !displayName.isEmpty else {
                return nil
            }
            return displayName
        })
        switch displayNames.count {
        case 0:
            return String(localized: "Reader identity unavailable")
        case 1:
            return displayNames.first!
        default:
            return String(localized: "Multiple reader identities")
        }
    }

    private var authenticationSummary: String {
        switch summary {
        case .absent: ProximityReaderAuthenticationValidity.absent.label
        case .malformed: ProximityReaderAuthenticationValidity.malformed.label
        case .invalid: ProximityReaderAuthenticationValidity.invalid.label
        case .revoked: ProximityReaderTrustState.revoked.label
        case .partial: String(localized: "Authentication missing for part of the request")
        case .validButUntrusted: ProximityReaderTrustState.validButUntrusted.label
        case .trusted: ProximityReaderTrustState.trusted.label
        }
    }

    var body: some View {
        if suppliedAuthentications.isEmpty {
            ReviewMetadataSection(
                title: "Verifier",
                titleAccessibilityIdentifier: WalletAccessibilityID.proximityReaderSection
            ) {
                Text("Reader identity not provided")
                    .font(.headline)
                Text("This request was not signed by the reader.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        } else {
            ExpandableMetadataCard(
                title: "Verifier",
                titleAccessibilityIdentifier: WalletAccessibilityID.proximityReaderSection,
                toggleAccessibilityIdentifier: WalletAccessibilityID.proximityReaderDetailsToggle,
                isExpanded: $isExpanded
            ) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(readerDisplayName)
                        .font(.headline)
                    Text(authenticationSummary)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } details: {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(Array(authentications.enumerated()), id: \.offset) { index, authentication in
                        if index > 0 { Divider() }
                        Text(authentication.displayName ?? String(localized: "Reader identity unavailable"))
                            .font(.headline)
                        MetadataDetailList(
                            items: [
                                MetadataDetailItem(
                                    label: String(localized: "Applies to"),
                                    value: scopeDescription(authentication)
                                ),
                                MetadataDetailItem(
                                    label: String(localized: "Signature"),
                                    value: authentication.validity.label
                                ),
                                MetadataDetailItem(
                                    label: String(localized: "Certificate path"),
                                    value: authentication.certificatePath.label
                                ),
                                MetadataDetailItem(
                                    label: String(localized: "Revocation"),
                                    value: authentication.revocation.label
                                ),
                                MetadataDetailItem(
                                    label: String(localized: "RICAL evidence"),
                                    value: authentication.rical.label
                                ),
                                MetadataDetailItem(
                                    label: String(localized: "Trust"),
                                    value: authentication.trust.label
                                ),
                            ]
                        )
                        if let reason = authentication.reason {
                            Text(reason).font(.caption)
                        }
                        if authentication.validity == .valid && authentication.trust != .trusted {
                            Text("A valid signature does not by itself make this reader trusted.")
                                .font(.caption)
                                .foregroundStyle(.red)
                        }
                    }
                }
                .accessibilityIdentifier(WalletAccessibilityID.proximityReaderDetails)
            }
        }
    }

    private func scopeDescription(_ authentication: ProximityReaderAuthentication) -> String {
        switch authentication.scope {
        case .wholeRequest:
            return String(localized: "Whole request")
        case .document(let index):
            let requestIndex = index.value
            if let document = documents.first(where: { $0.requestIndex == requestIndex }) {
                let displayName = document.credentialOptions.compactMap { option in
                    credentialDetailsByID[option.credentialID]?.cardSummary.title
                }.first ?? document.documentType
                return String(localized: "Document: \(displayName)")
            }
            return String(localized: "Document request \(requestIndex + 1)")
        }
    }
}

extension ProximityRemediationAction {
    var label: String {
        switch self {
        case .requestBluetoothPermission: String(localized: "Allow Bluetooth")
        case .requestNearbyWifiPermission: String(localized: "Allow nearby Wi-Fi")
        case .requestLocalNetworkPermission: String(localized: "Allow local network")
        case .openApplicationSettings: String(localized: "Open app settings")
        case .enableBluetooth: String(localized: "Enable Bluetooth")
        case .enableWifi: String(localized: "Open Wi-Fi settings")
        case .enableNFC: String(localized: "Enable NFC")
        case .useSupportedDevice: String(localized: "Use a supported device")
        case .retry: String(localized: "Check again")
        }
    }
}

private extension ProximityReaderAuthenticationValidity {
    var label: String {
        switch self {
        case .absent: String(localized: "Absent")
        case .malformed: String(localized: "Malformed")
        case .invalid: String(localized: "Invalid")
        case .valid: String(localized: "Valid")
        }
    }
}

private extension ProximityReaderTrustState {
    var label: String {
        switch self {
        case .notEvaluated: String(localized: "Not evaluated")
        case .validButUntrusted: String(localized: "Valid but untrusted")
        case .revoked: String(localized: "Revoked")
        case .trusted: String(localized: "Trusted")
        }
    }
}

private extension ProximityReaderCertificatePathState {
    var label: String {
        switch self {
        case .notEvaluated: String(localized: "Not evaluated")
        case .unknownAuthority: String(localized: "Unknown authority")
        case .invalid: String(localized: "Invalid")
        case .valid: String(localized: "Valid")
        }
    }
}

private extension ProximityReaderRevocationState {
    var label: String {
        switch self {
        case .notChecked: String(localized: "Not checked")
        case .good: String(localized: "Good")
        case .revoked: String(localized: "Revoked")
        case .indeterminate: String(localized: "Indeterminate")
        }
    }
}

private extension ProximityRICALState {
    var label: String {
        switch self {
        case .notEvaluated: String(localized: "Not evaluated")
        case .unavailable: String(localized: "Unavailable")
        case .invalid: String(localized: "Invalid")
        case .noMatchingAuthority: String(localized: "No matching authority")
        case .matched: String(localized: "Matched authority")
        }
    }
}

extension ProximityDeviceAuthenticationMethod {
    var label: String {
        switch self {
        case .signature: String(localized: "Device signature")
        case .mac: String(localized: "Device MAC")
        }
    }
}
