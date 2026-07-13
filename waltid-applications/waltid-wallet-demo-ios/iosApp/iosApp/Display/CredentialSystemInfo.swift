import Foundation

extension CredentialDetails {
    var systemInfoGroup: ClaimGroup? {
        let items = [
            addedAt.map(Self.systemInfoDateFormatter.string(from:)).systemInfoItem(path: "system.added", label: "Added"),
            id.systemInfoItem(path: "system.id", label: "Credential ID"),
            format.systemInfoItem(path: "system.format", label: "Format"),
            issuer.systemInfoItem(path: "system.issuer", label: "Issuer"),
            subject.systemInfoItem(path: "system.subject", label: "Subject")
        ].compactMap { $0 }

        return items.isEmpty ? nil : ClaimGroup(title: "About this credential", items: items)
    }

    private static let systemInfoDateFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
}

private extension Optional where Wrapped == String {
    func systemInfoItem(path: String, label: String) -> ClaimItem? {
        self?.systemInfoItem(path: path, label: label)
    }
}

private extension String {
    func systemInfoItem(path: String, label: String) -> ClaimItem? {
        let value = trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return nil }

        return ClaimItem(
            path: ClaimItemPath.topLevel(path),
            label: label,
            value: .text(value),
            rawValue: value
        )
    }
}
