import Foundation
import UIKit
import WalletSDK

protocol WalletClient {
    func bootstrap(keyType: WalletKeyType?, didMethod: String) async throws -> WalletBootstrapResult
    func receive(offer: URL, txCode: String?, clientID: String) async throws -> [String]
    func credentials() async throws -> [Credential]
    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult
}

extension Wallet: WalletClient {}

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

    private let walletFactory: () async throws -> any WalletClient
    private var cachedWallet: (any WalletClient)?

    init(
        walletID: String = "default",
        attestationBaseUrl: String? = nil,
        attestationAttesterPath: String? = nil,
        attestationBearerToken: String? = nil,
        attestationHostHeader: String? = nil,
        walletFactory: (() async throws -> any WalletClient)? = nil
    ) {
        let configuration = WalletConfiguration(
            walletID: walletID,
            attestation: Self.attestationConfiguration(
                baseUrl: attestationBaseUrl,
                attesterPath: attestationAttesterPath,
                bearerToken: attestationBearerToken,
                hostHeader: attestationHostHeader
            )
        )
        self.walletFactory = walletFactory ?? { try await Wallet(configuration: configuration) }
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
                let wallet = try await wallet()
                let credentialIDs = try await wallet.receive(offer: offer, txCode: nil, clientID: "wallet-client")
                credentials = try await wallet.credentials()
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
                let wallet = try await wallet()
                let result = try await wallet.present(
                    request: request,
                    did: did.isEmpty ? nil : did,
                    runPolicies: nil
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
                let wallet = try await wallet()
                logE2E("Bootstrap: calling wallet.bootstrap()")
                let result = try await wallet.bootstrap(keyType: nil, didMethod: "key")
                logE2E("Bootstrap: success, DID: \(result.did)")

                logE2E("Bootstrap: calling wallet.credentials()")
                let list = try await wallet.credentials()
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

    private func wallet() async throws -> any WalletClient {
        if let cachedWallet {
            return cachedWallet
        }

        let wallet = try await walletFactory()
        cachedWallet = wallet
        return wallet
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
            baseURL: baseUrl,
            attesterPath: attesterPath ?? "",
            bearerToken: bearerToken ?? "",
            hostHeader: hostHeader ?? ""
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

#if DEBUG
extension WalletViewModel {
    static func mockForUITests() -> WalletViewModel {
        WalletViewModel(walletID: "mock-wallet") {
            MockWalletClient()
        }
    }
}

private final class MockWalletClient: WalletClient {
    private var storedCredentials = [MockCredentialData.credential]

    func bootstrap(keyType: WalletKeyType?, didMethod: String) async throws -> WalletBootstrapResult {
        WalletBootstrapResult(keyID: "mock-key", did: "did:key:mock-wallet-demo")
    }

    func receive(offer: URL, txCode: String?, clientID: String) async throws -> [String] {
        storedCredentials = [MockCredentialData.credential]
        return storedCredentials.map(\.id)
    }

    func credentials() async throws -> [Credential] {
        storedCredentials
    }

    func present(request: URL, did: String?, runPolicies: Bool?) async throws -> PresentationResult {
        PresentationResult(success: true, redirectTo: nil, verifierResponseJSON: nil)
    }
}

private enum MockCredentialData {
    static let credential = Credential(
        id: "mock-credential",
        format: "jwt_vc_json",
        issuer: "walt.id demo issuer",
        subject: "did:key:mock-holder",
        label: "Mock credential",
        addedAt: Date(timeIntervalSince1970: 1_771_132_800),
        credentialDataJSON: """
        {
          "given_name": "Ada",
          "family_name": "Lovelace",
          "age_over_18": true,
          "portrait": "\(mockCredentialPortraitDataURI)"
        }
        """
    )

    private static var mockCredentialPortraitDataURI: String {
        let data = UIImage(named: "MockCredentialPortrait")?.pngData() ?? fallbackPortraitPNGData
        return "data:image/png;base64,\(data.base64EncodedString())"
    }

    private static var fallbackPortraitPNGData: Data {
        Data(base64Encoded: fallbackPortraitPNGBase64) ?? Data()
    }

    private static let fallbackPortraitPNGBase64 =
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
}
#endif
