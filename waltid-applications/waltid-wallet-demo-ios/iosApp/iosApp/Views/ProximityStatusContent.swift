import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct ProximityProgressContent: View {
    let message: String

    var body: some View {
        VStack(spacing: 20) {
            ProgressView()
            Text(message).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 48)
        .accessibilityIdentifier(WalletAccessibilityID.proximityStatus)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.updatesFrequently)
    }
}

struct ProximityTerminalContent: View {
    let title: String
    let message: String
    var receipt: ProximitySharingReceipt? = nil
    var credentialDetailsByID: [String: CredentialDetails] = [:]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(title).font(.title2.bold()).accessibilityAddTraits(.isHeader)
            Text(message).foregroundStyle(.secondary)
            if let receipt {
                ReviewMetadataSection(title: "What was shared") {
                    WalletInstantText(receipt.completedAt)
                    if receipt.approvalTiming == .beforeConnection {
                        Text("Shared using your one-use prepared approval.")
                    }
                    ProximityDisclosureSummary(review: receipt.review, submission: receipt.submission, credentialDetailsByID: credentialDetailsByID, initiallyExpanded: true)
                }
            }

        }
    }
}

struct ProximityUnsatisfiedRequestContent: View {
    let request: ProximityUnsatisfiedRequest
    @State private var visibleDocuments = 5

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Reader authentication was not evaluated for this request.").font(.caption).foregroundStyle(.secondary)
            ForEach(Array(request.documents.prefix(visibleDocuments)), id: \.requestIndex) { document in
                ProximityUnsatisfiedDocumentContent(document: document)
            }
            if request.documents.count > visibleDocuments {
                Button("Show more requested documents") { visibleDocuments += 5 }
            }
        }
        .accessibilityIdentifier("proximity-unsatisfied-request")
        .onChange(of: request) { _ in visibleDocuments = 5 }
    }
}

private struct ProximityUnsatisfiedDocumentContent: View {
    let document: ProximityUnsatisfiedDocument
    @State private var visibleElements = 20

    var body: some View {
        ReviewMetadataSection(title: String(localized: "Document request \(document.requestIndex + 1)")) {
            Text(document.documentType).font(.caption).foregroundStyle(.secondary)
            ForEach(Array(document.requestedElements.prefix(visibleElements).enumerated()), id: \.offset) { _, element in
                VStack(alignment: .leading, spacing: 4) {
                    Text(CredentialDisplayVocabulary.humanizedLabel(element.elementIdentifier)).fontWeight(.semibold)
                    Text(element.namespace).font(.caption).foregroundStyle(.secondary)
                    if element.intentToRetain {
                        Text("Reader intends to retain this data").font(.caption)
                    }
                }
            }
            if document.requestedElements.count > visibleElements {
                Button("Show more requested fields") { visibleElements += 20 }
            }
        }
        .onChange(of: document) { _ in visibleElements = 20 }
    }
}

struct ProximityFailureContent: View {
    let message: String

    var body: some View {
        ReviewMetadataSection(title: String(localized: "Presentation failed")) {
            Text(message).accessibilityIdentifier(WalletAccessibilityID.proximityError)
        }
    }
}

/// Decisions remain available while a long receipt or error scrolls.
struct ProximityOutcomeActions: View {
    @ObservedObject var viewModel: ProximityPresentationViewModel

    var body: some View {
        if let remediation {
            WalletActions(primary: WalletAction(remediation.label, enabled: viewModel.hostActionInProgress == nil) {
                viewModel.remediate(remediation)
            }, secondary: done)
        } else if viewModel.startupFailed || error?.recovery == .startNewSession {
            WalletActions(primary: WalletAction(String(localized: "Try again"), identifier: WalletAccessibilityID.proximityRetryButton,
                perform: viewModel.restart), secondary: done)
        } else {
            WalletActions(primary: done, secondary: prepareAnother)
        }
    }

    private var done: WalletAction {
        WalletAction(String(localized: "Done"), enabled: viewModel.hostActionInProgress == nil,
            identifier: WalletAccessibilityID.proximityDoneButton, perform: viewModel.requestClose)
    }

    private var error: ProximityError? {
        if case .failed(let error) = viewModel.sessionState { return error }
        return nil
    }

    private var remediation: ProximityRemediationAction? {
        error?.remediationActions.first { $0 != .retry && $0 != .useSupportedDevice }
    }

    private var prepareAnother: WalletAction? {
        guard case .completed = viewModel.sessionState, viewModel.recentPlan?.isExpired == false else { return nil }
        return WalletAction(String(localized: "Prepare another share"), identifier: "proximity-prepare-again",
            perform: { viewModel.reviewRecentRequest() })
    }
}

struct ProximityConnectionDetails: View {
    let route: ProximityConnectedRoute

    var body: some View {
        MetadataDisclosure(title: String(localized: "Connection details"), initiallyExpanded: false) {
            VStack(alignment: .leading, spacing: 8) {
                Text("Started with: \(route.engagement == .qr ? String(localized: "Show QR code") : String(localized: "Hold near the reader"))")
                Text("Data connection: \(transport)")
            }
            .font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var transport: String {
        switch route.transport {
        case .bluetoothLowEnergy: String(localized: "Bluetooth")
        case .nfc: String(localized: "NFC")
        case .wifiAware: String(localized: "Wi-Fi Aware")
        }
    }
}
