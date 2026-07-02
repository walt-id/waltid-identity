import SwiftUI

struct PresentView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Present")
                .font(.headline)

            TextEditor(text: $viewModel.presentationRequestUrl)
                .font(.footnote.monospaced())
                .frame(minHeight: 72, maxHeight: 96)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(.separator), lineWidth: 1)
                )
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
                .accessibilityIdentifier("wallet.presentationInput")

            Button("Present") {
                viewModel.presentCredential()
            }
            .buttonStyle(.borderedProminent)
            .tint(.waltBlue)
            .disabled(!viewModel.isReady || viewModel.presentationRequestUrl.isEmpty || viewModel.credentials.isEmpty || viewModel.isLoading)
            .accessibilityIdentifier("wallet.presentButton")
        }
    }
}
