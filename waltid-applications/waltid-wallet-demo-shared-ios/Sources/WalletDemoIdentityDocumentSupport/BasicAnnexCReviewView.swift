import SwiftUI
import WalletSDK

/// Deliberately plain consent UI for an Annex C presentation, shared by both demo extensions.
///
/// It exists so the two demos exercise the same flow rather than to be a design reference: it shows
/// the verified origin, what is requested, which credential answers each request, and the reader
/// trust result, which is the minimum a user needs to consent meaningfully.
@available(iOS 26.0, *)
public struct BasicAnnexCReviewView: View {
    @StateObject private var model: AnnexCPresentationModel

    /// Creates the review UI over a prepared presentation model.
    public init(model: AnnexCPresentationModel) {
        _model = StateObject(wrappedValue: model)
    }

    /// Creates the review UI for one Apple request context.
    ///
    /// - Parameters:
    ///   - context: Apple's request context.
    ///   - makeWallet: Opens the wallet shared with the host app.
    public init(
        context: any AnnexCRequestContext,
        makeWallet: @escaping @Sendable () async throws -> any AnnexCPresentationWallet
    ) {
        self.init(model: AnnexCPresentationModel(context: context, makeWallet: makeWallet))
    }

    public var body: some View {
        NavigationStack {
            content
                .navigationTitle("Share documents")
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { model.cancel() }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Share") { Task { await model.submit() } }
                            .disabled(model.preview == nil || !model.hasCompleteSelection || model.isSubmitting)
                    }
                }
                .task { await model.prepare() }
        }
    }

    @ViewBuilder
    private var content: some View {
        if let failure = model.failure {
            ContentUnavailableView(
                "Unable to present",
                systemImage: "exclamationmark.shield",
                description: Text(failure)
            )
        } else if let preview = model.preview {
            List {
                Section("Requesting website") {
                    Text(preview.verifiedOrigin)
                }
                Section("Requested information") {
                    ForEach(Array(preview.parsedRequest.documents.enumerated()), id: \.offset) { _, document in
                        VStack(alignment: .leading, spacing: 4) {
                            Text(document.documentType).font(.headline)
                            Text(document.namespaces.values.flatMap { $0 }.sorted().joined(separator: ", "))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                Section("Credentials") {
                    ForEach(model.queryIDs, id: \.self) { queryID in
                        credentialPicker(queryID: queryID)
                    }
                }
                Section("Reader trust") {
                    Text(readerTrustDescription(preview.readerTrust))
                }
            }
        } else {
            ProgressView("Preparing request")
        }
    }

    @ViewBuilder
    private func credentialPicker(queryID: String) -> some View {
        let options = model.options(for: queryID)
        Picker(
            "Credential",
            selection: Binding<String?>(
                get: { model.selectedCredentialIDsByQuery[queryID] },
                set: { model.selectedCredentialIDsByQuery[queryID] = $0 }
            )
        ) {
            Text("Choose a credential").tag(nil as String?)
            ForEach(options) { option in
                Text(title(option)).tag(option.credentialID as String?)
            }
        }
        if let credentialID = model.selectedCredentialIDsByQuery[queryID],
           let selected = options.first(where: { $0.credentialID == credentialID }) {
            Text(detail(selected))
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private func title(_ option: PresentationCredentialOption) -> String {
        option.label ?? option.issuer ?? "Credential"
    }

    private func detail(_ option: PresentationCredentialOption) -> String {
        [option.issuer, option.subject, option.credentialID]
            .compactMap { $0 }
            .joined(separator: " · ")
    }
}
