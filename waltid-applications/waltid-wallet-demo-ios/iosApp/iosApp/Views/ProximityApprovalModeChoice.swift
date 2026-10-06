import SwiftUI
import WalletDemoIdentityDocumentSupport

struct ProximityApprovalModeChoice: View {
    @Binding var mode: WalletDemoProximityApprovalMode
    var compact = true

    var body: some View {
        Toggle(isOn: Binding(get: { mode == .prepareSharing }, set: { mode = $0 ? .prepareSharing : .askEachTime })) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Prepare sharing")
                Text(compact ? "Review once before connecting to the same reader." : WalletDemoProximityApprovalMode.prepareSharing.explanation)
                    .font(.footnote).foregroundStyle(.secondary)
            }
        }
        .frame(minHeight: 44).padding(.vertical, 8)
        .accessibilityIdentifier("proximity-approval-prepare")
    }
}
