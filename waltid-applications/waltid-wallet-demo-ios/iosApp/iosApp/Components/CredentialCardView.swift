import SwiftUI
import WaltidWalletSDK

struct CredentialCardView: View {
    let credential: Credential

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(credential.label ?? credential.format)
                .font(.headline)
                .lineLimit(1)
            HStack {
                Text(credential.issuer ?? "Unknown")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                Spacer()
                if let addedAt = credential.addedAt {
                    Text(addedAt.formatted(date: .abbreviated, time: .omitted))
                        .font(.caption2)
                        .foregroundColor(.secondary)
                }
            }
            Text("Format: \(credential.format)")
                .font(.caption2)
                .foregroundColor(.gray)
        }
        .padding()
        .background(Color(.systemGray6))
        .cornerRadius(12)
    }
}
