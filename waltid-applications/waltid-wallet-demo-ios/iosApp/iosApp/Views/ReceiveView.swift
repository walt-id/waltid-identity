import SwiftUI

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Receive")
                .font(.headline)

            TextEditor(text: $viewModel.offerUrl)
                .font(.footnote.monospaced())
                .frame(minHeight: 72, maxHeight: 96)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(.separator), lineWidth: 1)
                )
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
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
