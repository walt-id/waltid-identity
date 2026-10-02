import SwiftUI

public enum SigningKeySetting: String, CaseIterable {
    case recovery, storage, approval
}

/// Summary rows shared by onboarding and Settings. Editing only changes a draft before creation.
public struct SigningKeySummary: View {
    private let recovery: String
    private let storage: String
    private let approval: String
    private let onEdit: ((SigningKeySetting) -> Void)?

    public init(recovery: String, storage: String, approval: String,
                onEdit: ((SigningKeySetting) -> Void)? = nil) {
        self.recovery = recovery
        self.storage = storage
        self.approval = approval
        self.onEdit = onEdit
    }

    public var body: some View {
        row(.recovery, String(localized: "Recovery", bundle: .module), recovery)
        row(.storage, String(localized: "Key storage", bundle: .module), storage)
        row(.approval, String(localized: "Signing approval", bundle: .module), approval)
    }

    @ViewBuilder
    private func row(_ setting: SigningKeySetting, _ title: String, _ value: String) -> some View {
        if let onEdit {
            Button { onEdit(setting) } label: {
                HStack(spacing: 12) {
                    label(title, value)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.forward").font(.footnote).foregroundStyle(.secondary).accessibilityHidden(true)
                }.frame(minHeight: 44).contentShape(Rectangle())
            }.buttonStyle(.plain).accessibilityIdentifier("wallet.keySetupEdit.\(setting.rawValue)")
        } else {
            label(title, value)
        }
    }

    private func label(_ title: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text(value).font(.body).foregroundStyle(.primary)
        }.fixedSize(horizontal: false, vertical: true)
    }
}
