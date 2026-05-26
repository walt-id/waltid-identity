import SwiftUI

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var offerUrl = ""
    @State private var txCode = ""

    var body: some View {
        VStack(spacing: 16) {
            StatusBannerView(
                message: viewModel.statusMessage,
                isLoading: viewModel.isLoading,
                isError: viewModel.isError
            )

            Text("Enter a credential offer URL or scan a QR code to receive a credential.")
                .font(.subheadline)
                .foregroundColor(.secondary)

            TextField("Credential Offer URL", text: $offerUrl)
                .textFieldStyle(.roundedBorder)
                .autocapitalization(.none)
                .disableAutocorrection(true)

            TextField("Transaction Code (optional)", text: $txCode)
                .textFieldStyle(.roundedBorder)

            HStack(spacing: 12) {
                Button("Receive (Native)") {
                    viewModel.receiveCredential(
                        offerUrl: offerUrl,
                        txCode: txCode.isEmpty ? nil : txCode
                    )
                }
                .buttonStyle(.borderedProminent)
                .tint(.waltBlue)
                .disabled(offerUrl.isEmpty || viewModel.isLoading)

                Button("Enterprise") {
                    viewModel.enterpriseReceive(offerUrl: offerUrl)
                }
                .buttonStyle(.bordered)
                .tint(.waltBlue)
                .disabled(offerUrl.isEmpty || viewModel.baseUrl.isEmpty || viewModel.isLoading)
            }

            if viewModel.requireAttestation {
                Text("Attestation: \(viewModel.attestationStatus)")
                    .font(.caption)
                    .foregroundColor(.gray)
            }

            Spacer()
        }
        .padding()
        .navigationTitle("Receive Credential")
    }

    func setOfferUrl(_ url: String) {
        offerUrl = url
    }
}
