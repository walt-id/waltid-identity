import SwiftUI

/// The same synthetic, side-effect-free content is rendered by previews and snapshot tests.
struct WalletControlsPreview: View {
    @State private var copies = 1

    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Text("Actions and counts").font(.title2)
            WalletActions(primary: WalletAction("Continue", perform: {}),
                          secondary: WalletAction("Cancel", perform: {}))
            WalletActions(primary: WalletAction("Working…", enabled: false, perform: {}),
                          secondary: WalletAction("Cancel", enabled: false, perform: {}))
            HStack {
                Text("Copies: \(copies)")
                Spacer()
                WalletCountControl(value: $copies, range: 1...3, enabled: true,
                    decreaseLabel: "Fewer copies", increaseLabel: "More copies", identifier: "preview.copies")
            }
            Spacer()
        }.padding(20).background(Color(.systemGroupedBackground))
    }
}

#if DEBUG
#Preview("Wallet controls") { WalletControlsPreview() }
#Preview("Wallet controls · RTL") {
    WalletControlsPreview().environment(\.layoutDirection, .rightToLeft).environment(\.dynamicTypeSize, .accessibility1)
}
#endif
