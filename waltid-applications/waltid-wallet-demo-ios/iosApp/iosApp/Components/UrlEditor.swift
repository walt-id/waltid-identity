import CodeScanner
import SwiftUI

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
            Text(title)
                .font(.headline)
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
    @State private var scannerVisible = false
    @State private var scannerError: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            UrlEditor(
                title: title,
                label: label,
                text: $text,
                inputIdentifier: inputIdentifier,
                isEnabled: isEnabled,
                focusResetKey: focusResetKey
            )

            Button {
                scannerVisible = true
            } label: {
                Label("Scan QR", systemImage: "qrcode.viewfinder")
            }
            .buttonStyle(.bordered)
            .disabled(!isEnabled)
            .accessibilityIdentifier(scanButtonIdentifier)
        }
        .sheet(isPresented: $scannerVisible) {
            NavigationView {
                CodeScannerView(
                    codeTypes: [.qr],
                    scanMode: .once,
                    showViewfinder: true,
                    requiresPhotoOutput: false
                ) { result in
                    switch result {
                    case .success(let scan):
                        text = scan.string.trimmingCharacters(in: .whitespacesAndNewlines)
                        scannerVisible = false
                    case .failure:
                        scannerVisible = false
                        scannerError = "QR scanning is unavailable. Check camera access and try again."
                    }
                }
                .navigationTitle("Scan QR code")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") {
                            scannerVisible = false
                        }
                    }
                }
            }
            .navigationViewStyle(.stack)
        }
        .alert(
            "QR scanner unavailable",
            isPresented: Binding(
                get: { scannerError != nil },
                set: { if !$0 { scannerError = nil } }
            )
        ) {
            Button("OK", role: .cancel) {
                scannerError = nil
            }
        } message: {
            Text(scannerError ?? "")
        }
    }
}
