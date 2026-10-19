import SwiftUI

/// One label/value hierarchy for stored values, requested disclosures and offer definitions.
public struct CredentialDataRow<Value: View>: View {
    private let label: String
    private let labelIdentifier: String?
    private let value: Value

    public init(_ label: String, labelIdentifier: String? = nil, @ViewBuilder value: () -> Value) {
        self.label = label
        self.labelIdentifier = labelIdentifier
        self.value = value()
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityIdentifier(labelIdentifier ?? "")
            value.frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
