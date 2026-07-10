import SwiftUI

struct UrlEditor: View {
    let title: String
    let label: String
    @Binding var text: String
    let inputIdentifier: String
    let isEnabled: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title)
                .font(.headline)
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            TextEditor(text: $text)
                .font(.footnote.monospaced())
                .frame(minHeight: 52, maxHeight: 76)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(.separator), lineWidth: 1)
                )
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
                .disabled(!isEnabled)
                .accessibilityIdentifier(inputIdentifier)
        }
    }
}
