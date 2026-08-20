import Foundation
import WalletSDK

/// Semantic island types shared by review surfaces and, later, stored credential details.
public enum ReviewIslandKind: String, Equatable {
    case issuer
    case verifier
    case credential
    case information
    case validityAndStatus
    case purposeAndTransaction
    case requiredAction
}

public extension CredentialDetails {
    /// Builds stored-credential details with the same information hierarchy as review surfaces.
    func reviewIslands(context: ReviewSurfaceContext = .stored) -> [ReviewIsland] {
        let summary = cardSummary
        let issuerName = issuerDisplay?.name?.presentableValue
            ?? issuer?.presentableValue
            ?? "Issuer unavailable"
        var islands = [
            ReviewIsland(
                id: "credential",
                kind: .credential,
                context: context,
                title: summary.title,
                subtitle: summary.credentialType ?? "Stored credential",
                visual: ReviewIslandVisual(
                    imageData: summary.portraitData,
                    contentDescription: summary.portraitData == nil ? nil : "Credential portrait",
                    fallbackText: summary.title.first.map { String($0).uppercased() } ?? "C"
                ),
                expandedValues: [ReviewValue(label: "Holder", value: summary.holderName)],
                technicalSections: [
                    ReviewTechnicalSection(
                        id: "credential-identity",
                        title: "Credential identity",
                        values: [
                            ReviewValue(label: "Credential identifier", value: id),
                            ReviewValue(label: "Format", value: format),
                            ReviewValue(label: "Subject", value: subject),
                        ]
                    )
                ],
                initiallyExpanded: true
            ),
            ReviewIsland(
                id: "issuer",
                kind: .issuer,
                context: context,
                title: issuerName,
                subtitle: "Credential Issuer",
                visual: ReviewIslandVisual(
                    imageURI: issuerDisplay?.logoURI,
                    contentDescription: issuerDisplay?.logoAltText,
                    fallbackText: issuerName.first.map { String($0).uppercased() } ?? "I"
                ),
                expandedValues: [ReviewValue(label: "About", value: issuerDisplay?.description)],
                technicalSections: [
                    ReviewTechnicalSection(
                        id: "issuer-identity",
                        title: "Issuer identity",
                        values: [
                            ReviewValue(label: "Issuer identifier", value: issuer, linkURI: issuer),
                            ReviewValue(label: "Selected display name", value: issuerDisplay?.name),
                            ReviewValue(label: "Logo source", value: issuerDisplay?.logoURI, linkURI: issuerDisplay?.logoURI),
                        ]
                    )
                ]
            ),
        ]

        if !groups.isEmpty {
            let fieldCount = groups.flatMap(\.items).count
            islands.append(
                ReviewIsland(
                    id: "information",
                    kind: .information,
                    context: context,
                    title: "Information",
                    subtitle: "\(fieldCount) \(fieldCount == 1 ? "field" : "fields")",
                    visual: ReviewIslandVisual(fallbackText: "i"),
                    expandedValues: groups.flatMap { group in
                        group.items.map {
                            ReviewValue(label: $0.label, value: $0.value.reviewText, supportingText: group.title)
                        }
                    },
                    technicalSections: groups.enumerated().map { index, group in
                        ReviewTechnicalSection(
                            id: "stored-information-\(index)",
                            title: group.title,
                            values: group.items.map {
                                ReviewValue(label: $0.path.id, value: $0.rawValue ?? $0.value.reviewText)
                            }
                        )
                    },
                    initiallyExpanded: true
                )
            )
        }

        if let validity = summary.validityText {
            islands.append(
                ReviewIsland(
                    id: "validity-and-status",
                    kind: .validityAndStatus,
                    context: context,
                    title: "Dates and status",
                    subtitle: validity,
                    visual: ReviewIslandVisual(fallbackText: "✓"),
                    expandedValues: [ReviewValue(label: "Available information", value: validity)],
                    technicalSections: [
                        ReviewTechnicalSection(
                            id: "stored-dates",
                            title: "Stored dates",
                            values: [ReviewValue(label: "Added to wallet", value: addedAt.map(Self.reviewDateFormatter.string(from:)))]
                        )
                    ]
                )
            )
        }

        return islands
    }

    private static let reviewDateFormatter: ISO8601DateFormatter = {
        let formatter = ISO8601DateFormatter()
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.formatOptions = [.withInternetDateTime]
        return formatter
    }()
}

/// Why an island is being rendered. Context changes copy and controls, not source data.
public enum ReviewSurfaceContext: Equatable {
    case offered
    case selectedForSharing
    case platformInvoked
    case stored
}

/// A stable visual which never becomes the source of an identity claim.
public struct ReviewIslandVisual: Equatable {
    public let imageURI: String?
    public let imageData: Data?
    public let contentDescription: String?
    public let fallbackText: String

    public init(imageURI: String? = nil, imageData: Data? = nil, contentDescription: String? = nil, fallbackText: String) {
        self.imageURI = imageURI
        self.imageData = imageData
        self.contentDescription = contentDescription
        self.fallbackText = fallbackText
    }
}

/// One labelled, display-safe value in an island.
public struct ReviewValue: Equatable {
    public let label: String
    public let value: String?
    public let supportingText: String?
    public let linkURI: String?

    public init(label: String, value: String?, supportingText: String? = nil, linkURI: String? = nil) {
        self.label = label
        self.value = value
        self.supportingText = supportingText
        self.linkURI = linkURI
    }

    public var isVisible: Bool {
        value?.presentableValue != nil
    }
}

/// A grouped section on an island-specific technical page.
public struct ReviewTechnicalSection: Equatable, Identifiable {
    public let id: String
    public let title: String
    public let values: [ReviewValue]

    public init(id: String, title: String, values: [ReviewValue]) {
        self.id = id
        self.title = title
        self.values = values
    }

    public var visibleValues: [ReviewValue] { values.filter(\.isVisible) }
}

/// Container-independent presentation contract for one expandable review island.
public struct ReviewIsland: Equatable, Identifiable {
    public let id: String
    public let kind: ReviewIslandKind
    public let context: ReviewSurfaceContext
    public let title: String
    public let subtitle: String?
    public let visual: ReviewIslandVisual?
    public let summaryValues: [ReviewValue]
    public let expandedValues: [ReviewValue]
    public let technicalSections: [ReviewTechnicalSection]
    /// Reserved for exact typed status facts; Candidate 1 deliberately invents no trust signal.
    public let status: ReviewValue?
    public let warning: String?
    public let initiallyExpanded: Bool

    public init(
        id: String,
        kind: ReviewIslandKind,
        context: ReviewSurfaceContext,
        title: String,
        subtitle: String? = nil,
        visual: ReviewIslandVisual? = nil,
        summaryValues: [ReviewValue] = [],
        expandedValues: [ReviewValue] = [],
        technicalSections: [ReviewTechnicalSection] = [],
        status: ReviewValue? = nil,
        warning: String? = nil,
        initiallyExpanded: Bool = false
    ) {
        self.id = id
        self.kind = kind
        self.context = context
        self.title = title
        self.subtitle = subtitle
        self.visual = visual
        self.summaryValues = summaryValues
        self.expandedValues = expandedValues
        self.technicalSections = technicalSections
        self.status = status
        self.warning = warning
        self.initiallyExpanded = initiallyExpanded
    }

    public var visibleSummaryValues: [ReviewValue] { summaryValues.filter(\.isVisible) }
    public var visibleExpandedValues: [ReviewValue] { expandedValues.filter(\.isVisible) }
    public var visibleTechnicalSections: [ReviewTechnicalSection] {
        technicalSections.compactMap { section in
            let values = section.visibleValues
            return values.isEmpty ? nil : ReviewTechnicalSection(id: section.id, title: section.title, values: values)
        }
    }
    public var hasTechnicalDetails: Bool { !visibleTechnicalSections.isEmpty }
}

/// Typed navigation local to the current review surface.
public enum ReviewRoute: Equatable {
    case summary
    case technicalDetails(islandID: String)
}

public extension SharingReviewModel {
    /// Maps a transport-neutral sharing review to the common island grammar.
    func reviewIslands(context: ReviewSurfaceContext = .selectedForSharing) -> [ReviewIsland] {
        var islands: [ReviewIsland] = []
        if let verifier = verifierIsland(context: context) { islands.append(verifier) }
        if let credential = credentialIsland(context: context) { islands.append(credential) }
        if let information = informationIsland(context: context) { islands.append(information) }
        islands.append(contentsOf: request.transactionData.map { transactionIsland($0, context: context) })
        return islands
    }

    private func verifierIsland(context: ReviewSurfaceContext) -> ReviewIsland? {
        let verifier = request.verifier
        let name = verifier?.identityName ?? "Verifier"
        let verifiedOrigin = verifier?.verifiedOrigin?.presentableValue
        let actorValues = verifier?.detailRows.map {
            ReviewValue(label: $0.label, value: $0.value, linkURI: $0.linkURI)
        } ?? []

        var technicalSections: [ReviewTechnicalSection] = []
        let requestValues = request.technicalDetails.map {
            ReviewValue(label: $0.label, value: $0.value, linkURI: $0.linkURI)
        }
        if requestValues.contains(where: \.isVisible) {
            technicalSections.append(
                ReviewTechnicalSection(id: "verifier-request", title: "Verifier request", values: requestValues)
            )
        }
        if let readerTrust = request.readerTrust {
            technicalSections.append(
                ReviewTechnicalSection(
                    id: "reader-authentication",
                    title: "Reader authentication",
                    values: readerTrust.reviewValues
                )
            )
        }
        technicalSections.append(
            ReviewTechnicalSection(
                id: "response-protection",
                title: "Response protection",
                values: request.responseProtection.reviewValues
            )
        )

        guard verifier?.hasContent == true || technicalSections.contains(where: { !$0.visibleValues.isEmpty }) else {
            return nil
        }
        return ReviewIsland(
            id: "verifier",
            kind: .verifier,
            context: context,
            title: name,
            subtitle: verifiedOrigin == name
                ? SharingVerifier.verifiedOriginCaption(for: verifiedOrigin)
                : "Verifier",
            visual: ReviewIslandVisual(
                imageURI: verifier?.display?.logoURI,
                contentDescription: verifier?.display?.logoAltText,
                fallbackText: name.first.map { String($0).uppercased() } ?? "V"
            ),
            summaryValues: [ReviewValue(label: "Response", value: request.responseProtection.summaryText)],
            expandedValues: actorValues,
            technicalSections: technicalSections,
            initiallyExpanded: verifiedOrigin != nil || request.readerTrust != nil || !(verifier?.details.isEmpty ?? true)
        )
    }

    private func credentialIsland(context: ReviewSurfaceContext) -> ReviewIsland? {
        guard let first = credentialOptions.first else { return nil }
        let title = credentialOptions.count == 1
            ? first.userFacingLabel
            : "\(credentialOptions.count) credentials"
        return ReviewIsland(
            id: "credential",
            kind: .credential,
            context: context,
            title: title,
            subtitle: credentialOptions.count == 1 ? "Selected credential" : "Choose credentials",
            visual: ReviewIslandVisual(fallbackText: title.first.map { String($0).uppercased() } ?? "C"),
            expandedValues: credentialOptions.map {
                ReviewValue(label: $0.userFacingLabel, value: $0.issuer ?? "Issuer unavailable", supportingText: $0.subject)
            },
            technicalSections: credentialOptions.enumerated().map { index, option in
                ReviewTechnicalSection(
                    id: "credential-option-\(index)",
                    title: option.userFacingLabel,
                    values: [
                        ReviewValue(label: "Credential identifier", value: option.credentialID),
                        ReviewValue(label: "Query identifier", value: option.queryID),
                        ReviewValue(label: "Format", value: option.format),
                        ReviewValue(label: "Issuer", value: option.issuer),
                        ReviewValue(label: "Subject", value: option.subject),
                    ]
                )
            },
            initiallyExpanded: true
        )
    }

    private func informationIsland(context: ReviewSurfaceContext) -> ReviewIsland? {
        let disclosures = credentialOptions.flatMap { option in option.disclosures.map { (option, $0) } }
        guard !disclosures.isEmpty else { return nil }
        let optionalCount = disclosures.filter { $0.1.selectable }.count
        var subtitle = "\(disclosures.count) \(disclosures.count == 1 ? "field" : "fields")"
        if optionalCount > 0 { subtitle += " · \(optionalCount) optional" }
        return ReviewIsland(
            id: "information",
            kind: .information,
            context: context,
            title: "Information to share",
            subtitle: subtitle,
            visual: ReviewIslandVisual(fallbackText: "i"),
            expandedValues: disclosures.map { option, disclosure in
                ReviewValue(
                    label: disclosure.name?.presentableValue ?? disclosure.path,
                    value: disclosure.displayValue ?? disclosure.valueJSON,
                    supportingText: "\(option.userFacingLabel) · \(disclosure.selectable ? "Optional" : "Required")"
                )
            },
            technicalSections: credentialOptions.enumerated().compactMap { index, option in
                let values = option.disclosures.flatMap { disclosure in
                    [
                        ReviewValue(label: disclosure.name?.presentableValue ?? "Claim path", value: disclosure.path),
                        ReviewValue(label: "Selection", value: disclosure.selectable ? "Optional" : "Required"),
                    ]
                }
                return values.isEmpty ? nil : ReviewTechnicalSection(
                    id: "requested-information-\(index)",
                    title: option.userFacingLabel,
                    values: values
                )
            },
            initiallyExpanded: true
        )
    }

    private func transactionIsland(_ group: ClaimGroup, context: ReviewSurfaceContext) -> ReviewIsland {
        ReviewIsland(
            id: "purpose-and-transaction-\(group.id)",
            kind: .purposeAndTransaction,
            context: context,
            title: group.title,
            subtitle: "Review before sharing",
            visual: ReviewIslandVisual(fallbackText: "!"),
            expandedValues: group.items.map {
                ReviewValue(label: $0.label, value: $0.value.reviewText, supportingText: group.title)
            },
            technicalSections: [
                ReviewTechnicalSection(
                    id: "transaction",
                    title: group.title,
                    values: group.items.map { ReviewValue(label: $0.path.id, value: $0.rawValue ?? $0.value.reviewText) }
                )
            ],
            initiallyExpanded: true
        )
    }
}

extension PresentationCredentialOption {
    var userFacingLabel: String {
        CredentialReviewDisplayNameResolver.resolve(
            label: label,
            format: format,
            credentialDataJSON: credentialDataJSON
        )
    }
}

private enum CredentialReviewDisplayNameResolver {
    private static let typeKeys = Set(["doctype", "docType", "vct"])

    static func resolve(label: String?, format: String, credentialDataJSON: String) -> String {
        if let label = label?.presentableValue {
            return label
        }
        if let type = credentialType(in: credentialDataJSON), let name = standardName(for: type) {
            return name
        }
        switch format.lowercased() {
        case "mso_mdoc": return "Mobile document"
        case "dc+sd-jwt", "vc+sd-jwt", "jwt_vc_json", "jwt_vc_json-ld": return "Digital credential"
        default: return "Credential"
        }
    }

    private static func credentialType(in credentialDataJSON: String) -> String? {
        guard let data = credentialDataJSON.data(using: .utf8),
              let root = try? JSONSerialization.jsonObject(with: data) else {
            return nil
        }
        return credentialType(in: root)
    }

    private static func credentialType(in value: Any) -> String? {
        if let object = value as? [String: Any] {
            for (key, child) in object where typeKeys.contains(key) {
                if let type = child as? String, type.presentableValue != nil { return type }
            }
            for child in object.values {
                if let type = credentialType(in: child) { return type }
            }
        } else if let values = value as? [Any] {
            for child in values {
                if let type = credentialType(in: child) { return type }
            }
        }
        return nil
    }

    private static func standardName(for type: String) -> String? {
        switch type.lowercased() {
        case "org.iso.18013.5.1.mdl": return "Mobile driving licence"
        case "org.iso.23220.photoid.1": return "Photo ID"
        case "eu.europa.ec.eudi.pid.1", "urn:eudi:pid:1", "urn:eu.europa.ec.eudi:pid:1": return "Personal ID"
        default: return nil
        }
    }
}

private extension SharingReaderTrust {
    var reviewValues: [ReviewValue] {
        switch self {
        case .notAuthenticated:
            return [ReviewValue(label: "Status", value: "Reader not authenticated")]
        case .pendingVerification:
            return [ReviewValue(label: "Status", value: "Verified before sharing")]
        case .untrusted(let reason):
            return [
                ReviewValue(label: "Status", value: "Reader identity not trusted by this wallet"),
                ReviewValue(label: "Reason", value: reason),
            ]
        case .trusted(let readerIdentity):
            return [
                ReviewValue(label: "Status", value: "Trusted reader"),
                ReviewValue(label: "Reader identity", value: readerIdentity),
            ]
        }
    }
}

private extension SharingResponseProtection {
    var summaryText: String {
        self == .none ? "No message-level encryption requested" : "Protected response"
    }

    var reviewValues: [ReviewValue] {
        switch self {
        case .none:
            return [ReviewValue(label: "Message-level encryption", value: "Not requested")]
        case let .encrypted(mechanism, keyManagement, contentEncryption, keyID, thumbprint):
            return [
                ReviewValue(label: "Message-level encryption", value: "Required"),
                ReviewValue(label: "Encryption mechanism", value: mechanism.displayName),
                ReviewValue(label: "Key management algorithm", value: keyManagement),
                ReviewValue(label: "Content encryption algorithm", value: contentEncryption),
                ReviewValue(label: "Verifier key ID", value: keyID),
                ReviewValue(label: "Verifier key thumbprint", value: thumbprint),
            ]
        }
    }
}

extension DisplayValue {
    var reviewText: String {
        switch self {
        case .text(let value), .number(let value), .decodedText(let value), .raw(let value): return value
        case .bool(let value): return value ? "Yes" : "No"
        case .object(let entries): return entries.map { "\($0.label): \($0.value.reviewText)" }.joined(separator: ", ")
        case .list(let values): return values.map(\.reviewText).joined(separator: ", ")
        case .image(_, _, let mimeType, _): return "\(mimeType) image"
        case .null: return "Not provided"
        }
    }
}
