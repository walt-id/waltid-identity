import SwiftUI

struct ClaimGroupView: View {
    let group: ClaimGroup

    var body: some View {
        if !group.items.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                Text(group.title.uppercased())
                    .font(.caption.weight(.bold))
                    .foregroundStyle(.tint)
                    .accessibilityLabel(group.title)
                    .accessibilityIdentifier(WalletAccessibilityID.claimGroup(group.title))

                VStack(alignment: .leading, spacing: 12) {
                    ForEach(Array(group.items.enumerated()), id: \.element.id) { index, item in
                        if index > 0 {
                            Divider()
                        }
                        ClaimValueRow(item: item)
                    }
                }
                .padding()
                .background(Color(.systemGray6))
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}
