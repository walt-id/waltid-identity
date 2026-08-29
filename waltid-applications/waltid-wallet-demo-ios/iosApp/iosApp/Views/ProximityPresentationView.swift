import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct ProximityPresentationView: View {
    @ObservedObject var viewModel: ProximityPresentationViewModel
    let credentialDetailsByID: [String: CredentialDetails]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            if let message = viewModel.actionErrorMessage {
                StatusBannerView(
                    message: String(localized: "Action failed: \(message)"),
                    isLoading: false,
                    isError: true
                )
            }
            content
        }
        .accessibilityIdentifier(WalletAccessibilityID.proximityScreen)
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.startupFailed {
            ProximityFailureContent(
                message: String(localized: "The in-person presentation could not be started"),
                recoverable: true,
                onRetry: viewModel.restart,
                onDismiss: viewModel.dismiss
            )
        } else if let state = viewModel.sessionState {
            switch state {
            case .checkingPrerequisites(let capabilities):
                ProximityPrerequisiteContent(
                    capabilities: capabilities,
                    actionInProgress: viewModel.hostActionInProgress,
                    onRetry: viewModel.retryPrerequisites,
                    onRemediate: viewModel.remediate
                )
            case .preparing:
                ProximityProgressContent(message: String(localized: "Preparing a secure presentation…"))
            case .engagementReady(let engagements):
                ProximityEngagementContent(engagements: engagements, connecting: false)
            case .connecting(let engagements):
                ProximityEngagementContent(engagements: engagements, connecting: true)
            case .awaitingRequest:
                ProximityProgressContent(
                    message: String(localized: "Connected. Waiting for the reader's request…")
                )
            case .reviewRequired(let review):
                ProximityReviewContent(
                    review: review,
                    selections: viewModel.selections,
                    credentialDetailsByID: credentialDetailsByID,
                    onSelectCredential: viewModel.selectCredential,
                    onToggleElement: viewModel.toggleElement,
                    continueAfterResponse: viewModel.continueAfterResponse,
                    onContinueAfterResponseChange: viewModel.setContinueAfterResponse
                )
            case .authorizingHolderKey:
                ProximityProgressContent(message: String(localized: "Confirming the selected credentials…"))
            case .sendingResponse:
                ProximityProgressContent(message: String(localized: "Sharing the approved data…"))
            case .awaitingNextRequest:
                ProximityProgressContent(
                    message: String(localized: "Response sent. Waiting for another request…")
                )
            case .terminating:
                ProximityProgressContent(message: String(localized: "Closing the secure connection…"))
            case .completed(_, let declined):
                ProximityTerminalContent(
                    title: declined
                        ? String(localized: "Request declined")
                        : String(localized: "Presentation complete"),
                    message: declined
                        ? String(localized: "No credential data was shared.")
                        : String(localized: "The approved credential data was sent to the reader."),
                    onDismiss: viewModel.dismiss
                )
            case .cancelled:
                ProximityTerminalContent(
                    title: String(localized: "Presentation cancelled"),
                    message: String(localized: "The nearby presentation was closed."),
                    onDismiss: viewModel.dismiss
                )
            case .failed(let error):
                ProximityFailureContent(
                    message: error.message,
                    recoverable: error.recoverable,
                    onRetry: viewModel.restart,
                    onDismiss: viewModel.dismiss
                )
            }
        } else {
            ProximityProgressContent(message: String(localized: "Checking this device…"))
        }
    }
}

private struct ProximityPrerequisiteContent: View {
    let capabilities: ProximityPresentationCapabilities
    let actionInProgress: ProximityPresentationRemediationAction?
    let onRetry: () -> Void
    let onRemediate: (ProximityPresentationRemediationAction) -> Void

    var body: some View {
        ReviewMetadataSection(
            title: capabilities.mayStart
                ? String(localized: "Device ready")
                : String(localized: "Action needed")
        ) {
            Text(message)
            ForEach(Array(capabilities.remediationActions.enumerated()), id: \.offset) { _, action in
                Button {
                    onRemediate(action)
                } label: {
                    HStack {
                        if actionInProgress == action {
                            ProgressView()
                        }
                        Text(action.label)
                            .frame(maxWidth: .infinity)
                    }
                }
                .buttonStyle(.bordered)
                .disabled(actionInProgress != nil)
            }
            Button("Check again", action: onRetry)
                .buttonStyle(.borderedProminent)
                .disabled(actionInProgress != nil)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier(WalletAccessibilityID.proximityRetryButton)
        }
    }

    private var message: String {
        if capabilities.mayStart {
            return String(localized: "This device is ready for nearby presentation.")
        }
        return capabilities.selectedUnavailableMessage
            ?? String(localized: "Nearby presentation is not available yet.")
    }
}

private extension ProximityPresentationCapabilities {
    var selectedUnavailableMessage: String? {
        [
            nfcEngagement,
            bluetoothLowEnergy,
            nfcRetrieval,
            nfcV2Retrieval,
            qrEngagement,
            wifiAwareRetrieval,
        ].first { $0.selected && $0.unavailable != nil }?.unavailable?.message
    }
}

private struct ProximityEngagementContent: View {
    let engagements: [ProximityPresentationEngagement]
    let connecting: Bool

    var body: some View {
        VStack(spacing: 16) {
            Text(title)
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(
                connecting
                    ? String(localized: "Keep this screen open while the secure connection is established.")
                    : guidance
            )
            .multilineTextAlignment(.center)
            if let payload = qrPayload {
                ProximityQRCode(payload: payload)
                    .frame(maxWidth: 320, maxHeight: 320)
                    .accessibilityIdentifier(WalletAccessibilityID.proximityQRCode)
            }
            if connecting {
                ProgressView()
            }
        }
        .frame(maxWidth: .infinity)
    }

    private var qrPayload: String? {
        engagements.compactMap { engagement in
            guard case .qr(let payload) = engagement else { return nil }
            return payload
        }.first
    }

    private var hasNFC: Bool {
        engagements.contains { engagement in
            if case .nfc = engagement { return true }
            return false
        }
    }

    private var title: String {
        if connecting { return String(localized: "Reader detected") }
        if qrPayload != nil && hasNFC { return String(localized: "Scan or hold near the reader") }
        if qrPayload != nil { return String(localized: "Scan with the reader") }
        return String(localized: "Hold near the reader")
    }

    private var guidance: String {
        if qrPayload != nil && hasNFC {
            return String(localized: "Scan this QR code or hold this iPhone near a compatible reader.")
        }
        if qrPayload != nil {
            return String(localized: "Open a compatible reader and scan this QR code. Keep both devices nearby.")
        }
        return String(localized: "Hold this iPhone near a compatible reader and keep this screen open.")
    }
}

private struct ProximityReviewContent: View {
    let review: ProximityPresentationReview
    let selections: [ProximityDocumentSelection]
    let credentialDetailsByID: [String: CredentialDetails]
    let onSelectCredential: (Int, String) -> Void
    let onToggleElement: (Int, ProximityElementReference) -> Void
    let continueAfterResponse: Bool
    let onContinueAfterResponseChange: (Bool) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            ProximityReaderMetadataCard(
                authentications: review.readerAuthentication,
                documents: review.documents,
                credentialDetailsByID: credentialDetailsByID
            )

            ForEach(review.useCases) { useCase in
                ReviewMetadataSection(title: String(localized: "Reader-stated purpose")) {
                    Text("Use case \(useCase.index + 1)\(useCase.mandatory ? " (mandatory)" : "")")
                        .font(.headline)
                    if useCase.purposeHints.isEmpty {
                        Text("No purpose hint was supplied.")
                    } else {
                        ForEach(Array(useCase.purposeHints.enumerated()), id: \.offset) { _, hint in
                            Text("\(hint.type): \(hint.code) (claimed by reader)")
                        }
                    }
                }
            }

            ForEach(review.applicationAuthorizations, id: \.profileID) { authorization in
                ReviewMetadataSection(title: authorization.displayTitle) {
                    Text("Validated application request")
                        .font(.headline)
                    MetadataDetailList(
                        items: authorization.details.map {
                            MetadataDetailItem(label: $0.label, value: $0.value)
                        }
                    )
                }
            }

            ForEach(review.documents) { document in
                ProximityDocumentContent(
                    document: document,
                    selection: selections.first(where: { $0.requestIndex == document.requestIndex }),
                    credentialDetailsByID: credentialDetailsByID,
                    onSelectCredential: onSelectCredential,
                    onToggleElement: onToggleElement
                )
            }

            Toggle(
                isOn: Binding(
                    get: { continueAfterResponse },
                    set: onContinueAfterResponseChange
                )
            ) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Stay connected for another request")
                    Text("You will review and approve each new request separately.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .accessibilityIdentifier(WalletAccessibilityID.proximityContinueAfterResponse)
        }
        .accessibilityIdentifier(WalletAccessibilityID.proximityReview)
    }
}

private struct ProximityReaderMetadataCard: View {
    let authentications: [ProximityReaderAuthentication]
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

    private var mostSevereAuthentication: ProximityReaderAuthentication? {
        authentications.max { $0.summarySeverity < $1.summarySeverity }
    }

    private var authenticationSummary: String? {
        guard let authentication = mostSevereAuthentication else { return nil }
        if authentication.validity == .malformed || authentication.validity == .invalid {
            return authentication.validity.label
        }
        if authentication.trust == .revoked {
            return authentication.trust.label
        }
        if authentications.contains(where: { $0.validity == .absent }) {
            return String(localized: "Authentication missing for part of the request")
        }
        return authentication.trust.label
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
                    if let authenticationSummary {
                        Text(authenticationSummary)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
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
        case .document:
            guard let requestIndex = authentication.documentRequestIndex else {
                return String(localized: "Document request")
            }
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

private extension ProximityReaderAuthentication {
    var summarySeverity: Int {
        switch (validity, trust) {
        case (.malformed, _): 7
        case (.invalid, _): 6
        case (.valid, .revoked): 5
        case (.absent, _): 4
        case (.valid, .validButUntrusted): 3
        case (.valid, .notEvaluated): 2
        case (.valid, .trusted): 1
        }
    }
}

private struct ProximityDocumentContent: View {
    let document: ProximityDocumentReview
    let selection: ProximityDocumentSelection?
    let credentialDetailsByID: [String: CredentialDetails]
    let onSelectCredential: (Int, String) -> Void
    let onToggleElement: (Int, ProximityElementReference) -> Void

    var body: some View {
        ReviewMetadataSection(title: String(localized: "Credential to share")) {
            if document.credentialOptions.count > 1 {
                Text("Choose a credential").font(.headline)
            }
            ForEach(document.credentialOptions) { credential in
                let details = credentialDetailsByID[credential.credentialID]
                Button {
                    onSelectCredential(document.requestIndex, credential.credentialID)
                } label: {
                    HStack(alignment: .center, spacing: 12) {
                        if document.credentialOptions.count > 1 {
                            Image(
                                systemName: selection?.credentialID == credential.credentialID
                                    ? "largecircle.fill.circle" : "circle"
                            )
                        }
                        if let details {
                            CredentialCardView(details: details, compact: true)
                        } else {
                            VStack(alignment: .leading, spacing: 3) {
                                Text(credential.label ?? String(localized: "Wallet credential"))
                                if let issuer = credential.issuer {
                                    Text(issuer).font(.caption).foregroundStyle(.secondary)
                                }
                                Text("Valid until \(credential.validUntil.formatted(date: .abbreviated, time: .omitted))")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier(
                    WalletAccessibilityID.proximityCredential(
                        requestIndex: document.requestIndex,
                        credentialID: credential.credentialID
                    )
                )
            }
            if let credential = selectedCredential {
                let details = credentialDetailsByID[credential.credentialID]
                Divider()
                Text("Data to share").font(.headline)
                ForEach(Array(credential.requestedElements.enumerated()), id: \.offset) { _, element in
                    let reference = ProximityElementReference(
                        namespace: element.namespace,
                        elementIdentifier: element.elementIdentifier
                    )
                    Toggle(isOn: binding(for: reference)) {
                        VStack(alignment: .leading, spacing: 3) {
                            let claims = details?.mdocClaims(
                                namespace: element.namespace,
                                elementIdentifier: element.elementIdentifier
                            ) ?? []
                            if !claims.isEmpty {
                                ForEach(claims) { claim in
                                    ClaimValueRow(item: claim)
                                }
                            } else {
                                Text(CredentialDisplayVocabulary.humanizedLabel(element.elementIdentifier))
                                    .font(.caption.weight(.semibold))
                                Text("Value preview unavailable")
                                    .font(.caption)
                                    .foregroundStyle(.red)
                            }
                            if element.intentToRetain {
                                Text("Reader intends to retain this data")
                                    .font(.caption)
                                    .foregroundStyle(.red)
                            }
                        }
                    }
                    .toggleStyle(ReviewCheckboxToggleStyle())
                    .accessibilityIdentifier(
                        WalletAccessibilityID.proximityElement(
                            requestIndex: document.requestIndex,
                            namespace: element.namespace,
                            elementIdentifier: element.elementIdentifier
                        )
                    )
                }
                MetadataDisclosure(title: "Technical details", initiallyExpanded: false) {
                    MetadataDetailList(items: technicalDetails(for: credential))
                }
            }
        }
    }

    private var selectedCredential: ProximityCredentialOption? {
        document.credentialOptions.first { $0.credentialID == selection?.credentialID }
    }

    private func binding(for element: ProximityElementReference) -> Binding<Bool> {
        Binding(
            get: { selection?.disclosedElements.contains(element) == true },
            set: { _ in onToggleElement(document.requestIndex, element) }
        )
    }

    private func technicalDetails(for credential: ProximityCredentialOption) -> [MetadataDetailItem] {
        [
            MetadataDetailItem(label: "Document type", value: document.documentType),
            MetadataDetailItem(
                label: "Device authentication",
                value: credential.deviceAuthentication.label
            ),
        ] + credential.requestedElements.map { element in
            MetadataDetailItem(
                label: "Requested element",
                value: "\(element.namespace) / \(element.elementIdentifier)"
            )
        }
    }
}

private extension CredentialDetails {
    func mdocClaims(namespace: String, elementIdentifier: String) -> [ClaimItem] {
        groups
            .flatMap(\.items)
            .filter { claim in
                claim.pathComponents.first == namespace
                    && claim.pathComponents.dropFirst().first == elementIdentifier
            }
    }
}

private struct ProximityProgressContent: View {
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

private struct ProximityTerminalContent: View {
    let title: String
    let message: String
    let onDismiss: () -> Void

    var body: some View {
        ReviewMetadataSection(title: title) {
            Text(message)
            Button("Done", action: onDismiss)
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier(WalletAccessibilityID.proximityDoneButton)
        }
    }
}

private struct ProximityFailureContent: View {
    let message: String
    let recoverable: Bool
    let onRetry: () -> Void
    let onDismiss: () -> Void

    var body: some View {
        ReviewMetadataSection(title: String(localized: "Presentation failed")) {
            Text(message)
                .accessibilityIdentifier(WalletAccessibilityID.proximityError)
            if recoverable {
                Button("Try again", action: onRetry)
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
                    .accessibilityIdentifier(WalletAccessibilityID.proximityRetryButton)
            }
            Button("Done", action: onDismiss)
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier(WalletAccessibilityID.proximityDoneButton)
        }
    }
}

private struct ProximityQRCode: View {
    let payload: String

    var body: some View {
        Group {
            if let image = WalletQRCodeRenderer.proximityImage(payload: payload) {
                WalletQRCodeView(image: image)
            } else {
                Text("The device engagement QR code could not be rendered.")
                    .foregroundStyle(.red)
            }
        }
        .padding(20)
        .background(Color.white, in: RoundedRectangle(cornerRadius: 16))
        .accessibilityLabel("Device engagement QR code")
    }
}

@MainActor
final class ProximityScreenPolicy: ObservableObject {
    private var originalIdleTimerDisabled: Bool?
    private var originalBrightness: CGFloat?

    func update(active: Bool, qrVisible: Bool) {
        guard active else {
            restore()
            return
        }
        if originalIdleTimerDisabled == nil {
            originalIdleTimerDisabled = UIApplication.shared.isIdleTimerDisabled
        }
        UIApplication.shared.isIdleTimerDisabled = true

        if qrVisible {
            if originalBrightness == nil {
                originalBrightness = UIScreen.main.brightness
            }
            UIScreen.main.brightness = 1
        } else if let originalBrightness {
            UIScreen.main.brightness = originalBrightness
            self.originalBrightness = nil
        }
    }

    func restore() {
        if let originalIdleTimerDisabled {
            UIApplication.shared.isIdleTimerDisabled = originalIdleTimerDisabled
            self.originalIdleTimerDisabled = nil
        }
        if let originalBrightness {
            UIScreen.main.brightness = originalBrightness
            self.originalBrightness = nil
        }
    }

    deinit {
        MainActor.assumeIsolated { restore() }
    }
}

private extension ProximityPresentationRemediationAction {
    var label: String {
        switch self {
        case .requestBluetoothPermission: String(localized: "Allow Bluetooth")
        case .openApplicationSettings: String(localized: "Open app settings")
        case .enableBluetooth: String(localized: "Enable Bluetooth")
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

private extension ProximityDeviceAuthenticationMethod {
    var label: String {
        switch self {
        case .signature: String(localized: "Device signature")
        case .mac: String(localized: "Device MAC")
        }
    }
}
