import SwiftUI
import WalletSDK

struct CredentialsTabView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?

    private var details: [CredentialDetails] {
        viewModel.credentials.map(CredentialDisplayNormalizer.details(for:))
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    StatusBannerView(
                        message: viewModel.statusMessage(for: .credentials),
                        isLoading: viewModel.statusIsLoading(for: .credentials),
                        isError: viewModel.statusIsError(for: .credentials)
                    )

                    if let warning = viewModel.transactionDataProfilesWarning {
                        WarningBannerView(message: warning)
                    }

                    if details.isEmpty {
                        EmptyCredentialsView()
                    } else {
                        ForEach(details) { item in
                            CredentialCardButton(details: item) {
                                selectedDetailsID = item.id
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Credentials")
            .background(detailsNavigationLink)
            .accessibilityIdentifier(WalletAccessibilityID.credentialsTabContent)
        }
        .navigationViewStyle(.stack)
    }

    private var detailsNavigationLink: some View {
        NavigationLink(
            destination: detailsDestination,
            isActive: Binding(
                get: { selectedDetailsID != nil },
                set: { isActive in
                    if !isActive {
                        selectedDetailsID = nil
                    }
                }
            )
        ) {
            EmptyView()
        }
        .hidden()
    }

    private var detailsDestination: some View {
        Group {
            if let detailsID = selectedDetailsID {
                CredentialDetailsDestination(
                    detailsID: detailsID,
                    details: details
                )
            } else {
                EmptyView()
            }
        }
    }
}
