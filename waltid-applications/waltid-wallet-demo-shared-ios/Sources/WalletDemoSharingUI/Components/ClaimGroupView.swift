import SwiftUI

public struct ClaimGroupView: View {
    public let group: ClaimGroup
    public let collapsible: Bool
    private let claimStatus: (ClaimItem) -> String?

    public init(group: ClaimGroup, collapsible: Bool = true, claimStatus: @escaping (ClaimItem) -> String? = { _ in nil }) {
        self.group = group
        self.collapsible = collapsible
        self.claimStatus = claimStatus
    }

    public var body: some View {
        if !group.items.isEmpty {
            WalletSection(collapsible ? nil : group.title, titleIdentifier: WalletAccessibilityID.claimGroup(group.title)) {
                VStack(alignment: .leading, spacing: 12) {
                    if collapsible {
                        MetadataDisclosure(
                            title: group.title,
                            initiallyExpanded: group.initiallyExpanded,
                            accessibilityIdentifier: WalletAccessibilityID.claimGroupDisclosure(group.title)
                        ) {
                            claimItems.padding(.bottom, 12)
                        }
                    } else {
                        claimItems
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, collapsible ? 4 : 16)
            }
        }
    }

    @ViewBuilder
    private var claimItems: some View {
        LazyVStack(alignment: .leading, spacing: 8) {
            ForEach(Array(group.items.enumerated()), id: \.element.id) { index, item in
                if index > 0 {
                    Divider()
                }
                ClaimValueRow(item: item)
                if let status = claimStatus(item) {
                    Text(status).font(.footnote).foregroundStyle(.secondary)
                }
            }
        }
    }
}
