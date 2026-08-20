import SwiftUI
import UIKit

/// Container-independent expandable islands with horizontal, in-surface technical navigation.
public struct ReviewIslandNavigationView<ExpandedContent: View>: View {
    private let islands: [ReviewIsland]
    private let expandedContent: (ReviewIsland) -> ExpandedContent
    private let showsModelExpandedValues: (ReviewIsland) -> Bool

    @State private var route: ReviewRoute = .summary
    @State private var expandedIslandIDs: Set<String>
    @AccessibilityFocusState private var accessibilityFocus: String?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(
        islands: [ReviewIsland],
        showsModelExpandedValues: @escaping (ReviewIsland) -> Bool = { _ in true },
        @ViewBuilder expandedContent: @escaping (ReviewIsland) -> ExpandedContent
    ) {
        self.islands = islands
        self.showsModelExpandedValues = showsModelExpandedValues
        self.expandedContent = expandedContent
        _expandedIslandIDs = State(initialValue: Set(islands.filter(\.initiallyExpanded).map(\.id)))
    }

    public var body: some View {
        ZStack(alignment: .topLeading) {
            switch route {
            case .summary:
                summary
                    .transition(routeTransition(forward: false))
            case .technicalDetails(let islandID):
                if let island = islands.first(where: { $0.id == islandID }) {
                    ReviewTechnicalPage(
                        island: island,
                        accessibilityFocus: $accessibilityFocus,
                        onBack: { showSummary(originatingIslandID: island.id) }
                    )
                    .transition(routeTransition(forward: true))
                }
            }
        }
        .clipped()
        .onChange(of: islands.map(\.id)) { _ in
            route = .summary
            expandedIslandIDs = Set(islands.filter(\.initiallyExpanded).map(\.id))
        }
    }

    private var summary: some View {
        VStack(alignment: .leading, spacing: 12) {
            ForEach(islands) { island in
                ReviewIslandCard(
                    island: island,
                    isExpanded: expandedIslandIDs.contains(island.id),
                    showsModelExpandedValues: showsModelExpandedValues(island),
                    accessibilityFocus: $accessibilityFocus,
                    onToggle: { toggle(island.id) },
                    onTechnicalDetails: island.hasTechnicalDetails ? { showTechnicalDetails(island.id) } : nil,
                    expandedContent: expandedContent(island)
                )
            }
        }
    }

    private func toggle(_ islandID: String) {
        if expandedIslandIDs.contains(islandID) {
            expandedIslandIDs.remove(islandID)
        } else {
            expandedIslandIDs.insert(islandID)
        }
    }

    private func showTechnicalDetails(_ islandID: String) {
        performRouteAnimation { route = .technicalDetails(islandID: islandID) }
        DispatchQueue.main.async {
            accessibilityFocus = "technical-title-\(islandID)"
            UIAccessibility.post(notification: .screenChanged, argument: nil)
        }
    }

    private func showSummary(originatingIslandID: String) {
        performRouteAnimation { route = .summary }
        DispatchQueue.main.async {
            accessibilityFocus = "technical-link-\(originatingIslandID)"
            UIAccessibility.post(notification: .screenChanged, argument: nil)
        }
    }

    private func performRouteAnimation(_ change: () -> Void) {
        if reduceMotion {
            change()
        } else {
            withAnimation(.easeInOut(duration: 0.22), change)
        }
    }

    private func routeTransition(forward: Bool) -> AnyTransition {
        guard !reduceMotion else { return .opacity }
        return .asymmetric(
            insertion: .move(edge: forward ? .trailing : .leading),
            removal: .move(edge: forward ? .leading : .trailing)
        )
    }
}

/// Convenience initializer for islands that need no custom expanded content.
public extension ReviewIslandNavigationView where ExpandedContent == EmptyView {
    init(
        islands: [ReviewIsland],
        showsModelExpandedValues: @escaping (ReviewIsland) -> Bool = { _ in true }
    ) {
        self.init(islands: islands, showsModelExpandedValues: showsModelExpandedValues) { _ in EmptyView() }
    }
}

private struct ReviewIslandCard<ExpandedContent: View>: View {
    let island: ReviewIsland
    let isExpanded: Bool
    let showsModelExpandedValues: Bool
    @AccessibilityFocusState.Binding var accessibilityFocus: String?
    let onToggle: () -> Void
    let onTechnicalDetails: (() -> Void)?
    let expandedContent: ExpandedContent

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Color.clear
                .frame(width: 1, height: 1)
                .accessibilityElement()
                .accessibilityIdentifier(WalletAccessibilityID.reviewIsland(island.id))

            Button(action: onToggle) {
                HStack(spacing: 12) {
                    ReviewIslandVisualView(island: island)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(island.title)
                            .font(.body.weight(.semibold))
                            .foregroundStyle(.primary)
                            .multilineTextAlignment(.leading)
                        if let subtitle = island.subtitle?.presentableValue {
                            Text(subtitle)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                                .multilineTextAlignment(.leading)
                        }
                    }
                    Spacer(minLength: 8)
                    Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                        .foregroundStyle(.secondary)
                }
                .contentShape(Rectangle())
                .padding(16)
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier(WalletAccessibilityID.reviewIslandToggle(island.id))
            .accessibilityValue(isExpanded ? "Expanded" : "Collapsed")

            if !island.visibleSummaryValues.isEmpty {
                Divider()
                ReviewValueList(values: island.visibleSummaryValues)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
            }

            if isExpanded {
                Divider()
                VStack(alignment: .leading, spacing: 12) {
                    if let warning = island.warning?.presentableValue {
                        Text(warning)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                    if let status = island.status, status.isVisible {
                        ReviewValueList(values: [status])
                    }
                    if showsModelExpandedValues, !island.visibleExpandedValues.isEmpty {
                        ReviewValueList(values: island.visibleExpandedValues)
                    }
                    expandedContent
                    if let onTechnicalDetails {
                        Divider()
                        Button(action: onTechnicalDetails) {
                            HStack {
                                Text("Technical details")
                                    .font(.subheadline.weight(.medium))
                                Spacer()
                                Image(systemName: "chevron.right")
                            }
                            .contentShape(Rectangle())
                            .frame(minHeight: 44)
                        }
                        .buttonStyle(.plain)
                        .accessibilityIdentifier(WalletAccessibilityID.reviewIslandTechnicalDetails(island.id))
                        .accessibilityFocused($accessibilityFocus, equals: "technical-link-\(island.id)")
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color(.separator), lineWidth: 0.5))
    }
}

private struct ReviewIslandVisualView: View {
    let island: ReviewIsland

    private var imageURL: URL? {
        guard let value = island.visual?.imageURI,
              let url = URL(string: value),
              url.scheme?.lowercased() == "https" else { return nil }
        return url
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12).fill(Color(.tertiarySystemFill))
            if let imageData = island.visual?.imageData, let image = UIImage(data: imageData) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .clipped()
                    .accessibilityLabel(island.visual?.contentDescription ?? "Credential image")
            } else if let imageURL {
                AsyncImage(url: imageURL) { phase in
                    switch phase {
                    case .success(let image): image.resizable().scaledToFit()
                    case .empty: ProgressView()
                    case .failure: fallback
                    @unknown default: fallback
                    }
                }
                .accessibilityLabel(island.visual?.contentDescription ?? "\(island.title) image")
            } else {
                fallback
            }
        }
        .frame(width: 52, height: 52)
    }

    private var fallback: some View {
        Text(String((island.visual?.fallbackText ?? island.title).prefix(2)))
            .font(.headline.weight(.bold))
            .foregroundStyle(.primary)
    }
}

private struct ReviewValueList: View {
    let values: [ReviewValue]

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(values.filter(\.isVisible).enumerated()), id: \.offset) { index, value in
                if index > 0 { Divider() }
                VStack(alignment: .leading, spacing: 1) {
                    Text(value.label).font(.caption2).foregroundStyle(.secondary)
                    if let rendered = value.value?.presentableValue {
                        if let link = safeHTTPSURL(value.linkURI) {
                            Link(rendered, destination: link).font(.caption)
                        } else {
                            Text(rendered).font(.caption)
                        }
                    }
                    if let supportingText = value.supportingText?.presentableValue {
                        Text(supportingText).font(.caption2).foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    private func safeHTTPSURL(_ value: String?) -> URL? {
        guard let value, let url = URL(string: value), url.scheme?.lowercased() == "https" else { return nil }
        return url
    }
}

private struct ReviewTechnicalPage: View {
    let island: ReviewIsland
    @AccessibilityFocusState.Binding var accessibilityFocus: String?
    let onBack: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            Color.clear
                .frame(width: 1, height: 1)
                .accessibilityElement()
                .accessibilityIdentifier(WalletAccessibilityID.reviewTechnicalDetailsPage)

            HStack(spacing: 8) {
                Button(action: onBack) {
                    Label("Back", systemImage: "chevron.left")
                        .labelStyle(.iconOnly)
                        .frame(width: 44, height: 44)
                }
                .accessibilityLabel("Back to review")
                .accessibilityIdentifier(WalletAccessibilityID.reviewTechnicalDetailsBack)

                VStack(alignment: .leading, spacing: 2) {
                    Text(island.title)
                        .font(.title3.weight(.semibold))
                        .accessibilityAddTraits(.isHeader)
                        .accessibilityFocused($accessibilityFocus, equals: "technical-title-\(island.id)")
                    Text("Technical details").font(.caption).foregroundStyle(.secondary)
                }
            }

            ForEach(island.visibleTechnicalSections) { section in
                VStack(alignment: .leading, spacing: 8) {
                    Text(section.title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.tint)
                    ReviewValueList(values: section.visibleValues)
                        .padding(16)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
