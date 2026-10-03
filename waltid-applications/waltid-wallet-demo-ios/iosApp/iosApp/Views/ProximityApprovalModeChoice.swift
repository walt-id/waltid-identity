import SwiftUI
import WalletDemoSharingUI
import WalletSDK
import WalletDemoIdentityDocumentSupport

struct ProximityApprovalModeChoice: View {
    @Binding var mode: WalletDemoProximityApprovalMode
    var compact = true

    var body: some View {
        Group {
            if compact {
                HStack(spacing: 12) {
                    choice(.askEachTime, title: "Ask each time")
                    choice(.prepareSharing, title: "Prepare sharing")
                }
            } else {
                VStack(spacing: 0) {
                    choice(.askEachTime, title: "Ask each time")
                    Divider()
                    choice(.prepareSharing, title: "Prepare sharing")
                }
            }
        }
    }

    private func choice(_ choice: WalletDemoProximityApprovalMode, title: LocalizedStringKey) -> some View {
        Button { mode = choice } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).foregroundStyle(.primary)
                    if !compact { Text(choice.explanation).font(.footnote).foregroundStyle(.secondary) }
                }
                Spacer()
                Image(systemName: mode == choice ? "largecircle.fill.circle" : "circle")
                    .foregroundStyle(.tint).accessibilityHidden(true)
            }
            .frame(minHeight: 44).padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier(choice == .prepareSharing ? "proximity-approval-prepare" : "proximity-approval-ask")
        .accessibilityValue(mode == choice ? "Selected" : "Not selected")
        .accessibilityAddTraits(mode == choice ? .isSelected : [])
    }
}
