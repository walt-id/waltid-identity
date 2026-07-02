import Foundation
import SwiftUI
import WaltidWalletSDK

public struct ContentView: View {
    @StateObject private var model = WalletSDKSampleModel()

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("WaltidWalletSDK")
                .font(.headline)
                .accessibilityIdentifier("sdk-title")

            Text(model.statusText)
                .font(.caption)
                .accessibilityIdentifier(model.statusAccessibilityIdentifier)

            Button {
                model.bootstrapAndListCredentials()
            } label: {
                Label("Bootstrap", systemImage: "key")
            }
            .disabled(model.isRunning)
            .accessibilityIdentifier("sdk-bootstrap-button")

            if let credentialText = model.credentialText {
                Text(credentialText)
                    .font(.caption)
                    .accessibilityIdentifier(model.credentialsAccessibilityIdentifier)
            }

            TextEditor(text: $model.offerURLText)
                .font(.caption.monospaced())
                .frame(minHeight: 72, maxHeight: 96)
                .overlay {
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(Color.secondary.opacity(0.35))
                }
                .accessibilityIdentifier("sdk-offer-input")

            Button {
                model.receiveCredential()
            } label: {
                Label("Receive", systemImage: "square.and.arrow.down")
            }
            .disabled(model.isRunning || model.offerURLText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .accessibilityIdentifier("sdk-receive-button")

            Text(model.receiveStatusText)
                .font(.caption)
                .accessibilityIdentifier(model.receiveStatusAccessibilityIdentifier)

            TextEditor(text: $model.presentationURLText)
                .font(.caption.monospaced())
                .frame(minHeight: 72, maxHeight: 96)
                .overlay {
                    RoundedRectangle(cornerRadius: 4)
                        .stroke(Color.secondary.opacity(0.35))
                }
                .accessibilityIdentifier("sdk-presentation-input")

            Button {
                model.presentCredential()
            } label: {
                Label("Present", systemImage: "person.crop.square")
            }
            .disabled(model.isRunning || model.presentationURLText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .accessibilityIdentifier("sdk-present-button")

            Text(model.presentStatusText)
                .font(.caption)
                .accessibilityIdentifier(model.presentStatusAccessibilityIdentifier)

            Text(model.lastEventText)
                .font(.caption2)
                .accessibilityIdentifier(model.lastEventAccessibilityIdentifier)
        }
        .padding()
    }

    public init() {}
}

@MainActor
private final class WalletSDKSampleModel: ObservableObject {
    @Published private(set) var statusText = "Ready"
    @Published private(set) var statusAccessibilityIdentifier = "sdk-bootstrap-idle"
    @Published private(set) var credentialText: String?
    @Published private(set) var credentialsAccessibilityIdentifier = "sdk-credentials-empty"
    @Published var offerURLText = ""
    @Published var presentationURLText = ""
    @Published private(set) var receiveStatusText = "Receive idle"
    @Published private(set) var receiveStatusAccessibilityIdentifier = "sdk-receive-idle"
    @Published private(set) var presentStatusText = "Present idle"
    @Published private(set) var presentStatusAccessibilityIdentifier = "sdk-present-idle"
    @Published private(set) var lastEventText = "No events"
    @Published private(set) var lastEventAccessibilityIdentifier = "sdk-last-event-idle"
    @Published private(set) var isRunning = false

    private var client: WalletClient?
    private var did: String?
    private var eventTask: Task<Void, Never>?

    deinit {
        eventTask?.cancel()
    }

    func bootstrapAndListCredentials() {
        guard !isRunning else { return }

        isRunning = true
        statusText = "Bootstrapping"
        statusAccessibilityIdentifier = "sdk-bootstrap-running"
        credentialText = nil
        credentialsAccessibilityIdentifier = "sdk-credentials-empty"

        Task {
            do {
                let client = try await WalletClient(
                    configuration: WalletConfiguration(
                        walletID: "ios-sdk-sample-\(UUID().uuidString)"
                    )
                )
                attachEvents(to: client)
                let bootstrap = try await client.bootstrap(didMethod: "key")
                let credentials = try await client.credentials()

                self.client = client
                did = bootstrap.did
                statusText = "Bootstrapped \(bootstrap.did)"
                statusAccessibilityIdentifier = "sdk-bootstrap-success"
                credentialText = credentials.isEmpty ? "No credentials" : "\(credentials.count) credentials"
                credentialsAccessibilityIdentifier = credentials.isEmpty ? "sdk-credentials-empty" : "sdk-credentials-count"
            } catch {
                statusText = "Failed: \(error)"
                statusAccessibilityIdentifier = "sdk-bootstrap-error"
                credentialText = nil
                credentialsAccessibilityIdentifier = "sdk-credentials-empty"
            }

            isRunning = false
        }
    }

    func receiveCredential() {
        guard !isRunning else { return }

        let trimmedOfferURL = offerURLText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let offerURL = URL(string: trimmedOfferURL) else {
            receiveStatusText = "Receive failed: invalid offer URL"
            receiveStatusAccessibilityIdentifier = "sdk-receive-error"
            return
        }

        isRunning = true
        receiveStatusText = "Receiving"
        receiveStatusAccessibilityIdentifier = "sdk-receive-running"

        Task {
            do {
                let client = try await walletClient()
                let credentialIDs = try await client.receive(offer: offerURL)
                let credentials = try await client.credentials()

                receiveStatusText = "Received \(credentialIDs.count) credential(s)"
                receiveStatusAccessibilityIdentifier = "sdk-receive-success"
                credentialText = credentials.isEmpty ? "No credentials" : "\(credentials.count) credentials"
                credentialsAccessibilityIdentifier = credentials.isEmpty ? "sdk-credentials-empty" : "sdk-credentials-count"
            } catch {
                receiveStatusText = "Receive failed: \(error)"
                receiveStatusAccessibilityIdentifier = "sdk-receive-error"
            }

            isRunning = false
        }
    }

    func presentCredential() {
        guard !isRunning else { return }

        let trimmedPresentationURL = presentationURLText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let presentationURL = URL(string: trimmedPresentationURL) else {
            presentStatusText = "Present failed: invalid request URL"
            presentStatusAccessibilityIdentifier = "sdk-present-error"
            return
        }

        isRunning = true
        presentStatusText = "Presenting"
        presentStatusAccessibilityIdentifier = "sdk-present-running"

        Task {
            do {
                let client = try await walletClient()
                let result = try await client.present(request: presentationURL, did: did)

                if result.success {
                    presentStatusText = "Presentation sent"
                    presentStatusAccessibilityIdentifier = "sdk-present-success"
                } else {
                    presentStatusText = "Presentation finished without verifier confirmation"
                    presentStatusAccessibilityIdentifier = "sdk-present-warning"
                }
            } catch {
                presentStatusText = "Present failed: \(error)"
                presentStatusAccessibilityIdentifier = "sdk-present-error"
            }

            isRunning = false
        }
    }

    private func walletClient() async throws -> WalletClient {
        if let client {
            return client
        }

        let client = try await WalletClient(
            configuration: WalletConfiguration(
                walletID: "ios-sdk-sample-\(UUID().uuidString)"
            )
        )
        attachEvents(to: client)
        let bootstrap = try await client.bootstrap(didMethod: "key")
        self.client = client
        did = bootstrap.did
        statusText = "Bootstrapped \(bootstrap.did)"
        statusAccessibilityIdentifier = "sdk-bootstrap-success"

        return client
    }

    private func attachEvents(to client: WalletClient) {
        eventTask?.cancel()
        lastEventText = "No events"
        lastEventAccessibilityIdentifier = "sdk-last-event-idle"

        eventTask = Task { [weak self] in
            let events = await client.events

            for await event in events {
                await MainActor.run {
                    self?.lastEventText = "\(event.phase) \(event.status): \(event.name)"
                    self?.lastEventAccessibilityIdentifier = "sdk-last-event"
                }
            }
        }
    }
}
