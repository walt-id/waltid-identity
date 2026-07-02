import Foundation
import WaltidWalletSDK

@MainActor
class WalletViewModel: ObservableObject {
    @Published var isReady = false
    @Published var did = ""
    @Published var credentials: [Credential] = []
    @Published var statusMessage = "Starting wallet..."
    @Published var isLoading = false
    @Published var isError = false
    @Published var offerUrl = ""
    @Published var presentationRequestUrl = ""

    private let configuration: WalletConfiguration
    private var client: WalletClient?

    init(
        walletID: String = "default",
        attestationBaseUrl: String? = nil,
        attestationAttesterPath: String? = nil,
        attestationBearerToken: String? = nil,
        attestationHostHeader: String? = nil
    ) {
        configuration = WalletConfiguration(
            walletID: walletID,
            attestation: Self.attestationConfiguration(
                baseUrl: attestationBaseUrl,
                attesterPath: attestationAttesterPath,
                bearerToken: attestationBearerToken,
                hostHeader: attestationHostHeader
            )
        )
        bootstrap()
    }

    func handleDeepLink(_ url: URL) {
        logE2E("Deep link received: \(url.scheme ?? "unknown")")
        switch url.scheme {
        case "openid-credential-offer":
            offerUrl = url.absoluteString
        case "openid4vp":
            presentationRequestUrl = url.absoluteString
        default:
            break
        }
    }

    func receiveCredential() {
        let trimmedOfferUrl = offerUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let offer = URL(string: trimmedOfferUrl) else {
            setError("Receive failed: invalid offer URL")
            return
        }

        setLoading("Receiving credential...")
        Task {
            do {
                let client = try await walletClient()
                let credentialIDs = try await client.receive(offer: offer)
                credentials = try await client.credentials()
                setSuccess("Received \(credentialIDs.count) credential(s)")
            } catch {
                setError("Receive failed: \(error.localizedDescription)")
            }
        }
    }

    func presentCredential() {
        let trimmedRequestUrl = presentationRequestUrl.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let request = URL(string: trimmedRequestUrl) else {
            setError("Present failed: invalid request URL")
            return
        }

        setLoading("Presenting credential...")
        Task {
            do {
                let client = try await walletClient()
                let result = try await client.present(
                    request: request,
                    did: did.isEmpty ? nil : did
                )
                setSuccess(result.success ? "Presentation sent" : "Presentation finished without verifier confirmation")
            } catch {
                setError("Present failed: \(error.localizedDescription)")
            }
        }
    }

    private func bootstrap() {
        setLoading("Bootstrapping wallet...")
        logE2E("Bootstrap started")
        Task {
            do {
                let client = try await walletClient()
                logE2E("Bootstrap: calling client.bootstrap()")
                let result = try await client.bootstrap()
                logE2E("Bootstrap: success, DID: \(result.did)")

                logE2E("Bootstrap: calling client.credentials()")
                let list = try await client.credentials()
                logE2E("Bootstrap: listCredentials returned \(list.count) credentials")

                did = result.did
                credentials = list
                isReady = true
                setSuccess("Wallet ready")
                logE2E("Bootstrap: completed successfully, wallet is ready")
            } catch {
                logE2E("Bootstrap: FAILED with error: \(error.localizedDescription)")
                setError("Bootstrap failed: \(error.localizedDescription)")
            }
        }
    }

    private func walletClient() async throws -> WalletClient {
        if let client {
            return client
        }

        let client = try await WalletClient(configuration: configuration)
        self.client = client
        return client
    }

    private static func attestationConfiguration(
        baseUrl: String?,
        attesterPath: String?,
        bearerToken: String?,
        hostHeader: String?
    ) -> WalletAttestationConfiguration? {
        guard let baseUrl = baseUrl?.trimmingCharacters(in: .whitespacesAndNewlines),
              !baseUrl.isEmpty else {
            return nil
        }

        return WalletAttestationConfiguration(
            enterpriseBaseURL: baseUrl,
            attesterPath: attesterPath ?? "",
            bearerToken: bearerToken ?? "",
            enterpriseHostHeader: hostHeader ?? ""
        )
    }

    private func setLoading(_ message: String) {
        isLoading = true
        isError = false
        statusMessage = message
        logE2E("STATUS \(message)")
    }

    private func setSuccess(_ message: String) {
        isLoading = false
        isError = false
        statusMessage = message
        logE2E("STATUS \(message)")
    }

    private func setError(_ message: String) {
        isLoading = false
        isError = true
        statusMessage = message
        logE2E("STATUS \(message)")
    }

    private func logE2E(_ message: String) {
        NSLog("[WalletE2E] \(message)")
    }
}
