import SwiftUI

public struct ClaimGroupView: View {
    public let group: ClaimGroup

    public init(group: ClaimGroup) {
        self.group = group
    }

    public var body: some View {
        if !group.items.isEmpty {
            ReviewMetadataSection(
                title: group.title,
                titleAccessibilityIdentifier: WalletAccessibilityID.claimGroup(group.title)
            ) {
                MetadataDisclosure(
                    title: "\(group.items.count) \(group.items.count == 1 ? "entry" : "entries")",
                    initiallyExpanded: group.initiallyExpanded,
                    accessibilityIdentifier: WalletAccessibilityID.claimGroupDisclosure(group.title)
                ) {
                    ForEach(Array(group.items.enumerated()), id: \.element.id) { index, item in
                        if index > 0 {
                            Divider()
                        }
                        ClaimValueRow(item: item)
                    }
                }
            }
        }
    }
}
