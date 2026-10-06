import CodeScanner
import SwiftUI

struct ScanCodeButton: View {
    var title: String = "Use camera"
    let identifier: String
    let isEnabled: Bool
    let onCodeScanned: (String) -> Void
    @State private var scannerVisible = false
    @State private var scannerError: String?

    var body: some View {
        Button {
            scannerVisible = true
        } label: {
            Label(title, systemImage: "qrcode.viewfinder")
        }
        .buttonStyle(.bordered)
        .disabled(!isEnabled)
        .accessibilityIdentifier(identifier)
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
                        scannerVisible = false
                        onCodeScanned(scan.string.trimmingCharacters(in: .whitespacesAndNewlines))
                    case .failure:
                        scannerVisible = false
                        scannerError = "QR scanning is unavailable. Check camera access and try again."
                    }
                }
                .navigationTitle("Scan QR code")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button { scannerVisible = false } label: { Image(systemName: "xmark") }
                            .accessibilityLabel("Close")
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
