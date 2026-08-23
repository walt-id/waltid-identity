import CodeScanner
import SwiftUI
import WalletDemoSharingUI

struct UnifiedScanView: View {
    let classify: (String) -> WalletInteractionClassification
    let onAccepted: (String) -> Void
    let onDismiss: () -> Void

    @State private var input = ""
    @State private var error: String?
    @State private var accepted = false
    @State private var scannerID = UUID()

    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("Scan a credential offer or information request.")
                        .foregroundStyle(.secondary)

                    CodeScannerView(
                        codeTypes: [.qr],
                        scanMode: .once,
                        showViewfinder: true,
                        requiresPhotoOutput: false
                    ) { result in
                        guard !accepted else { return }
                        switch result {
                        case .success(let scan):
                            submit(scan.string)
                        case .failure:
                            error = "QR scanning is unavailable. Check camera access or enter the link manually."
                        }
                    }
                    .id(scannerID)
                    .frame(height: 320)
                    .clipShape(RoundedRectangle(cornerRadius: 16))

                    Text("Or enter the wallet link")
                        .font(.headline)

                    TextField("Wallet link", text: $input)
                        .font(.footnote.monospaced())
                        .lineLimit(1)
                        .padding(12)
                        .overlay {
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(error == nil ? Color(.separator) : Color.red, lineWidth: 1)
                        }
                        .textInputAutocapitalization(.never)
                        .disableAutocorrection(true)
                        .disabled(accepted)
                        .accessibilityIdentifier(WalletAccessibilityID.scanInput)

                    if let error {
                        Text(error)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }

                    Button("Continue") {
                        submit(input)
                    }
                    .buttonStyle(WalletPrimaryButtonStyle())
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .disabled(accepted || input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    .accessibilityIdentifier(WalletAccessibilityID.scanSubmit)
                }
                .padding()
            }
            .navigationTitle("Scan")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(action: onDismiss) {
                        Image(systemName: "xmark")
                            .frame(width: 32, height: 32)
                    }
                        .accessibilityLabel("Close")
                        .accessibilityIdentifier(WalletAccessibilityID.scanDismiss)
                }
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        error = nil
                        scannerID = UUID()
                    } label: {
                        Image(systemName: "camera.viewfinder")
                            .frame(width: 32, height: 32)
                    }
                    .accessibilityLabel("Restart camera")
                }
            }
            .accessibilityIdentifier(WalletAccessibilityID.scanSheet)
        }
        .navigationViewStyle(.stack)
    }

    private func submit(_ rawInput: String) {
        guard !accepted else { return }
        switch classify(rawInput) {
        case .supported(_, let normalizedInput):
            accepted = true
            input = ""
            error = nil
            onAccepted(normalizedInput)
        case .invalid(let message), .unsupported(let message):
            error = message
        }
    }
}
