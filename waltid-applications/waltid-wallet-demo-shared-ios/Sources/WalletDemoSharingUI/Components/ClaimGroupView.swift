import SwiftUI

public struct ClaimGroupView: View {
    public let group: ClaimGroup
    public let collapsible: Bool

    public init(group: ClaimGroup, collapsible: Bool = true) {
        self.group = group
        self.collapsible = collapsible
    }

    public var body: some View {
        if !group.items.isEmpty {
            ReviewMetadataSection(
                title: group.title,
                titleAccessibilityIdentifier: WalletAccessibilityID.claimGroup(group.title)
            ) {
                if collapsible {
                    MetadataDisclosure(
                        title: "\(group.items.count) \(group.items.count == 1 ? "entry" : "entries")",
                        initiallyExpanded: group.initiallyExpanded,
                        accessibilityIdentifier: WalletAccessibilityID.claimGroupDisclosure(group.title)
                    ) {
                        claimItems
                    }
                } else {
                    claimItems
                }
            }
        }
    }

    @ViewBuilder
    private var claimItems: some View {
        ForEach(Array(group.items.enumerated()), id: \.element.id) { index, item in
            if index > 0 {
                Divider()
            }
            ClaimValueRow(item: item)
        }
    }
}
