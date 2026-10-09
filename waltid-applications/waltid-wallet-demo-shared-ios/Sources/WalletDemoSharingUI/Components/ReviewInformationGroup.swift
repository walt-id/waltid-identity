import SwiftUI

/// Selected-credential source and field rows shared by online and nearby reviews.
public struct ReviewInformationGroup<Content: View>: View {
    private let title: String
    private let issuer: String?
    private let content: Content

    public init(title: String, issuer: String? = nil,
                @ViewBuilder content: () -> Content) {
        self.title = title; self.issuer = issuer; self.content = content()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(.subheadline.weight(.semibold))
                if let issuer, !issuer.isEmpty { Text(issuer).font(.footnote).foregroundStyle(.secondary) }
            }.frame(maxWidth: .infinity, alignment: .leading)
            content
        }.padding(16)
    }
}
