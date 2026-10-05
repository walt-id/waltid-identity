import SwiftUI

public struct WalletAction {
    public let label: String
    public let enabled: Bool
    public let identifier: String?
    public let perform: () -> Void

    public init(_ label: String, enabled: Bool = true, identifier: String? = nil, perform: @escaping () -> Void) {
        self.label = label
        self.enabled = enabled
        self.identifier = identifier
        self.perform = perform
    }
}

public struct WalletActionBar: View {
    private let primary: WalletAction
    private let secondary: WalletAction?

    public init(primary: WalletAction, secondary: WalletAction? = nil) {
        self.primary = primary
        self.secondary = secondary
    }

    public var body: some View {
        WalletFooter { WalletActions(primary: primary, secondary: secondary) }
    }
}
