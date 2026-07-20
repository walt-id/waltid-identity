import SwiftUI

struct ClaimGroupView: View {
    let group: ClaimGroup

    var body: some View {
        if !group.items.isEmpty {
            ReviewMetadataSection(title: group.title) {
                ForEach(Array(group.items.enumerated()), id: \.element.id) { index, item in
                    if index > 0 {
                        Divider()
                    }
                    ClaimValueRow(item: item)
                }
            }
            .accessibilityIdentifier(WalletAccessibilityID.claimGroup(group.title))
        }
    }
}
