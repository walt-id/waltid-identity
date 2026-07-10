import SwiftUI

struct ClaimGroupView: View {
    let group: ClaimGroup

    var body: some View {
        if !group.items.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text(group.title)
                    .font(.subheadline.weight(.semibold))
                    .accessibilityIdentifier(WalletAccessibilityID.claimGroup(group.title))
                ForEach(group.items) { item in
                    ClaimValueRow(item: item)
                }
            }
        }
    }
}
