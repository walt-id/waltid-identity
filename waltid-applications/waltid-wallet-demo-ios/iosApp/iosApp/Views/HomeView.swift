import SwiftUI
import WalletDemoSharingUI

struct HomeView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var selectedCredentialDetailsID: String?
    @State private var selectedReceiveDetailsID: String?
    @State private var selectedPresentationDetailsID: String?
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
        .onChange(of: viewModel.presentationNavigationResetKey) { _ in
            selectedPresentationDetailsID = nil
        }
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
                selectedDetailsID: $selectedPresentationDetailsID,
                showsInput: viewModel.presentationUrlEntryEnabled,
                onClose: viewModel.dismissInteraction
            )
        }
    }
}
