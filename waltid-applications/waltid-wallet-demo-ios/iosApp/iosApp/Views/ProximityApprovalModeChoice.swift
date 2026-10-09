import SwiftUI
import WalletDemoIdentityDocumentSupport

struct ProximityApprovalModeChoice: View {
    @Binding var mode: WalletDemoProximityApprovalMode
    var compact = true

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Prepare sharing")
                Text(compact ? "Review once before connecting to the same reader." : WalletDemoProximityApprovalMode.prepareSharing.explanation)
                    .font(.footnote).foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .fixedSize(horizontal: false, vertical: true)
            Toggle("Prepare sharing", isOn: Binding(get: { mode == .prepareSharing },
                set: { mode = $0 ? .prepareSharing : .askEachTime }))
                .labelsHidden()
                .fixedSize()
                .accessibilityIdentifier("proximity-approval-prepare")
        }
        .frame(minHeight: 44).padding(.vertical, 8)
    }
}
