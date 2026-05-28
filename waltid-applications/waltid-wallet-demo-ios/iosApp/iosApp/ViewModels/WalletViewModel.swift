import Foundation
import shared

@MainActor
class WalletViewModel: ObservableObject {
    @Published var isReady = false
    @Published var did = ""
    @Published var credentials: [BridgeCredential] = []
    @Published var statusMessage = "Starting wallet..."
    @Published var isLoading = false
    @Published var isError = false
    @Published var offerUrl = ""
    @Published var presentationRequestUrl = ""

    private let controller: WalletDemoBridgeController

    init(
        attestationBaseUrl: String? = nil,
        attestationAttesterPath: String? = nil,
        attestationBearerToken: String? = nil,
        attestationHostHeader: String? = nil
    ) {
        controller = WalletDemoBridgeController(
            attestationBaseUrl: attestationBaseUrl,
            attestationAttesterPath: attestationAttesterPath,
            attestationBearerToken: attestationBearerToken,
            attestationHostHeader: attestationHostHeader
        )
        bootstrap()
    }

    // TODO: if a deep link arrives before bootstrap completes, the URL is stored but the action
    // is never triggered. Consider checking for pending URLs after bootstrap sets isReady = true.
    func handleDeepLink(_ url: URL) {
        switch url.scheme {
        case "openid-credential-offer":
            offerUrl = url.absoluteString
            if isReady && !isLoading {
                receiveCredential()
            }
        case "openid4vp":
            presentationRequestUrl = url.absoluteString
            if isReady && !isLoading && !credentials.isEmpty {
                presentCredential()
            }
        default:
            break
        }
    }

    func receiveCredential() {
        setLoading("Receiving credential...")
        Task {
            do {
                let result = try await controller.receiveCredential(offerUrl: offerUrl)
                let list = try await controller.listCredentials()
                await MainActor.run {
                    self.credentials = list
                    self.setSuccess(result.message)
                }
            } catch {
                await MainActor.run {
                    self.setError("Receive failed: \(error.localizedDescription)")
                }
            }
        }
    }

    func presentCredential() {
        setLoading("Presenting credential...")
        Task {
            do {
                let result = try await controller.presentCredential(requestUrl: presentationRequestUrl)
                await MainActor.run {
                    self.setSuccess(result.message)
                }
            } catch {
                await MainActor.run {
                    self.setError("Present failed: \(error.localizedDescription)")
                }
            }
        }
    }

    private func bootstrap() {
        setLoading("Bootstrapping wallet...")
        Task {
            do {
                let result = try await controller.bootstrap()
                await MainActor.run {
                    self.did = result.message
                    self.isReady = true
                    self.setSuccess("Wallet ready")
                }
            } catch {
                await MainActor.run {
                    self.setError("Bootstrap failed: \(error.localizedDescription)")
                }
            }
        }
    }

    private func setLoading(_ message: String) {
        isLoading = true
        isError = false
        statusMessage = message
    }

    private func setSuccess(_ message: String) {
        isLoading = false
        isError = false
        statusMessage = message
    }

    private func setError(_ message: String) {
        isLoading = false
        isError = true
        statusMessage = message
    }
}
