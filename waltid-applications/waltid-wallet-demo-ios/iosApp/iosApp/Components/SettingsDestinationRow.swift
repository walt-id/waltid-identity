import SwiftUI

struct SettingsDestinationRow: View {
    let title: LocalizedStringKey
    let systemImage: String
    let summary: String
    let identifier: String
    private let action: () -> Void

    init(_ title: LocalizedStringKey, systemImage: String, summary: String, identifier: String,
         action: @escaping () -> Void) {
        self.title = title
        self.systemImage = systemImage
        self.summary = summary
        self.identifier = identifier
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack {
                Label {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(title).foregroundStyle(.primary)
                        Text(summary).font(.footnote).foregroundStyle(.secondary)
                    }
                } icon: {
                    Image(systemName: systemImage).foregroundStyle(.tint).accessibilityHidden(true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "chevron.forward").font(.footnote).foregroundStyle(.tint).accessibilityHidden(true)
            }
            .contentShape(Rectangle())
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(title))
            .accessibilityValue(summary)
            .accessibilityIdentifier(identifier)
        }
        .buttonStyle(.plain)
    }
}
