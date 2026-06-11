import SwiftUI

struct PresentView: View {
    @ObservedObject var viewModel: WalletViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Present")
                .font(.headline)

            TextField("OpenID4VP request URL", text: $viewModel.presentationRequestUrl, axis: .vertical)
                .textFieldStyle(.roundedBorder)
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
                .lineLimit(3)
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
