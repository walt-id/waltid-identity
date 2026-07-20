import SwiftUI
import WalletSDK

struct OfferPreview {
    let issuer: IssuerMetadata
    let offeredCredentials: [OfferedCredentialMetadata]
    let transactionCode: TransactionCodeRequirement?
}

struct OfferReviewView: View {
    let preview: OfferPreview
    let isAcceptEnabled: Bool
    let isReviewEnabled: Bool
    let txCode: String
    let onTxCodeChange: (String) -> Void
    let onAccept: () -> Void
    let onDecline: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Credential offer")
                .font(.headline)

            Text("Issuer")
                .font(.caption)
                .foregroundStyle(.secondary)
            MetadataIdentityView(
                display: preview.issuer.display,
                fallbackName: preview.issuer.credentialIssuer.isEmpty
                    ? "Unknown issuer"
                    : preview.issuer.credentialIssuer,
                supportingText: preview.issuer.display?.name == preview.issuer.credentialIssuer
                    ? nil
                    : preview.issuer.credentialIssuer
            )

            if !preview.offeredCredentials.isEmpty {
                Text("Offered credentials")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                ForEach(Array(preview.offeredCredentials.enumerated()), id: \.offset) { _, credential in
                    OfferedCredentialView(credential: credential)
                }
            }

            if let requirement = preview.transactionCode {
                VStack(alignment: .leading, spacing: 6) {
                    Text(requirement.description ?? "Enter the transaction code provided by the issuer.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    SecureField(
                        "Transaction code",
                        text: Binding(get: { txCode }, set: onTxCodeChange)
                    )
                    .textContentType(.oneTimeCode)
                    .keyboardType(requirement.inputMode == .numeric ? .numberPad : .asciiCapable)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                    .padding(8)
                    .frame(minHeight: 52)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(.separator), lineWidth: 1))
                    .disabled(!isReviewEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.txCodeInput)

                    if let length = requirement.length {
                        Text("\(length) characters")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }

            HStack(spacing: 8) {
                Button("Accept", action: onAccept)
                    .buttonStyle(.borderedProminent)
                    .tint(.waltBlue)
                    .disabled(!isAcceptEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.offerAcceptButton)

                Button("Decline", action: onDecline)
                    .buttonStyle(.bordered)
                    .disabled(!isReviewEnabled)
                    .accessibilityIdentifier(WalletAccessibilityID.offerDeclineButton)
            }
        }
    }
}

struct MetadataIdentityView: View {
    let display: MetadataDisplay?
    let fallbackName: String
    let supportingText: String?

    private var name: String {
        guard let displayName = display?.name?.trimmingCharacters(in: .whitespacesAndNewlines),
              !displayName.isEmpty else {
            return fallbackName
        }
        return displayName
    }

    private var logoURL: URL? {
        guard let value = display?.logoURI,
              let url = URL(string: value),
              url.scheme?.lowercased() == "https" else {
            return nil
        }
        return url
    }

    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(.systemGray6))
                if let logoURL {
                    AsyncImage(url: logoURL) { phase in
                        switch phase {
                        case let .success(image):
                            image.resizable().scaledToFit()
                        case .empty:
                            ProgressView()
                        case .failure:
                            MetadataLogoFallback(name: name)
                        @unknown default:
                            MetadataLogoFallback(name: name)
                        }
                    }
                    .accessibilityLabel(display?.logoAltText ?? "\(name) logo")
                } else {
                    MetadataLogoFallback(name: name)
                }
            }
            .frame(width: 48, height: 48)

            VStack(alignment: .leading, spacing: 2) {
                Text(name)
                    .font(.body.weight(.semibold))
                    .lineLimit(2)
                if let supportingText, !supportingText.isEmpty {
                    Text(supportingText)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }
            }
        }
    }
}

private struct MetadataLogoFallback: View {
    let name: String

    var body: some View {
        Text(name.first.map { String($0).uppercased() } ?? "?")
            .font(.headline)
    }
}

struct MetadataDetailLine: View {
    let label: String
    let value: String?

    var body: some View {
        if let value, !value.isEmpty {
            VStack(alignment: .leading, spacing: 1) {
                Text(label).font(.caption2).foregroundStyle(.secondary)
                Text(value).font(.caption)
            }
        }
    }
}

private struct OfferedCredentialView: View {
    let credential: OfferedCredentialMetadata

    private var title: String {
        credential.display?.name ?? credential.vct ?? credential.doctype ?? credential.configurationID
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            MetadataIdentityView(
                display: credential.display,
                fallbackName: title,
                supportingText: credential.display?.description
            )
            MetadataDetailLine(label: "Format", value: credential.format)
            MetadataDetailLine(label: "Type", value: credential.vct ?? credential.doctype)
            if !credential.claims.isEmpty {
                Text("Claims")
                    .font(.caption.weight(.medium))
                ForEach(Array(credential.claims.enumerated()), id: \.offset) { _, claim in
                    let fallback = claim.path.joined(separator: ".")
                    let trimmedName = claim.displayName?.trimmingCharacters(in: .whitespacesAndNewlines)
                    let name = trimmedName.flatMap { value in value.isEmpty ? nil : value } ?? fallback
                    Text(claim.mandatory == true ? "\(name) (required)" : name)
                        .font(.caption)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
