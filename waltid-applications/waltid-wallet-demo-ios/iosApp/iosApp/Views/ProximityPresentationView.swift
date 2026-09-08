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
            if let route = viewModel.connectedRoute {
                ProximityConnectionDetails(route: route)
            }
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
                    onContinueWithAvailableConnection: viewModel.continueWithAvailableConnection,
                    onRemediate: viewModel.remediate
                )
            case .preparing:
                ProximityProgressContent(message: String(localized: "Preparing a secure presentation…"))
            case .engagementReady:
                ProximityEngagementContent(viewModel: viewModel)
            case .connecting:
                ProximityProgressContent(message: String(localized: "Connecting to reader…"))
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
                        ? String(localized: "No credential data was shared for this request.")
                        : String(localized: "The approved credential data was sent to the reader."),
                    onDismiss: viewModel.dismiss
                )
            case .noData:
                ProximityTerminalContent(
                    title: String(localized: "No data shared"),
                    message: String(localized: "No credential data was shared for this request."),
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
                    recoverable: error.recovery == .startNewSession,
                    onRetry: viewModel.restart,
                    onDismiss: viewModel.dismiss,
                    errorCode: error.code,
                    remediationActions: error.remediationActions,
                    onRemediate: viewModel.remediate
                )
            }
        } else {
            ProximityProgressContent(message: String(localized: "Checking this device…"))
        }
    }
}

private struct ProximityPrerequisiteContent: View {
    let capabilities: ProximityCapabilities
    let actionInProgress: ProximityRemediationAction?
    let onRetry: () -> Void
    let onContinueWithAvailableConnection: () -> Void
    let onRemediate: (ProximityPresentationRemediationAction) -> Void

    var body: some View {
        ReviewMetadataSection(
            title: primaryAction?.label ?? String(localized: "Action needed")
        ) {
            Text(message)
            if let action = primaryAction {
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
            if capabilities.mayStart {
                Button("Continue with available connection", action: onContinueWithAvailableConnection)
                    .frame(minHeight: 44).disabled(actionInProgress != nil)
            }
            if primaryAction == nil {
                Button("Check again", action: onRetry)
                    .buttonStyle(.borderedProminent)
                    .disabled(actionInProgress != nil)
                    .frame(maxWidth: .infinity)
                    .accessibilityIdentifier(WalletAccessibilityID.proximityRetryButton)
            }
        }
    }

    private var primaryAction: ProximityPresentationRemediationAction? {
        capabilities.remediationActions.first { $0 != .useSupportedDevice }
    }

    private var message: String {
        let selected = [capabilities.nfcEngagement, capabilities.qrEngagement, capabilities.bluetoothLowEnergy,
            capabilities.nfcRetrieval, capabilities.nfcV2Retrieval, capabilities.wifiAwareRetrieval]
        if let primaryAction, let error = selected.first(where: {
            $0.selected && $0.remediationActions.contains(primaryAction)
        })?.unavailable { return error.message }
        return capabilities.selectedUnavailableMessage ?? String(localized: "Nearby presentation is not available yet.")
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
    @ObservedObject var viewModel: ProximityPresentationViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            if let method = viewModel.displayedEngagement {
                Text(method == .qr ? String(localized: "Show QR code") : String(localized: "Hold near the reader"))
                    .font(.title2.bold()).accessibilityAddTraits(.isHeader)
                Text(method == .qr
                    ? String(localized: "Let the reader scan this code. Keep both devices nearby. You’ll review the request before sharing.")
                    : String(localized: "Keep your phone near the reader while it connects. You’ll review the request before sharing."))
                    .foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                if let payload = viewModel.qrPayload {
                    ProximityQRCode(payload: payload)
                        .aspectRatio(1, contentMode: .fit)
                        .frame(maxWidth: 320)
                        .frame(maxWidth: .infinity)
                        .accessibilityIdentifier(WalletAccessibilityID.proximityQRCode)
                }
                if viewModel.engagementChoices.count > 1 {
                    Button(method == .qr ? String(localized: "Hold near the reader instead") : String(localized: "Show QR code instead")) {
                        viewModel.showEngagement(method == .qr ? .nfc : .qr)
                    }
                    .frame(minHeight: 44)
                }
            } else {
                Text("Share in person").font(.title2.bold()).accessibilityAddTraits(.isHeader)
                Text("Choose how to connect to the reader.").foregroundStyle(.secondary)
                ForEach(Array(viewModel.engagementChoices.enumerated()), id: \.offset) { _, method in
                    Button { viewModel.showEngagement(method) } label: {
                        HStack(spacing: 16) {
                            Image(systemName: method == .qr ? "qrcode" : "wave.3.right")
                                .font(.title2).foregroundStyle(Color.accentColor)
                                .frame(width: 28).accessibilityHidden(true)
                            VStack(alignment: .leading, spacing: 6) {
                                Text(method == .qr ? String(localized: "Show QR code") : String(localized: "Hold near the reader"))
                                    .font(.headline).foregroundStyle(.primary)
                                Text(method == .qr ? String(localized: "Let the reader scan your screen.")
                                    : String(localized: "Bring your phone close to connect."))
                                    .font(.subheadline).foregroundStyle(.secondary)
                            }
                            .fixedSize(horizontal: false, vertical: true)
                            Spacer(minLength: 0)
                        }
                        .padding(.vertical, 16).frame(maxWidth: .infinity, alignment: .leading)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier(method == .qr ? "proximity-show-Qr" : "proximity-show-Nfc")
                    Divider()
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct ProximityReviewContent: View {
    let review: ProximityReview
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
                summary: review.readerAuthenticationSummary,
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
        VStack(alignment: .leading, spacing: 16) {
            Text(title).font(.title2.bold()).accessibilityAddTraits(.isHeader)
            Text(message).foregroundStyle(.secondary)
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
    var errorCode: String? = nil
    var remediationActions: [ProximityPresentationRemediationAction] = []
    var onRemediate: (ProximityPresentationRemediationAction) -> Void = { _ in }

    var body: some View {
        ReviewMetadataSection(title: String(localized: "Presentation failed")) {
            Text(message)
                .accessibilityIdentifier(WalletAccessibilityID.proximityError)
            if let action = remediationActions.first(where: { $0 != .retry && $0 != .useSupportedDevice }) {
                Button(action.label) { onRemediate(action) }.buttonStyle(.bordered)
            }
            if recoverable && remediationActions.allSatisfy({ $0 == .retry || $0 == .useSupportedDevice }) {
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

private extension ProximityRemediationAction {
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


private struct ProximityConnectionDetails: View {
    let route: ProximityPresentationConnectedRoute

    var body: some View {
        DisclosureGroup {
            VStack(alignment: .leading, spacing: 8) {
                Text("Started with: \(route.engagement == .qr ? String(localized: "Show QR code") : String(localized: "Hold near the reader"))")
                Text("Data connection: \(transport)")
            }
            .font(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
        } label: {
            Text("Connection details").font(.subheadline).frame(minHeight: 44)
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
