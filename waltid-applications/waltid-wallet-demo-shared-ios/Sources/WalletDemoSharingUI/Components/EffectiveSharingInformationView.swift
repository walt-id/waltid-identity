import SwiftUI
import WalletSDK

struct EffectiveSharingInformationView: View {
    let state: ClaimReviewState
    let enabled: Bool
    let readOnly: Bool
    let onToggle: (PresentationDisclosureSelection) -> Void
    let onRetry: () -> Void

    var body: some View {
        WalletSection("Information to share") {
            VStack(alignment: .leading, spacing: 14) {
                if case .loading = state {
                    Text(state.previous == nil ? "Updating information…" : "Updating information… Previous review shown below.")
                        .padding(16).accessibilityIdentifier("review-claims-loading")
                }
                if case .failed(let message) = state {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Couldn't update the information to share.").foregroundStyle(.red)
                        Text(message).font(.footnote)
                        Button("Try again", action: onRetry).disabled(!enabled)
                    }.padding(16).accessibilityIdentifier("review-claims-error")
                }
                if let review = state.previous {
                    ForEach(review.groups) { group in
                        ReviewInformationGroup(title: group.title) {
                            ForEach(Array(group.contributions.enumerated()), id: \.element.id) { index, contribution in
                                if group.contributions.count > 1 { Text("Selected requirement \(index + 1)").font(.subheadline.weight(.semibold)) }
                                contributionBody(contribution)
                            }
                        }
                    }
                }
            }
        }.accessibilityElement(children: .contain)
            .accessibilityIdentifier("review-information-to-share")
    }
    private var controlsEnabled: Bool { enabled && state.canConfirm }
    @ViewBuilder private func contributionBody(_ contribution: EffectiveClaimContribution) -> some View {
        if contribution.separatesAlwaysShared {
            let ordinary = contribution.nodes.filter { !$0.technical }
            let always = ordinary.sharingSection(always: true)
            let requested = ordinary.sharingSection(always: false)
            if !always.isEmpty {
                MetadataDisclosure(title: "Always shared", initiallyExpanded: true,
                    accessibilityIdentifier: "review-always-shared", indicatorColor: .secondary) {
                    EffectiveClaimChildren(nodes: always, enabled: controlsEnabled, readOnly: readOnly, onToggle: onToggle)
                }
            }
            if !requested.isEmpty || !contribution.detachedChoices.isEmpty {
                MetadataDisclosure(title: "Requested information", initiallyExpanded: true,
                    accessibilityIdentifier: "review-requested-information", indicatorColor: .secondary) {
                    VStack(alignment: .leading, spacing: 10) {
                        ForEach(contribution.detachedChoices) { choice in
                            EffectiveClaimChoiceView(choice: choice, enabled: controlsEnabled, readOnly: readOnly,
                                showValue: !choice.included, onToggle: onToggle)
                        }
                        EffectiveClaimChildren(nodes: requested, enabled: controlsEnabled, readOnly: readOnly, onToggle: onToggle)
                    }
                }
            }
            EffectiveClaimChildren(nodes: contribution.nodes.filter(\.technical), enabled: controlsEnabled,
                readOnly: readOnly, onToggle: onToggle)
        } else {
            if contribution.wholeCredential { Text("This credential is shared in full").font(.footnote) }
            ForEach(contribution.detachedChoices) { choice in
                EffectiveClaimChoiceView(choice: choice, enabled: controlsEnabled, readOnly: readOnly,
                    showValue: !choice.included, onToggle: onToggle)
            }
            EffectiveClaimChildren(nodes: contribution.nodes, enabled: controlsEnabled, readOnly: readOnly, onToggle: onToggle)
        }
    }
}

private struct EffectiveClaimChildren: View {
    let nodes: [EffectiveClaimNode]
    let enabled: Bool
    let readOnly: Bool
    let onToggle: (PresentationDisclosureSelection) -> Void
    var technicalContext = false
    @State private var count = 25
    private var ordinary: [EffectiveClaimNode] { technicalContext ? nodes : nodes.filter { !$0.technical } }
    private var technical: [EffectiveClaimNode] { technicalContext ? [] : nodes.filter(\.technical) }
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            ForEach(Array(ordinary.prefix(count))) { node in
                EffectiveClaimNodeView(node: node, enabled: enabled, readOnly: readOnly, onToggle: onToggle, technicalContext: technicalContext)
            }
            if ordinary.count > count { Button("Show more") { count += 25 } }
            if !technical.isEmpty {
                MetadataDisclosure(title: "Technical details", initiallyExpanded: false) {
                    EffectiveClaimChildren(nodes: technical, enabled: enabled, readOnly: readOnly, onToggle: onToggle, technicalContext: true)
                }
            }
        }
    }
}

private struct EffectiveClaimNodeView: View {
    let node: EffectiveClaimNode
    let enabled: Bool
    let readOnly: Bool
    let onToggle: (PresentationDisclosureSelection) -> Void
    var technicalContext = false
    @State private var count = 25
    private var status: String? { node.choice?.status ?? (technicalContext ? nil : node.reason) }
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if node.kind == .scalar {
                if let choice = node.choice {
                    EffectiveClaimChoiceView(choice: choice, enabled: enabled, readOnly: readOnly, onToggle: onToggle)
                } else if !node.label.isEmpty { Text(node.label).font(.subheadline.weight(.semibold)) }
                if let value = node.value { ClaimValueView(value: value, path: node.id) }
                if node.choice == nil, let status { Text(status).font(.footnote).foregroundStyle(.secondary) }
            } else if !node.label.isEmpty {
                MetadataDisclosure(title: node.label, initiallyExpanded: node.initiallyExpanded,
                    accessibilityIdentifier: WalletAccessibilityID.claim(node.id.id), supportingText: status) { containerBody }
            } else {
                if let status { Text(status).font(.footnote).foregroundStyle(.secondary) }
                containerBody
            }
        }.accessibilityElement(children: .contain)
            .accessibilityIdentifier("review-claim-" + node.id.id)
    }
    @ViewBuilder private var containerBody: some View {
        if let choice = node.choice {
            EffectiveClaimChoiceView(choice: choice, enabled: enabled, readOnly: readOnly,
                showLabel: false, showStatus: false, onToggle: onToggle)
        }
        if node.children.isEmpty { Text(node.kind == .object ? "No fields" : "No items").font(.footnote) }
        if node.kind == .object {
            EffectiveClaimChildren(nodes: node.children, enabled: enabled, readOnly: readOnly, onToggle: onToggle, technicalContext: technicalContext)
        } else {
            VStack(alignment: .leading, spacing: 10) {
                ForEach(Array(node.children.prefix(count))) { child in
                    if node.usesCards {
                        EffectiveClaimNodeView(node: child, enabled: enabled, readOnly: readOnly, onToggle: onToggle, technicalContext: technicalContext)
                            .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.secondary.opacity(0.25)))
                    } else {
                        EffectiveClaimNodeView(node: child, enabled: enabled, readOnly: readOnly, onToggle: onToggle, technicalContext: technicalContext)
                    }
                }
                if node.children.count > count { Button("Show more") { count += 25 } }
            }
        }
    }
}

private struct EffectiveClaimChoiceView: View {
    let choice: EffectiveClaimChoice
    let enabled: Bool
    let readOnly: Bool
    var showValue = false
    var showLabel = true
    var showStatus = true
    let onToggle: (PresentationDisclosureSelection) -> Void
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if choice.forcedReason == nil && !readOnly {
                Toggle(isOn: Binding(get: { choice.selected }, set: { _ in onToggle(choice.selection) })) { label }
                    .toggleStyle(ReviewCheckboxToggleStyle()).disabled(!enabled)
                    .accessibilityIdentifier(WalletAccessibilityID.presentationDisclosureToggle(choice.id))
                    .accessibilityValue(choice.status)
            } else { label }
            if showValue && !choice.selected { ClaimValueView(value: choice.item.value, path: choice.item.path) }
        }
    }
    private var label: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(showLabel ? choice.item.label : "Include this information").font(.subheadline.weight(.semibold))
            if showStatus { Text(choice.status).font(.footnote).foregroundStyle(.secondary) }
        }
    }
}
