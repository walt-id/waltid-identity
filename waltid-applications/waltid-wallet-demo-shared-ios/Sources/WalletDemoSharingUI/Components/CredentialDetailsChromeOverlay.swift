import SwiftUI

public struct CredentialDetailsChromeOverlay: View {
    public var onClose: () -> Void
    public var onCopy: (() -> Void)?
    public var onDelete: (() -> Void)?

    public init(
        onClose: @escaping () -> Void,
        onCopy: (() -> Void)? = nil,
        onDelete: (() -> Void)? = nil
    ) {
        self.onClose = onClose
        self.onCopy = onCopy
        self.onDelete = onDelete
    }

    public var body: some View {
        HStack {
            chromeButton(systemName: "xmark", identifier: WalletAccessibilityID.detailsBack, action: onClose)
            Spacer()
            if onCopy != nil || onDelete != nil {
                Menu {
                    if let onCopy {
                        Button("Copy", action: onCopy)
                            .accessibilityIdentifier(WalletAccessibilityID.copyRawCredential)
                    }
                    if let onDelete {
                        Button("Delete", role: .destructive, action: onDelete)
                            .accessibilityIdentifier(WalletAccessibilityID.deleteCredential)
                    }
                } label: {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.primary)
                        .frame(width: 40, height: 40)
                        .background(.ultraThinMaterial, in: Circle())
                }
                .accessibilityIdentifier(WalletAccessibilityID.detailsMenu)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private func chromeButton(
        systemName: String,
        identifier: String,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.primary)
                .frame(width: 40, height: 40)
                .background(.ultraThinMaterial, in: Circle())
        }
        .accessibilityIdentifier(identifier)
    }
}
