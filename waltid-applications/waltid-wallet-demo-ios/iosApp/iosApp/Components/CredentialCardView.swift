import SwiftUI
import shared

struct CredentialCardView: View {
    let credential: BridgeCredential

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(credential.label)
                .font(.headline)
                .lineLimit(1)
            HStack {
                Text(credential.issuer)
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                Spacer()
                if !credential.addedAt.isEmpty {
                    Text(String(credential.addedAt.prefix(10)))
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
