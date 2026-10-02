import SwiftUI
import WalletDemoSharingUI

struct UrlEditor: View {
    let title: String
    let label: String
    @Binding var text: String
    let inputIdentifier: String
    let isEnabled: Bool
    let focusResetKey: Int
    @FocusState private var isInputFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if !title.isEmpty { Text(title).font(.headline) }
            Text(label)
                .font(.caption)
                .foregroundStyle(.secondary)
            TextField(label, text: $text)
                .font(.footnote.monospaced())
                .lineLimit(1)
                .padding(8)
                .frame(minHeight: 52)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(.separator), lineWidth: 1)
                )
                .textInputAutocapitalization(.never)
                .disableAutocorrection(true)
                .submitLabel(.done)
                .onSubmit {
                    isInputFocused = false
                }
                .disabled(!isEnabled)
                .focused($isInputFocused)
                .accessibilityIdentifier(inputIdentifier)
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("Done") {
                    isInputFocused = false
                }
            }
        }
        .onChange(of: isEnabled) { enabled in
            if !enabled {
                isInputFocused = false
            }
        }
        .onChange(of: focusResetKey) { _ in
            isInputFocused = false
        }
    }
}

struct ScannableUrlEditor: View {
    let title: String
    let label: String
    @Binding var text: String
    let inputIdentifier: String
    let scanButtonIdentifier: String
    let isEnabled: Bool
    let focusResetKey: Int
    var onCodeScanned: ((String) -> Void)? = nil
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            UrlEditor(title: title, label: label, text: $text, inputIdentifier: inputIdentifier,
                isEnabled: isEnabled, focusResetKey: focusResetKey)
            ScanCodeButton(title: "Scan QR", identifier: scanButtonIdentifier, isEnabled: isEnabled) { value in
                text = value
                onCodeScanned?(value)
            }
        }
    }
}
