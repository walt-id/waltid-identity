import SwiftUI

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel
    @Binding var selectedDetailsID: String?

    private var receivedDetails: [CredentialDetails] {
        viewModel.receivedCredentials.map(CredentialDisplayNormalizer.details(for:))
    }

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    ScannableUrlEditor(
                        title: "Receive",
                        label: "Credential offer URL",
                        text: $viewModel.offerUrl,
                        inputIdentifier: WalletAccessibilityID.offerInput,
                        scanButtonIdentifier: WalletAccessibilityID.offerScanButton,
                        isEnabled: viewModel.receiveUrlEntryEnabled,
                        focusResetKey: viewModel.inputFocusResetKey
                    )

                    if let requirement = viewModel.transactionCode {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(requirement.description.flatMap { $0.isEmpty ? nil : $0 }
                                ?? "This offer requires a transaction code.")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            SecureField(
                                "Transaction code",
                                text: Binding(
                                    get: { viewModel.txCode },
                                    set: viewModel.updateTxCode
                                )
                            )
                                .textContentType(.oneTimeCode)
                                .keyboardType(requirement.inputMode == .numeric ? .numberPad : .asciiCapable)
                                .textInputAutocapitalization(.never)
                                .disableAutocorrection(true)
                                .padding(8)
                                .frame(minHeight: 52)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(Color(.separator), lineWidth: 1)
                                )
                                .disabled(!viewModel.receiveUrlEntryEnabled)
                                .accessibilityIdentifier(WalletAccessibilityID.txCodeInput)
                        }
                    }

                    Button("Receive") {
                        viewModel.receiveCredential()
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.waltBlue)
                    .disabled(!viewModel.receiveActionEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.receiveButton)

                    StatusBannerView(
                        message: viewModel.statusMessage(for: .receive),
                        isLoading: viewModel.statusIsLoading(for: .receive),
                        isError: viewModel.statusIsError(for: .receive)
                    )

                    if let warning = viewModel.transactionDataProfilesWarning {
                        WarningBannerView(message: warning)
                    }

                    if viewModel.receiveCompleted {
                        Button("New receive", action: viewModel.startNewReceiveFlow)
                            .buttonStyle(.bordered)
                            .accessibilityIdentifier(WalletAccessibilityID.receiveNewButton)

                        Text("Received credentials")
                            .font(.subheadline.weight(.semibold))

                        ForEach(receivedDetails) { item in
                            CredentialCardButton(details: item) {
                                selectedDetailsID = item.id
                            }
                        }
                    }
                }
                .padding()
            }
            .navigationTitle("Receive")
            .background(detailsNavigationLink)
            .accessibilityIdentifier(WalletAccessibilityID.receiveTabContent)
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
                    details: receivedDetails
                )
            } else {
                EmptyView()
            }
        }
    }
}
