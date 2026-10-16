import CodeScanner
import SwiftUI

/// Camera QR scanner presented as a sheet. It reports the first scanned code once and leaves
/// dismissing the sheet and showing errors to the caller.
public struct QRCodeScannerSheet: View {
    private let onScan: (String) -> Void
    private let onFailure: () -> Void
    private let onClose: () -> Void

    public init(
        onScan: @escaping (String) -> Void,
        onFailure: @escaping () -> Void,
        onClose: @escaping () -> Void
    ) {
        self.onScan = onScan
        self.onFailure = onFailure
        self.onClose = onClose
    }

    public var body: some View {
        NavigationView {
            CodeScannerView(
                codeTypes: [.qr],
                scanMode: .once,
                showViewfinder: true,
                requiresPhotoOutput: false
            ) { result in
                switch result {
                case .success(let scan):
                    onScan(scan.string)
                case .failure:
                    onFailure()
                }
            }
            .navigationTitle("Scan QR code")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close", action: onClose)
                }
            }
        }
        .navigationViewStyle(.stack)
    }
}
