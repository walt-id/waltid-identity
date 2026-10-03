import SwiftUI
import WalletDemoSharingUI
import UniformTypeIdentifiers
import WalletDemoIdentityDocumentSupport
import WalletSDK

struct ReaderTrustSettingsView: View {
    @ObservedObject var controller: DemoReaderTrustSettingsController
    @State private var importing = false
    @State private var importTask: Task<Void, Never>?
    @State private var confirmReset = false
    @State private var removal: Removal?

    private struct Removal: Identifiable {
        let id = UUID()
        let name: String
        let readerCA: Bool
        let action: () -> Void
    }

    var body: some View {
        List {
            Section {
                policyChoice(
                    .allowAnonymousOrUntrusted,
                    title: "Allow anonymous or untrusted readers",
                    detail: "Anonymous or untrusted readers may request credentials. Their authentication status is shown during review."
                )
                .accessibilityIdentifier(WalletAccessibilityID.readerTrustAllowUntrusted)
                policyChoice(
                    .requireTrusted,
                    title: "Require trusted readers",
                    detail: "Only trusted readers can proceed to review. Evidence-only RICAL providers do not establish trust."
                )
                .accessibilityIdentifier(WalletAccessibilityID.readerTrustRequireTrusted)
                if controller.settings.readerPolicy == .requireTrusted,
                   controller.settings.trustAnchors.isEmpty,
                   !controller.settings.ricalProviders.contains(where: \.establishesReaderTrust) {
                    Text("No reader CAs or trust-establishing RICAL providers are configured. All readers will be rejected.")
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            } header: {
                Text("Reader policy")
                    .accessibilityIdentifier(WalletAccessibilityID.readerTrustPolicy)
            }

            Section("Reader CAs") {
                if controller.settings.trustAnchors.isEmpty { Text("No reader CAs configured").foregroundStyle(.secondary) }
                ForEach(controller.settings.trustAnchors) { anchor in
                    configuredMaterialRow(title: anchor.displayName, detail: "Reader CA") {
                        removal = Removal(name: anchor.displayName, readerCA: true, action: { controller.removeReaderAuthority(id: anchor.id) })
                    }
                }
            }
            Section("RICAL providers") {
                if controller.settings.ricalProviders.isEmpty { Text("No RICAL providers configured").foregroundStyle(.secondary) }
                ForEach(controller.settings.ricalProviders) { provider in
                    configuredMaterialRow(title: provider.providerID, detail: provider.establishesReaderTrust
                        ? "RICAL provider · Establishes reader trust" : "RICAL provider · Evidence only") {
                            removal = Removal(name: provider.providerID, readerCA: false, action: { controller.removeRICALProvider(id: provider.id) })
                        }
                }
            }

            Section("Import") {
                Button { importing = true } label: { Label("Import trust material", systemImage: "square.and.arrow.down") }
                    .disabled(controller.importInProgress || controller.loading)
                    .accessibilityIdentifier(WalletAccessibilityID.readerTrustImport)
                Text("Import a reader CA certificate in DER or PEM format, or a versioned walt.id JSON trust bundle. Private keys and PKCS#12 files are not supported.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                if controller.importInProgress {
                    ProgressView("Importing trust material…")
                        .accessibilityIdentifier(WalletAccessibilityID.readerTrustImportProgress)
                }
            }

            if !controller.settings.trustAnchors.isEmpty ||
                !controller.settings.ricalProviders.isEmpty ||
                controller.settings.readerPolicy != .allowAnonymousOrUntrusted {
                Section {
                    Button(role: .destructive) { confirmReset = true } label: { Label("Reset reader trust", systemImage: "arrow.counterclockwise") }
                        .accessibilityIdentifier(WalletAccessibilityID.readerTrustReset)
                }
            }
        }
        .frame(maxWidth: 640)
        .frame(maxWidth: .infinity)
        .background(Color(uiColor: .systemGroupedBackground))
        .navigationTitle("Reader authentication")
        .navigationBarTitleDisplayMode(.inline)
        .onDisappear { importTask?.cancel(); controller.cancelImport() }
        .fileImporter(
            isPresented: $importing,
            allowedContentTypes: [.data],
            allowsMultipleSelection: false,
            onCompletion: handleImportResult
        )
        .sheet(isPresented: importReviewPresented) {
            if let preview = controller.pendingImport {
                ReaderTrustImportReviewView(
                    preview: preview,
                    confirm: controller.confirmImport,
                    cancel: controller.cancelImport
                )
            }
        }
        .confirmationDialog(
            "Reset reader trust?",
            isPresented: $confirmReset,
            titleVisibility: .visible
        ) {
            Button("Reset reader trust", role: .destructive, action: controller.reset)
                .accessibilityIdentifier(WalletAccessibilityID.readerTrustResetConfirm)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This removes all imported reader CAs and RICAL providers and allows anonymous or untrusted readers again.")
        }
        .confirmationDialog(removal.map { String(localized: "Remove \($0.name)?") } ?? "", isPresented: Binding(
            get: { removal != nil }, set: { if !$0 { removal = nil } }), titleVisibility: .visible) {
                if let item = removal {
                    Button("Remove", role: .destructive) { item.action(); removal = nil }
                        .accessibilityIdentifier("reader-trust-remove-confirm")
                }
                Button("Cancel", role: .cancel) { removal = nil }
        } message: {
            Text(removal?.readerCA == true
                ? "This reader CA will no longer be used by new nearby-sharing sessions. An active session keeps its existing trust settings."
                : "This RICAL provider will no longer be used by new nearby-sharing sessions. An active session keeps its existing trust settings.")
        }
        .alert("Could not update reader trust", isPresented: errorPresented) {
            Button("Close", action: controller.dismissError)
        } message: {
            Text(controller.errorMessage ?? "Could not import the trust material. Choose another file or try again.")
                .accessibilityIdentifier(WalletAccessibilityID.readerTrustError)
        }
    }

    private func policyChoice(
        _ policy: ProximityStoredReaderPolicy,
        title: LocalizedStringKey,
        detail: LocalizedStringKey
    ) -> some View {
        Button {
            controller.setReaderPolicy(policy)
        } label: {
            HStack(alignment: .top, spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(title).foregroundStyle(.primary)
                    Text(detail).font(.footnote).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Image(systemName: controller.settings.readerPolicy == policy
                    ? "largecircle.fill.circle"
                    : "circle")
                    .foregroundStyle(.tint).accessibilityHidden(true)
            }
        }
        .buttonStyle(.plain)
        .disabled(controller.loading || controller.importInProgress)
        .accessibilityValue(
            controller.settings.readerPolicy == policy ? "Selected" : "Not selected"
        )
        .accessibilityAddTraits(
            controller.settings.readerPolicy == policy ? .isSelected : []
        )
    }

    private func configuredMaterialRow(
        title: String,
        detail: LocalizedStringKey,
        remove: @escaping () -> Void
    ) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                Text(detail).font(.footnote).foregroundStyle(.secondary)
            }
            Spacer()
            Button(role: .destructive, action: remove) {
                Image(systemName: "trash").frame(minWidth: 44, minHeight: 44)
            }
            .buttonStyle(.borderless)
            .disabled(controller.loading || controller.importInProgress)
            .accessibilityLabel("Remove \(title)")
            .help("Remove \(title)")
        }
    }

    private func handleImportResult(_ result: Result<[URL], Error>) {
        importTask?.cancel()
        importTask = Task {
            do {
                switch try await ReaderTrustImportFileLoader.loadOffMain(result) {
                case .cancelled: return
                case let .selected(sourceName, data):
                    await controller.prepareImport(sourceName: sourceName, data: data)
                }
            } catch is CancellationError { return }
            catch { controller.reportImportError(error.localizedDescription) }
        }
    }

    private var importReviewPresented: Binding<Bool> {
        Binding(
            get: { controller.pendingImport != nil },
            set: { if !$0 { controller.cancelImport() } }
        )
    }

    private var errorPresented: Binding<Bool> {
        Binding(
            get: { controller.errorMessage != nil },
            set: { if !$0 { controller.dismissError() } }
        )
    }
}
