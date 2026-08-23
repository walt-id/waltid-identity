import SwiftUI
import UIKit

/// Container-independent expandable islands with horizontal, in-surface technical navigation.
public struct ReviewIslandNavigationView<HeaderContent: View, ExpandedContent: View>: View {
    private let islands: [ReviewIsland]
    private let headerContent: (ReviewIsland) -> HeaderContent
    private let expandedContent: (ReviewIsland) -> ExpandedContent
    private let showsModelExpandedValues: (ReviewIsland) -> Bool
    private let hasCustomExpandedContent: (ReviewIsland) -> Bool
    private let routeBinding: Binding<ReviewRoute>?
    private let showsTechnicalHeader: Bool
    private let onRouteChanged: (ReviewRoute, ReviewIsland?) -> Void

    @State private var localRoute: ReviewRoute = .summary
    @State private var expandedIslandIDs: Set<String>
    @Namespace private var islandTransitionNamespace
    @AccessibilityFocusState private var accessibilityFocus: String?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(
        islands: [ReviewIsland],
        showsModelExpandedValues: @escaping (ReviewIsland) -> Bool = { _ in true },
        hasCustomExpandedContent: @escaping (ReviewIsland) -> Bool = { _ in false },
        route: Binding<ReviewRoute>? = nil,
        showsTechnicalHeader: Bool = true,
        onRouteChanged: @escaping (ReviewRoute, ReviewIsland?) -> Void = { _, _ in },
        @ViewBuilder headerContent: @escaping (ReviewIsland) -> HeaderContent,
        @ViewBuilder expandedContent: @escaping (ReviewIsland) -> ExpandedContent
    ) {
        self.islands = islands
        self.showsModelExpandedValues = showsModelExpandedValues
        self.hasCustomExpandedContent = hasCustomExpandedContent
        self.routeBinding = route
        self.showsTechnicalHeader = showsTechnicalHeader
        self.onRouteChanged = onRouteChanged
        self.headerContent = headerContent
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
                        transitionNamespace: islandTransitionNamespace,
                        accessibilityFocus: $accessibilityFocus,
                        showsHeader: showsTechnicalHeader,
                        onBack: { showSummary(originatingIslandID: island.id) }
                    )
                    .transition(routeTransition(forward: true))
                }
            }
        }
        .clipped()
        .onAppear { notifyRouteChanged(route) }
        .onChange(of: route) { notifyRouteChanged($0) }
        .onChange(of: islands.map(\.id)) { _ in
            setRoute(.summary)
            expandedIslandIDs = Set(islands.filter(\.initiallyExpanded).map(\.id))
        }
    }

    private var route: ReviewRoute { routeBinding?.wrappedValue ?? localRoute }

    private var summary: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(islands) { island in
                ReviewIslandCard(
                    island: island,
                    isExpanded: expandedIslandIDs.contains(island.id),
                    showsModelExpandedValues: showsModelExpandedValues(island),
                    hasCustomExpandedContent: hasCustomExpandedContent(island),
                    accessibilityFocus: $accessibilityFocus,
                    transitionNamespace: islandTransitionNamespace,
                    onToggle: { toggle(island.id) },
                    onTechnicalDetails: island.hasTechnicalDetails ? { showTechnicalDetails(island.id) } : nil,
                    headerContent: headerContent(island),
                    expandedContent: expandedContent(island)
                )
            }
        }
    }

    private func toggle(_ islandID: String) {
        let change = {
            if expandedIslandIDs.contains(islandID) {
                expandedIslandIDs.remove(islandID)
            } else {
                expandedIslandIDs.insert(islandID)
            }
        }
        if reduceMotion {
            change()
        } else {
            withAnimation(.easeInOut(duration: 0.22), change)
        }
    }

    private func showTechnicalDetails(_ islandID: String) {
        performRouteAnimation { setRoute(.technicalDetails(islandID: islandID)) }
        DispatchQueue.main.async {
            accessibilityFocus = "technical-title-\(islandID)"
            UIAccessibility.post(notification: .screenChanged, argument: nil)
        }
    }

    private func showSummary(originatingIslandID: String) {
        performRouteAnimation { setRoute(.summary) }
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

    private func setRoute(_ route: ReviewRoute) {
        if let routeBinding {
            routeBinding.wrappedValue = route
        } else {
            localRoute = route
        }
    }

    private func notifyRouteChanged(_ route: ReviewRoute) {
        let island: ReviewIsland?
        switch route {
        case .summary:
            island = nil
        case .technicalDetails(let islandID):
            island = islands.first { $0.id == islandID }
        }
        onRouteChanged(route, island)
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
public extension ReviewIslandNavigationView where HeaderContent == EmptyView {
    init(
        islands: [ReviewIsland],
        showsModelExpandedValues: @escaping (ReviewIsland) -> Bool = { _ in true },
        hasCustomExpandedContent: @escaping (ReviewIsland) -> Bool = { _ in false },
        route: Binding<ReviewRoute>? = nil,
        showsTechnicalHeader: Bool = true,
        onRouteChanged: @escaping (ReviewRoute, ReviewIsland?) -> Void = { _, _ in },
        @ViewBuilder expandedContent: @escaping (ReviewIsland) -> ExpandedContent
    ) {
        self.init(
            islands: islands,
            showsModelExpandedValues: showsModelExpandedValues,
            hasCustomExpandedContent: hasCustomExpandedContent,
            route: route,
            showsTechnicalHeader: showsTechnicalHeader,
            onRouteChanged: onRouteChanged,
            headerContent: { _ in EmptyView() },
            expandedContent: expandedContent
        )
    }
}

public extension ReviewIslandNavigationView where HeaderContent == EmptyView, ExpandedContent == EmptyView {
    init(
        islands: [ReviewIsland],
        showsModelExpandedValues: @escaping (ReviewIsland) -> Bool = { _ in true },
        hasCustomExpandedContent: @escaping (ReviewIsland) -> Bool = { _ in false },
        route: Binding<ReviewRoute>? = nil,
        showsTechnicalHeader: Bool = true,
        onRouteChanged: @escaping (ReviewRoute, ReviewIsland?) -> Void = { _, _ in }
    ) {
        self.init(
            islands: islands,
            showsModelExpandedValues: showsModelExpandedValues,
            hasCustomExpandedContent: hasCustomExpandedContent,
            route: route,
            showsTechnicalHeader: showsTechnicalHeader,
            onRouteChanged: onRouteChanged
        ) { _ in EmptyView() }
    }
}

private struct ReviewIslandCard<HeaderContent: View, ExpandedContent: View>: View {
    let island: ReviewIsland
    let isExpanded: Bool
    let showsModelExpandedValues: Bool
    let hasCustomExpandedContent: Bool
    @AccessibilityFocusState.Binding var accessibilityFocus: String?
    let transitionNamespace: Namespace.ID
    let onToggle: () -> Void
    let onTechnicalDetails: (() -> Void)?
    let headerContent: HeaderContent
    let expandedContent: ExpandedContent

    var body: some View {
        let accentColor = island.kind.accentColor
        let hasModelExpandedContent = island.warning?.presentableValue != nil ||
            island.status?.isVisible == true ||
            (showsModelExpandedValues && !island.visibleExpandedValues.isEmpty)
        let hasContentBeforeTechnicalDetails = hasModelExpandedContent || hasCustomExpandedContent
        let hasExpandedContent = hasContentBeforeTechnicalDetails || onTechnicalDetails != nil
        let effectiveExpanded = isExpanded && hasExpandedContent
        VStack(alignment: .leading, spacing: 0) {
            Color.clear
                .frame(width: 1, height: 1)
                .accessibilityElement()
                .accessibilityIdentifier(WalletAccessibilityID.reviewIsland(island.id))

            HStack(spacing: 10) {
                headerContent
                Button(action: { if hasExpandedContent { onToggle() } }) {
                    HStack(spacing: 10) {
                        if island.visual != nil {
                            ReviewIslandVisualView(island: island, accentColor: accentColor)
                        }
                        VStack(alignment: .leading, spacing: 2) {
                            Text(island.title)
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.primary)
                                .multilineTextAlignment(.leading)
                                .fixedSize(horizontal: false, vertical: true)
                            if let subtitle = island.subtitle?.presentableValue {
                                Text(subtitle)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .multilineTextAlignment(.leading)
                                    .fixedSize(horizontal: false, vertical: true)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .layoutPriority(1)
                        if hasExpandedContent {
                            Spacer(minLength: 8)
                            Image(systemName: "chevron.down")
                                .foregroundStyle(accentColor)
                                .rotationEffect(.degrees(effectiveExpanded ? 180 : 0))
                        }
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier(
                    island.context == .offered && island.kind == .information
                        ? WalletAccessibilityID.offerSupportedClaims
                        : WalletAccessibilityID.reviewIslandToggle(island.id)
                )
                .accessibilityValue(
                    hasExpandedContent
                        ? (effectiveExpanded ? "Expanded" : "Collapsed")
                        : "No additional details"
                )
            }
            .padding(12)

            if !island.visibleSummaryValues.isEmpty {
                Divider()
                ReviewValueList(values: island.visibleSummaryValues)
                    .padding(12)
            }

            if effectiveExpanded {
                Divider()
                VStack(alignment: .leading, spacing: 8) {
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
                        if hasContentBeforeTechnicalDetails { Divider() }
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
                .padding(12)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(effectiveExpanded ? accentColor.opacity(0.42) : Color(.separator), lineWidth: 1)
        )
        .matchedGeometryEffect(
            id: "review-island-shell-\(island.id)",
            in: transitionNamespace,
            properties: .frame,
            anchor: .top,
            isSource: true
        )
    }
}

private struct ReviewIslandVisualView: View {
    let island: ReviewIsland
    let accentColor: Color

    private var imageURL: URL? {
        guard let value = island.visual?.imageURI,
              let url = URL(string: value),
              url.scheme?.lowercased() == "https" else { return nil }
        return url
    }

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 10).fill(accentColor.opacity(0.12))
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
        .frame(width: 44, height: 44)
    }

    private var fallback: some View {
        Text(String((island.visual?.fallbackText ?? island.title).prefix(2)))
            .font(.subheadline.weight(.bold))
            .foregroundStyle(accentColor)
    }
}

private extension ReviewIslandKind {
    var accentColor: Color {
        switch self {
        case .issuer, .verifier: return .blue
        case .credential: return .purple
        case .information: return .cyan
        case .validityAndStatus: return .green
        case .purposeAndTransaction: return .orange
        case .requiredAction: return .indigo
        }
    }
}

private struct ReviewValueList: View {
    let values: [ReviewValue]
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var displayValues: [ReviewValue] {
        var previousSupportingText: String?
        return values.filter(\.isVisible).map { value in
            let supportingText = value.supportingText == previousSupportingText ? nil : value.supportingText
            previousSupportingText = value.supportingText
            return ReviewValue(
                label: value.label,
                value: value.value,
                supportingText: supportingText,
                linkURI: value.linkURI
            )
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(Array(displayValues.enumerated()), id: \.offset) { index, value in
                if index > 0 { Divider() }
                if let rendered = value.value?.presentableValue {
                    let supportingText = value.supportingText?.presentableValue
                    let link = safeHTTPSURL(value.linkURI)
                    let stacked = dynamicTypeSize.isAccessibilitySize || value.label.count > 28 || rendered.count > 30 ||
                        value.label.contains("\n") || rendered.contains("\n") || link != nil
                    if let supportingText {
                        Text(supportingText)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(.tint)
                    }
                    if stacked {
                        VStack(alignment: .leading, spacing: 2) {
                            valueLabel(value.label)
                            renderedValue(rendered, link: link)
                        }
                    } else {
                        HStack(alignment: .firstTextBaseline, spacing: 12) {
                            valueLabel(value.label)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            renderedValue(rendered, link: nil)
                                .lineLimit(1)
                                .multilineTextAlignment(.trailing)
                        }
                    }
                }
            }
        }
    }

    private func valueLabel(_ value: String) -> some View {
        Text(value).font(.caption2).foregroundStyle(.secondary)
    }

    @ViewBuilder
    private func renderedValue(_ value: String, link: URL?) -> some View {
        if let link {
            Link(destination: link) {
                Text(value)
                    .font(.caption)
                    .fixedSize(horizontal: false, vertical: true)
            }
        } else {
            Text(value)
                .font(.caption)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func safeHTTPSURL(_ value: String?) -> URL? {
        guard let value, let url = URL(string: value), url.scheme?.lowercased() == "https" else { return nil }
        return url
    }
}

private struct ReviewTechnicalPage: View {
    let island: ReviewIsland
    let transitionNamespace: Namespace.ID
    @AccessibilityFocusState.Binding var accessibilityFocus: String?
    let showsHeader: Bool
    let onBack: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Color.clear
                .frame(width: 1, height: 1)
                .accessibilityElement()
                .accessibilityIdentifier(WalletAccessibilityID.reviewTechnicalDetailsPage)

            if showsHeader { HStack(spacing: 8) {
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
            } }

            ForEach(island.visibleTechnicalSections) { section in
                VStack(alignment: .leading, spacing: 6) {
                    Text(section.title)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.tint)
                    ReviewValueList(values: section.visibleValues)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(.systemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(.separator), lineWidth: 1))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .matchedGeometryEffect(
            id: "review-island-shell-\(island.id)",
            in: transitionNamespace,
            properties: .frame,
            anchor: .top,
            isSource: false
        )
    }
}
