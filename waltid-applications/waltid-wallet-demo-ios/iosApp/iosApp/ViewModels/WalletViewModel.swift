import Foundation
import shared

@MainActor
class WalletViewModel: ObservableObject {
    @Published var isBootstrapped = false
    @Published var did = ""
    @Published var keyId = ""
    @Published var credentials: [BridgeCredential] = []
    @Published var statusMessage = "Ready"
    @Published var isLoading = false
    @Published var isError = false
    @Published var attestationStatus = "Not checked"

    @Published var baseUrl: String
    @Published var walletPath: String
    @Published var hostHeader: String
    @Published var bearerToken = ""
    @Published var attesterRef: String
    @Published var keyRef: String
    @Published var requireAttestation = false

    private let controller = WalletDemoBridgeController()

    init() {
        baseUrl = WalletDemoBridgeKt.quickstartEnterpriseBaseUrl()
        walletPath = WalletDemoBridgeKt.quickstartWalletPath()
        hostHeader = WalletDemoBridgeKt.quickstartHostHeader()
        attesterRef = WalletDemoBridgeKt.quickstartAttesterServiceRef()
        keyRef = WalletDemoBridgeKt.quickstartInstanceKeyReference()
    }

    private func syncEnvironment() {
        controller.updateEnvironment(
            baseUrl: baseUrl,
            walletPath: walletPath,
            hostHeader: hostHeader,
            bearerToken: bearerToken,
            attesterRef: attesterRef,
            keyRef: keyRef
        )
    }

    func applyProfile(_ name: String) {
        controller.applyProfile(profileName: name)
        baseUrl = WalletDemoBridgeKt.quickstartEnterpriseBaseUrl()
        walletPath = WalletDemoBridgeKt.quickstartWalletPath()
        hostHeader = WalletDemoBridgeKt.quickstartHostHeader()
        attesterRef = WalletDemoBridgeKt.quickstartAttesterServiceRef()
        keyRef = WalletDemoBridgeKt.quickstartInstanceKeyReference()
    }

    func bootstrap() {
        setLoading("Bootstrapping wallet...")
        syncEnvironment()
        Task {
            do {
                let result = try await controller.bootstrap()
                await MainActor.run {
                    self.isBootstrapped = true
                    self.did = result.did
                    self.keyId = result.keyId
                    self.setSuccess("Wallet ready")
                }
                await refreshCredentials()
            } catch {
                await MainActor.run {
                    self.setError("Bootstrap failed: \(error.localizedDescription)")
                }
            }
        }
    }

    func receiveCredential(offerUrl: String, txCode: String?) {
        setLoading("Receiving credential...")
        syncEnvironment()
        controller.setRequireAttestation(value: requireAttestation)
        Task {
            do {
                let result = try await controller.receiveCredential(offerUrl: offerUrl, txCode: txCode)
                await MainActor.run {
                    self.setSuccess(result.message)
                }
                await refreshCredentials()
            } catch {
                await MainActor.run {
                    self.setError("Receive failed: \(error.localizedDescription)")
                }
            }
        }
    }

    func enterpriseReceive(offerUrl: String) {
        setLoading("Receiving via enterprise...")
        syncEnvironment()
        Task {
            do {
                let result = try await controller.enterpriseReceive(offerUrl: offerUrl)
                await MainActor.run {
                    self.setSuccess(result.message)
                }
                await refreshCredentials()
            } catch {
                await MainActor.run {
                    self.setError("Enterprise receive failed: \(error.localizedDescription)")
                }
            }
        }
    }

    func presentCredential(requestUrl: String) {
        setLoading("Presenting credential...")
        syncEnvironment()
        Task {
            do {
                let result = try await controller.presentCredential(requestUrl: requestUrl)
                await MainActor.run {
                    self.setSuccess(result.success ? "Presented successfully" : "Presentation returned")
                }
            } catch {
                await MainActor.run {
                    self.setError("Present failed: \(error.localizedDescription)")
                }
            }
        }
    }

    func enterprisePresent(requestUrl: String) {
        setLoading("Presenting via enterprise...")
        syncEnvironment()
        Task {
            do {
                let result = try await controller.enterprisePresent(requestUrl: requestUrl)
                await MainActor.run {
                    self.setSuccess(result.message)
                }
            } catch {
                await MainActor.run {
                    self.setError("Enterprise present failed: \(error.localizedDescription)")
                }
            }
        }
    }

    func obtainAttestation() {
        setLoading("Obtaining attestation...")
        syncEnvironment()
        Task {
            do {
                let result = try await controller.obtainAttestation()
                await MainActor.run {
                    self.attestationStatus = result.message
                    self.setSuccess("Attestation obtained")
                }
            } catch {
                await MainActor.run {
                    self.setError("Attestation obtain failed: \(error.localizedDescription)")
                }
            }
        }
    }

    func checkAttestation() {
        setLoading("Checking attestation...")
        syncEnvironment()
        Task {
            do {
                let result = try await controller.checkAttestation()
                await MainActor.run {
                    self.attestationStatus = result.message
                    self.setSuccess("Attestation valid")
                }
            } catch {
                await MainActor.run {
                    self.attestationStatus = "Not available"
                    self.setError("Attestation check failed: \(error.localizedDescription)")
                }
            }
        }
    }

    func refreshCredentials() async {
        syncEnvironment()
        do {
            let list = try await controller.listCredentials()
            await MainActor.run {
                self.credentials = list
            }
        } catch {
            // silently ignore list errors
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
