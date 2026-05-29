import SwiftUI

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Receive")
                .font(.headline)

            TextField("Credential offer URL", text: $viewModel.offerUrl, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
                .lineLimit(3...6)
                .accessibilityIdentifier("wallet.offerInput")

            Button("Receive") {
                viewModel.receiveCredential()
            }
            .buttonStyle(.borderedProminent)
            .tint(.waltBlue)
            .disabled(!viewModel.isReady || viewModel.offerUrl.isEmpty || viewModel.isLoading)
            .accessibilityIdentifier("wallet.receiveButton")
        }
    }
}
