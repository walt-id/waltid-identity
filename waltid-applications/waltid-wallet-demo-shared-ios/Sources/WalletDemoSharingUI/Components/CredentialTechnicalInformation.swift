import SwiftUI

public struct CredentialTechnicalInformation: View {
    public let details: CredentialDetails

    public init(details: CredentialDetails) { self.details = details }

    public var body: some View {
        ForEach(details.groups.filter { $0.id == "technical" } + [details.systemInfoGroup].compactMap { $0 }) { group in
            ClaimGroupView(group: group, collapsible: false)
        }
    }
}
