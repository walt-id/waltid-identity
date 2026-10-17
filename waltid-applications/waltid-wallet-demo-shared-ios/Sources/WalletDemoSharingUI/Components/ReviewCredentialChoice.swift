import SwiftUI

/// Whole-row selection, shared by online and nearby consent. The row never navigates.
public struct ReviewCredentialChoice<Content: View>: View {
    private let selected: Bool
    private let multiple: Bool
    private let enabled: Bool
    private let readOnly: Bool
    private let hint: String?
    private let onSelect: () -> Void
    private let content: Content

    public init(selected: Bool, multiple: Bool = false, enabled: Bool = true, readOnly: Bool = false,
                hint: String? = nil, onSelect: @escaping () -> Void, @ViewBuilder content: () -> Content) {
        self.selected = selected; self.multiple = multiple; self.enabled = enabled
        self.readOnly = readOnly; self.hint = hint; self.onSelect = onSelect; self.content = content()
    }

    public var body: some View {
        Group {
            if readOnly { row }
            else {
                Button { if multiple || !selected { onSelect() } } label: { row }
                    .buttonStyle(.plain).disabled(!enabled)
                    .accessibilityAddTraits(selected ? [.isSelected] : [])
                    .accessibilityValue(selected ? String(localized: "Selected", bundle: .module) : String(localized: "Not selected", bundle: .module))
            }
        }
    }

    private var row: some View {
        HStack(alignment: .center, spacing: 12) {
            if !readOnly {
                Image(systemName: multiple ? (selected ? "checkmark.square.fill" : "square")
                    : (selected ? "largecircle.fill.circle" : "circle"))
                    .font(.title3).foregroundStyle(selected ? Color.accentColor : Color.secondary)
                    .accessibilityHidden(true)
            }
            VStack(alignment: .leading, spacing: 6) {
                content
                if let hint { Text(hint).font(.footnote).foregroundStyle(.secondary) }
            }.frame(maxWidth: .infinity, alignment: .leading)
        }.padding(.horizontal, 16).padding(.vertical, 12).contentShape(Rectangle())
    }
}
