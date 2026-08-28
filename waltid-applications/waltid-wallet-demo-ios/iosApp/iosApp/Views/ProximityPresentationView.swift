import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct ProximityPresentationView: View {
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.walletDemoBranding) private var branding
    @ObservedObject var viewModel: ProximityPresentationViewModel
    @StateObject private var screenPolicy = ProximityScreenPolicy()

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    if let message = viewModel.actionErrorMessage {
                        ProximityMessageCard(
                            title: String(localized: "Action failed"),
                            message: message,
                            isError: true
                        )
                    }
                    content
                }
                .padding()
            }
            .navigationTitle("Present in person")
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    if !viewModel.isTerminal
                        && (viewModel.sessionState == nil
                            || viewModel.sessionState?.legalActions.contains(.cancel) == true) {
                        Button("Cancel", action: viewModel.cancel)
                            .accessibilityIdentifier(WalletAccessibilityID.proximityCancelButton)
                    }
                }
            }
        }
        .navigationViewStyle(.stack)
        .accessibilityIdentifier(WalletAccessibilityID.proximityScreen)
        .onAppear {
            screenPolicy.update(active: viewModel.active && !viewModel.isTerminal, qrVisible: viewModel.qrPayload != nil)
        }
        .onDisappear {
            screenPolicy.restore()
            viewModel.dismiss()
        }
        .onChange(of: viewModel.qrPayload != nil) { qrVisible in
            screenPolicy.update(active: viewModel.active && !viewModel.isTerminal, qrVisible: qrVisible)
        }
        .onChange(of: viewModel.active) { active in
            screenPolicy.update(active: active && !viewModel.isTerminal, qrVisible: viewModel.qrPayload != nil)
        }
        .onChange(of: viewModel.isTerminal) { terminal in
            screenPolicy.update(active: viewModel.active && !terminal, qrVisible: viewModel.qrPayload != nil)
        }
        .onChange(of: scenePhase) { phase in
            let foreground = phase == .active
            screenPolicy.update(
                active: foreground && viewModel.active && !viewModel.isTerminal,
                qrVisible: foreground && viewModel.qrPayload != nil
            )
            if !foreground { viewModel.handleLifecycleInterruption() }
        }
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
                    canApprove: viewModel.canApprove,
                    onSelectCredential: viewModel.selectCredential,
                    onToggleElement: viewModel.toggleElement,
                    continueAfterResponse: viewModel.continueAfterResponse,
                    onContinueAfterResponseChange: viewModel.setContinueAfterResponse,
                    onApprove: { viewModel.approve() },
                    onDecline: viewModel.decline
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
        ProximitySectionCard(
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
            return String(localized: "This device can create a QR code and present over Bluetooth.")
        }
        return capabilities.bluetoothLowEnergy.unavailable?.message
            ?? capabilities.qrEngagement.unavailable?.message
            ?? String(localized: "Nearby presentation is not available yet.")
    }
}

private struct ProximityEngagementContent: View {
    let engagements: [ProximityPresentationEngagement]
    let connecting: Bool

    var body: some View {
        VStack(spacing: 16) {
            Text(connecting ? String(localized: "Reader detected") : String(localized: "Scan with the reader"))
                .font(.title2.bold())
                .multilineTextAlignment(.center)
                .accessibilityAddTraits(.isHeader)
            Text(
                connecting
                    ? String(localized: "Keep this screen open while a secure Bluetooth connection is established.")
                    : String(localized: "Open a compatible reader and scan this QR code. Keep both devices nearby.")
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
}

private struct ProximityReviewContent: View {
    @Environment(\.walletDemoBranding) private var branding
    let review: ProximityPresentationReview
    let selections: [ProximityDocumentSelection]
    let canApprove: Bool
    let onSelectCredential: (Int, String) -> Void
    let onToggleElement: (Int, ProximityElementReference) -> Void
    let continueAfterResponse: Bool
    let onContinueAfterResponseChange: (Bool) -> Void
    let onApprove: () -> Void
    let onDecline: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text("Review the request")
                .font(.title2.bold())
                .accessibilityAddTraits(.isHeader)
            Text("Only the checked data will be shared after you approve.")

            readerContent

            ForEach(review.useCases) { useCase in
                ProximitySectionCard(title: String(localized: "Reader-stated purpose")) {
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
                ProximitySectionCard(title: authorization.displayTitle) {
                    Text("Validated application request")
                        .font(.headline)
                    ForEach(authorization.details) { detail in
                        ProximityLabelValue(label: detail.label, value: detail.value)
                    }
                }
            }

            ForEach(review.documents) { document in
                ProximityDocumentContent(
                    document: document,
                    selection: selections.first(where: { $0.requestIndex == document.requestIndex }),
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

            Button("Approve and share", action: onApprove)
                .buttonStyle(.borderedProminent)
                .tint(branding.primary)
                .disabled(!canApprove)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier(WalletAccessibilityID.proximityApproveButton)

            Button("Decline request", action: onDecline)
                .buttonStyle(.bordered)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier(WalletAccessibilityID.proximityDeclineButton)
        }
        .accessibilityIdentifier(WalletAccessibilityID.proximityReview)
    }

    @ViewBuilder
    private var readerContent: some View {
        if review.readerAuthentication.isEmpty {
            ProximitySectionCard(title: String(localized: "Reader")) {
                Text("This reader did not provide authenticated identity information.")
            }
        } else {
            ProximitySectionCard(title: String(localized: "Reader authentication")) {
                ForEach(Array(review.readerAuthentication.enumerated()), id: \.offset) { index, authentication in
                    if index > 0 { Divider() }
                    Text(authentication.displayName ?? String(localized: "Unnamed reader"))
                        .font(.headline)
                    ProximityLabelValue(
                        label: String(localized: "Applies to"),
                        value: scopeDescription(authentication)
                    )
                    ProximityLabelValue(
                        label: String(localized: "Signature"),
                        value: authentication.validity.label
                    )
                    ProximityLabelValue(
                        label: String(localized: "Certificate path"),
                        value: authentication.certificatePath.label
                    )
                    ProximityLabelValue(
                        label: String(localized: "Revocation"),
                        value: authentication.revocation.label
                    )
                    ProximityLabelValue(
                        label: String(localized: "RICAL evidence"),
                        value: authentication.rical.label
                    )
                    ProximityLabelValue(
                        label: String(localized: "Trust"),
                        value: authentication.trust.label
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
            if let documentType = review.documents.first(where: { $0.requestIndex == requestIndex })?.documentType {
                return String(localized: "Document: \(documentType)")
            }
            return String(localized: "Document request \(requestIndex + 1)")
        }
    }
}

private struct ProximityDocumentContent: View {
    let document: ProximityDocumentReview
    let selection: ProximityDocumentSelection?
    let onSelectCredential: (Int, String) -> Void
    let onToggleElement: (Int, ProximityElementReference) -> Void

    var body: some View {
        ProximitySectionCard(title: String(localized: "Requested document")) {
            ProximityLabelValue(
                label: String(localized: "Document type"),
                value: document.documentType
            )
            if document.credentialOptions.count > 1 {
                Text("Choose a credential").font(.headline)
            }
            ForEach(document.credentialOptions) { credential in
                Button {
                    onSelectCredential(document.requestIndex, credential.credentialID)
                } label: {
                    HStack(alignment: .top) {
                        Image(
                            systemName: selection?.credentialID == credential.credentialID
                                ? "largecircle.fill.circle" : "circle"
                        )
                        VStack(alignment: .leading, spacing: 3) {
                            Text(credential.label ?? String(localized: "Wallet credential"))
                            if let issuer = credential.issuer {
                                Text(issuer).font(.caption).foregroundStyle(.secondary)
                            }
                            Text("Valid until \(credential.validUntil.formatted(date: .abbreviated, time: .omitted))")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
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
                Divider()
                Text("Data to share").font(.headline)
                ForEach(Array(credential.requestedElements.enumerated()), id: \.offset) { _, element in
                    let reference = ProximityElementReference(
                        namespace: element.namespace,
                        elementIdentifier: element.elementIdentifier
                    )
                    Toggle(isOn: binding(for: reference)) {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(element.elementIdentifier)
                            Text(element.namespace).font(.caption).foregroundStyle(.secondary)
                            if element.intentToRetain {
                                Text("Reader intends to retain this data")
                                    .font(.caption)
                                    .foregroundStyle(.red)
                            }
                        }
                    }
                    .accessibilityIdentifier(
                        WalletAccessibilityID.proximityElement(
                            requestIndex: document.requestIndex,
                            namespace: element.namespace,
                            elementIdentifier: element.elementIdentifier
                        )
                    )
                }
                ProximityLabelValue(
                    label: String(localized: "Device authentication"),
                    value: credential.deviceAuthentication.label
                )
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
        ProximitySectionCard(title: title) {
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
        ProximitySectionCard(title: String(localized: "Presentation failed")) {
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

private struct ProximityMessageCard: View {
    let title: String
    let message: String
    let isError: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            Text(message)
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            (isError ? Color.red : Color.secondary).opacity(0.1),
            in: RoundedRectangle(cornerRadius: 12)
        )
    }
}

private struct ProximitySectionCard<Content: View>: View {
    let title: String
    @ViewBuilder let content: Content

    init(title: String, @ViewBuilder content: () -> Content) {
        self.title = title
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title)
                .font(.headline)
                .accessibilityAddTraits(.isHeader)
            content
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct ProximityLabelValue: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value).multilineTextAlignment(.trailing)
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
private final class ProximityScreenPolicy: ObservableObject {
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
