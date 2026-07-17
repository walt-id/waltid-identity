import SwiftUI
import UIKit
import WalletSDK

struct CredentialCardView: View {
    let details: CredentialDetails

    init(details: CredentialDetails) {
        self.details = details
    }

    init(credential: Credential) {
        self.details = CredentialDisplayNormalizer.details(for: credential)
    }

    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            CredentialPortraitView(summary: details.cardSummary, size: 56)

            VStack(alignment: .leading, spacing: 5) {
                Text(details.cardSummary.title)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(2)
                if let credentialType = details.cardSummary.credentialType {
                    Text(credentialType)
                        .font(.caption)
                        .foregroundColor(.accentColor)
                }
                if let holderName = details.cardSummary.holderName {
                    Text(holderName)
                        .font(.caption)
                        .foregroundColor(.primary)
                }
                Text("Issuer: \(details.issuer ?? CredentialDisplayText.unknown)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                HStack(spacing: 8) {
                    Text(details.format)
                    if let validityText = details.cardSummary.validityText {
                        Text(validityText)
                    }
                }
                .font(.caption2)
                .foregroundColor(.secondary)
            }

            Spacer(minLength: 0)
        }
        .padding()
        .background(Color(.systemGray6))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

struct CredentialCardButton: View {
    let details: CredentialDetails
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            CredentialCardView(details: details)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier(WalletAccessibilityID.credentialCard(details.id))
    }
}

struct CredentialPortraitView: View {
    let summary: CredentialCardSummary
    let size: CGFloat

    var body: some View {
        Group {
            if let data = summary.portraitData, let image = UIImage(data: data) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFit()
            } else {
                Image(systemName: "person.text.rectangle")
                    .font(.title3)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(width: size, height: size)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 8))
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(Color(.separator), lineWidth: 1)
        )
        .accessibilityLabel("Credential portrait")
    }
}
