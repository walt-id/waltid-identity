import SwiftUI
import WalletDemoSharingUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var selectedCredentialDetailsID: String?
    @State private var selectedReceiveDetailsID: String?
    @State private var scanVisible = false

    var body: some View {
        CredentialsTabView(
            viewModel: viewModel,
            selectedDetailsID: $selectedCredentialDetailsID,
            onScan: { scanVisible = true }
        )
        .accessibilityHidden(scanVisible || viewModel.selectedTab != .credentials)
        .sheet(isPresented: $scanVisible) {
            UnifiedScanView(
                classify: classifyWalletInteraction,
                onAccepted: { normalizedInput in
                    scanVisible = false
                    DispatchQueue.main.async {
                        _ = viewModel.submitInteractionInput(normalizedInput)
                        viewModel.resolveCurrentInteraction()
                    }
                },
                onDismiss: { scanVisible = false }
            )
        }
        .sheet(isPresented: interactionPresented) {
            interactionView
                .accessibilityIdentifier(WalletAccessibilityID.interactionSheet)
        }
        .onChange(of: viewModel.receiveNavigationResetKey) { _ in
            selectedReceiveDetailsID = nil
        }
        .overlay(alignment: .bottom) {
            if let message = viewModel.transientMessage {
                Text(message)
                    .font(.subheadline.weight(.semibold))
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.regularMaterial, in: Capsule())
                    .shadow(radius: 4, y: 2)
                    .padding(.bottom, 16)
                    .padding(.horizontal)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut, value: viewModel.transientMessage)
    }

    private var interactionPresented: Binding<Bool> {
        Binding(
            get: { viewModel.selectedTab != .credentials },
            set: { visible in
                if !visible {
                    viewModel.dismissInteraction()
                }
            }
        )
    }

    @ViewBuilder
    private var interactionView: some View {
        switch viewModel.selectedTab {
        case .credentials:
            EmptyView()
        case .receive:
            ReceiveView(
                viewModel: viewModel,
                selectedDetailsID: $selectedReceiveDetailsID,
                showsInput: viewModel.receiveUrlEntryEnabled,
                onClose: viewModel.dismissInteraction
            )
        case .present:
            PresentView(
                viewModel: viewModel,
                showsInput: viewModel.presentationUrlEntryEnabled,
                onClose: viewModel.dismissInteraction
            )
        }
    }
}
