import SwiftUI
import AVFoundation

struct ReceiveView: View {
    @ObservedObject var viewModel: WalletViewModel
    @State private var mode: ReceiveMode = .scan
    @State private var showScanner = false

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Receive")
                .font(.headline)

            Picker("Mode", selection: $mode) {
                Text("Scan QR").tag(ReceiveMode.scan)
                Text("Enter link").tag(ReceiveMode.manual)
            }
            .pickerStyle(.segmented)

            if mode == .scan {
                scanSection
            } else {
                manualSection
            }
        }
        .sheet(isPresented: $showScanner) {
            NavigationStack {
                QRScannerView { scanned in
                    showScanner = false
                    viewModel.offerUrl = scanned
                    viewModel.resolveOffer()
                }
                .ignoresSafeArea()
                .navigationTitle("Scan QR Code")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showScanner = false }
                    }
                }
            }
        }
    }

    @ViewBuilder
    private var scanSection: some View {
        if viewModel.txCodeRequired {
            txCodeSection
        } else {
            Button {
                showScanner = true
            } label: {
                Label("Scan QR Code", systemImage: "qrcode.viewfinder")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(!viewModel.isReady || viewModel.isLoading)
            .accessibilityIdentifier(WalletAccessibilityID.receiveNewButton)
        }
    }

    @ViewBuilder
    private var manualSection: some View {
        TextEditor(text: $viewModel.offerUrl)
            .font(.footnote.monospaced())
            .frame(minHeight: 72, maxHeight: 96)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color(.separator), lineWidth: 1)
            )
            .textInputAutocapitalization(.never)
            .disableAutocorrection(true)
            .accessibilityIdentifier(WalletAccessibilityID.offerInput)

        if viewModel.txCodeRequired {
            txCodeSection
        } else {
            Button("Receive") {
                viewModel.resolveOffer()
            }
            .buttonStyle(.borderedProminent)
            .tint(.waltBlue)
            .disabled(
                !viewModel.isReady ||
                viewModel.offerUrl.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
                viewModel.isLoading
            )
            .frame(maxWidth: .infinity)
            .accessibilityIdentifier(WalletAccessibilityID.receiveButton)
        }
    }

    @ViewBuilder
    private var txCodeSection: some View {
        Text("This offer requires a PIN code. Enter it below and tap Receive.")
            .font(.subheadline)
            .foregroundStyle(.secondary)

        TextField("PIN code", text: $viewModel.txCode)
            .keyboardType(.numberPad)
            .textFieldStyle(.roundedBorder)
            .accessibilityIdentifier("wallet.txCodeInput")

        Button("Receive") {
            viewModel.receiveCredential()
        }
        .buttonStyle(.borderedProminent)
        .tint(.waltBlue)
        .disabled(
            !viewModel.isReady ||
            viewModel.txCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ||
            viewModel.isLoading
        )
        .frame(maxWidth: .infinity)
        .accessibilityIdentifier(WalletAccessibilityID.receiveButton)
    }
}

private enum ReceiveMode {
    case scan, manual
}

// MARK: - QR Scanner (UIKit bridge, no separate file needed)

private struct QRScannerView: UIViewControllerRepresentable {
    let onScanned: (String) -> Void

    func makeUIViewController(context: Context) -> QRScannerViewController {
        let vc = QRScannerViewController()
        vc.onScanned = onScanned
        return vc
    }

    func updateUIViewController(_ uiViewController: QRScannerViewController, context: Context) {}
}

final class QRScannerViewController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var onScanned: ((String) -> Void)?

    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var hasScanned = false

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        setupSession()
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if captureSession?.isRunning == false {
            DispatchQueue.global(qos: .background).async { [weak self] in
                self?.captureSession?.startRunning()
            }
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        if captureSession?.isRunning == true {
            captureSession?.stopRunning()
        }
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
    }

    private func setupSession() {
        let session = AVCaptureSession()
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device),
              session.canAddInput(input) else {
            showUnavailableMessage()
            return
        }

        session.addInput(input)

        let output = AVCaptureMetadataOutput()
        guard session.canAddOutput(output) else {
            showUnavailableMessage()
            return
        }

        session.addOutput(output)
        output.setMetadataObjectsDelegate(self, queue: .main)
        output.metadataObjectTypes = [.qr]

        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        preview.frame = view.layer.bounds
        view.layer.addSublayer(preview)

        captureSession = session
        previewLayer = preview

        DispatchQueue.global(qos: .background).async {
            session.startRunning()
        }
    }

    private func showUnavailableMessage() {
        let label = UILabel()
        label.text = "Camera not available"
        label.textColor = .white
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
    }

    func metadataOutput(
        _ output: AVCaptureMetadataOutput,
        didOutput metadataObjects: [AVMetadataObject],
        from connection: AVCaptureConnection
    ) {
        guard !hasScanned,
              let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let value = object.stringValue,
              !value.isEmpty else { return }

        hasScanned = true
        captureSession?.stopRunning()
        onScanned?(value)
    }
}
