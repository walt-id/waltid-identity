import SwiftUI

struct SettingsView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        Form {
            Section("Environment") {
                HStack {
                    Button("Quickstart") { viewModel.applyProfile("QuickstartLocal") }
                        .buttonStyle(.bordered)
                        .tint(.waltBlue)
                    Button("Local") { viewModel.applyProfile("Local") }
                        .buttonStyle(.bordered)
                    Button("Sandbox") { viewModel.applyProfile("Sandbox") }
                        .buttonStyle(.bordered)
                }

                TextField("Enterprise Base URL", text: $viewModel.baseUrl)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                TextField("Wallet Path", text: $viewModel.walletPath)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                TextField("Host Header (local)", text: $viewModel.hostHeader)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                TextField("Bearer Token", text: $viewModel.bearerToken)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
            }

            Section("Service References") {
                TextField("Attester Service Ref", text: $viewModel.attesterRef)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                TextField("Instance Key Reference", text: $viewModel.keyRef)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
            }

            Section("Client Attestation") {
                Toggle("Require attestation before receive", isOn: $viewModel.requireAttestation)

                HStack {
                    Button("Obtain") { viewModel.obtainAttestation() }
                        .buttonStyle(.borderedProminent)
                        .tint(.waltBlue)
                        .disabled(viewModel.baseUrl.isEmpty || viewModel.attesterRef.isEmpty || viewModel.isLoading)
                    Button("Check") { viewModel.checkAttestation() }
                        .buttonStyle(.bordered)
                        .disabled(viewModel.baseUrl.isEmpty || viewModel.isLoading)
                }

                Text(viewModel.attestationStatus)
                    .font(.caption)
                    .foregroundColor(.gray)
            }
        }
        .navigationTitle("Settings")
    }
}
