import SwiftUI
import WalletSDK

public struct CredentialTechnicalInformation: View {
    @Environment(\.credentialStatusUI) private var statusUI
    public let details: CredentialDetails

    public init(details: CredentialDetails) { self.details = details }

    public var body: some View {
        if let status = statusUI.values[details.id] {
            WalletSection(String(localized: "Credential status", bundle: .module)) {
                ForEach(Array(status.status.entries.enumerated()), id: \.offset) { _, entry in
                    Text(String(describing: entry)).font(.caption).textSelection(.enabled).padding(12)
                }
            }
        }
        ForEach(details.groups.filter { $0.id == "technical" } + [details.systemInfoGroup].compactMap { $0 }) { group in
            ClaimGroupView(group: group, collapsible: false)
        }
    }
}
