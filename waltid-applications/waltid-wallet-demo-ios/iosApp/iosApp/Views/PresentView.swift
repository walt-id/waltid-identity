import SwiftUI

struct PresentView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var requestUrl = ""

    var body: some View {
        VStack(spacing: 16) {
            StatusBannerView(
                message: viewModel.statusMessage,
                isLoading: viewModel.isLoading,
                isError: viewModel.isError
            )

            Text("Enter a presentation request URL to share a credential with a verifier.")
                .font(.subheadline)
                .foregroundColor(.secondary)

            TextField("Presentation Request URL", text: $requestUrl)
                .textFieldStyle(.roundedBorder)
                .autocapitalization(.none)
                .disableAutocorrection(true)

            HStack(spacing: 12) {
                Button("Present (Native)") {
                    viewModel.presentCredential(requestUrl: requestUrl)
                }
                .buttonStyle(.borderedProminent)
                .tint(.waltBlue)
                .disabled(requestUrl.isEmpty || viewModel.isLoading)

                Button("Enterprise") {
                    viewModel.enterprisePresent(requestUrl: requestUrl)
                }
                .buttonStyle(.bordered)
                .tint(.waltBlue)
                .disabled(requestUrl.isEmpty || viewModel.baseUrl.isEmpty || viewModel.isLoading)
            }

            Spacer()
        }
        .padding()
        .navigationTitle("Present Credential")
    }

    func setRequestUrl(_ url: String) {
        requestUrl = url
    }
}
