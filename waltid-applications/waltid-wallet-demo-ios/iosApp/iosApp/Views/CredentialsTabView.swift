import SwiftUI
import WalletDemoSharingUI
import WalletSDK

struct CredentialsTabView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?
    let onScan: () -> Void

    private var details: [CredentialDetails] {
        viewModel.credentials.map(CredentialDisplayNormalizer.details(for:))
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    walletHeader

                    if viewModel.statusShouldPersist(for: .credentials) {
                        StatusBannerView(
                            message: viewModel.statusMessage(for: .credentials),
                            isLoading: viewModel.statusIsLoading(for: .credentials),
                            isError: viewModel.statusIsError(for: .credentials),
                            onDismiss: viewModel.dismissStatus
                        )
                    }

                    if let warning = viewModel.transactionDataProfilesWarning {
                        WarningBannerView(message: warning)
                    }

                    if details.isEmpty {
                        EmptyCredentialsView(onScan: onScan, scanEnabled: viewModel.isReady)
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
            .navigationBarHidden(true)
            .background(detailsNavigationLink)
            .accessibilityIdentifier(WalletAccessibilityID.credentialsTabContent)
        }
        .navigationViewStyle(.stack)
    }

    private var walletHeader: some View {
        HStack(spacing: 10) {
            Image("WaltIdLogo")
                .resizable()
                .renderingMode(.template)
                .scaledToFit()
                .frame(width: 34, height: 34)
                .foregroundStyle(Color.waltBlue)

            Text("Demo Wallet")
                .font(.title3.weight(.bold))
                .lineLimit(1)

            Spacer(minLength: 6)

            HStack(spacing: 2) {
                Button(action: onScan) {
                    Image(systemName: "qrcode.viewfinder")
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .disabled(!viewModel.isReady)
                .accessibilityLabel("Scan credential offer or presentation request")
                .accessibilityIdentifier(WalletAccessibilityID.scanAction)

                NavigationLink(
                    destination: WalletSettingsView(viewModel: viewModel)
                        .navigationBarHidden(false)
                ) {
                    Image(systemName: "gearshape")
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Settings")
                .accessibilityIdentifier(WalletAccessibilityID.settingsAction)
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 4)
            .background(.regularMaterial, in: Capsule())
            .shadow(color: .black.opacity(0.08), radius: 8, y: 3)
        }
        .accessibilityElement(children: .contain)
        .padding(.bottom, 8)
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
                    details: details,
                    onDelete: { credentialID in
                        Task {
                            if await viewModel.deleteCredential(id: credentialID) {
                                selectedDetailsID = nil
                            }
                        }
                    }
                )
                .navigationBarHidden(false)
            } else {
                EmptyView()
            }
        }
    }
}
