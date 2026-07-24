import Foundation

private struct ClaimDescriptor {
    let name: String
    let aliases: [String]
    let label: String?
    let group: ClaimGroupKind
    let roles: Set<ClaimRole>
    let displayOrder: Int

    init(
        _ name: String,
        aliases: [String] = [],
        label: String? = nil,
        group: ClaimGroupKind = .other,
        roles: Set<ClaimRole> = [],
        displayOrder: Int
    ) {
        self.name = name
        self.aliases = aliases
        self.label = label
        self.group = group
        self.roles = roles
        self.displayOrder = displayOrder
    }

    var recognizedNames: [String] {
        [name] + aliases
    }
}

private struct ClaimPath {
    let components: [String]

    var leaf: String {
        components.last ?? ""
    }

    var topLevel: String {
        components.first ?? ""
    }

    var isTopLevel: Bool {
        components.count <= 1
    }

    init(components: [String]) {
        self.components = components
    }
}

private struct NormalizedClaimKey: Hashable {
    let value: String

    init(_ rawValue: String) {
        self.value = rawValue
            .filter { $0.isLetter || $0.isNumber }
            .lowercased()
    }
}

enum CredentialDisplayVocabulary {
    static let genericVerifiableCredentialType = "VerifiableCredential"
    static let requestedDisclosuresTitle = "Requested disclosures"
    static let transactionDataTitle = "Transaction data"
    static let transactionDataTypeLabel = "Type"
    static let transactionDataCredentialQueriesLabel = "Credential queries"
    static let transactionDataDetailsLabel = "Details"
    static let transactionDataRequestDataLabel = "Request data"

    private static let givenNameClaim = "given_name"
    private static let familyNameClaim = "family_name"

    private static let descriptors = [
        ClaimDescriptor("docType", label: "Doc type", displayOrder: 0),
        ClaimDescriptor(givenNameClaim, aliases: ["Given name"], label: "Given name", group: .personal, roles: [.givenName], displayOrder: 10),
        ClaimDescriptor(familyNameClaim, aliases: ["Family name"], label: "Family name", group: .personal, roles: [.familyName], displayOrder: 20),
        ClaimDescriptor("family_name_birth", label: "Family name at birth", group: .personal, displayOrder: 30),
        ClaimDescriptor("birth_date", aliases: ["Date of birth", "birthdate"], label: "Date of birth", group: .personal, displayOrder: 40),
        ClaimDescriptor("birth_place", aliases: ["Place of birth", "place_of_birth"], label: "Place of birth", group: .personal, displayOrder: 50),
        ClaimDescriptor("locality", group: .address, displayOrder: 51),
        ClaimDescriptor("country", group: .address, displayOrder: 52),
        ClaimDescriptor("region", group: .address, displayOrder: 53),
        ClaimDescriptor("nationality", label: "Nationality", group: .personal, displayOrder: 60),
        ClaimDescriptor("nationalities", label: "Nationalities", group: .personal, displayOrder: 61),
        ClaimDescriptor("portrait", aliases: ["Portrait"], label: "Portrait", group: .personal, roles: [.image], displayOrder: 70),
        ClaimDescriptor("photo", roles: [.image], displayOrder: 71),
        ClaimDescriptor("picture", roles: [.image], displayOrder: 72),
        ClaimDescriptor("image", roles: [.image], displayOrder: 73),
        ClaimDescriptor("logo", roles: [.image], displayOrder: 74),
        ClaimDescriptor("age", group: .personal, displayOrder: 80),
        ClaimDescriptor("age_over_18", label: "Age over 18", group: .personal, displayOrder: 81),
        ClaimDescriptor("resident_address", label: "Resident address", group: .address, displayOrder: 100),
        ClaimDescriptor("resident_country", label: "Resident country", group: .address, displayOrder: 101),
        ClaimDescriptor("resident_state", label: "Resident state", group: .address, displayOrder: 102),
        ClaimDescriptor("resident_city", label: "Resident city", group: .address, displayOrder: 103),
        ClaimDescriptor("resident_street", label: "Resident street", group: .address, displayOrder: 104),
        ClaimDescriptor("resident_house_number", label: "Resident house number", group: .address, displayOrder: 105),
        ClaimDescriptor("resident_postal_code", label: "Resident postal code", group: .address, displayOrder: 106),
        ClaimDescriptor("street_address", label: "Street address", group: .address, displayOrder: 107),
        ClaimDescriptor("postal_code", label: "Postal code", group: .address, displayOrder: 108),
        ClaimDescriptor("document_number", label: "Document number", displayOrder: 120),
        ClaimDescriptor("personal_administrative_number", label: "Personal administrative number", displayOrder: 121),
        ClaimDescriptor("issuing_authority", label: "Issuing authority", displayOrder: 122),
        ClaimDescriptor("issuing_country", label: "Issuing country", displayOrder: 123),
        ClaimDescriptor("attestation_legal_category", label: "Attestation legal category", displayOrder: 124),
        ClaimDescriptor("issuer", label: "Issuer", displayOrder: 130),
        ClaimDescriptor("vct", label: "Credential type", group: .technical, roles: [.credentialType], displayOrder: 200),
        ClaimDescriptor("iss", label: "Issuer", group: .technical, displayOrder: 201),
        ClaimDescriptor("sub", label: "Subject", group: .technical, displayOrder: 202),
        ClaimDescriptor("iat", label: "Issued at", group: .technical, roles: [.temporal], displayOrder: 203),
        ClaimDescriptor("issued_at", roles: [.temporal], displayOrder: 204),
        ClaimDescriptor("issuance_date", roles: [.temporal], displayOrder: 205),
        ClaimDescriptor("valid_from", aliases: ["Valid from"], roles: [.temporal], displayOrder: 206),
        ClaimDescriptor("nbf", label: "Valid from", group: .technical, roles: [.temporal], displayOrder: 207),
        ClaimDescriptor("not_before", roles: [.temporal], displayOrder: 208),
        ClaimDescriptor("exp", label: "Expires", group: .technical, roles: [.temporal, .expiryDate], displayOrder: 209),
        ClaimDescriptor("expires", aliases: ["Expires"], roles: [.temporal, .expiryDate], displayOrder: 210),
        ClaimDescriptor("expiry", aliases: ["Expiry"], roles: [.temporal, .expiryDate], displayOrder: 211),
        ClaimDescriptor("expiry_date", aliases: ["Expiry date"], roles: [.temporal, .expiryDate], displayOrder: 212),
        ClaimDescriptor("expiration", aliases: ["Expiration"], roles: [.temporal, .expiryDate], displayOrder: 213),
        ClaimDescriptor("expiration_date", aliases: ["Expiration date"], roles: [.temporal, .expiryDate], displayOrder: 214),
        ClaimDescriptor("valid_until", aliases: ["Valid until"], roles: [.temporal, .expiryDate], displayOrder: 215),
        ClaimDescriptor("valid_to", aliases: ["Valid to"], roles: [.temporal, .expiryDate], displayOrder: 216),
        ClaimDescriptor("status", label: "Credential status", group: .technical, displayOrder: 220),
        ClaimDescriptor("credential_status", aliases: ["credentialStatus"], label: "Credential status", displayOrder: 221),
        ClaimDescriptor("_sd", label: "Undisclosed claims", group: .technical, displayOrder: 230),
        ClaimDescriptor("_sd_alg", label: "Selective disclosure algorithm", group: .technical, displayOrder: 231),
        ClaimDescriptor("cnf", label: "Confirmation key", group: .technical, displayOrder: 240),
        ClaimDescriptor("credential_type", aliases: ["Credential type", "credentialType"], roles: [.credentialType], displayOrder: 250)
    ]

    private static let descriptorsByName: [NormalizedClaimKey: ClaimDescriptor] = {
        return descriptors.reduce(into: [:]) { result, descriptor in
            for name in descriptor.recognizedNames {
                result[NormalizedClaimKey(name)] = descriptor
            }
        }
    }()
    private static let displayOrderByClaimName: [NormalizedClaimKey: Int] = {
        descriptors.reduce(into: [:]) { result, descriptor in
            for name in descriptor.recognizedNames {
                result[NormalizedClaimKey(name)] = descriptor.displayOrder
            }
        }
    }()

    private static let topLevelCredentialTypeClaimNames = Set(["type"].map(NormalizedClaimKey.init))
    private static let credentialSubjectContainerNames = Set(["credentialSubject", "credential_subject"].map(NormalizedClaimKey.init))
    private static let w3cMetadataClaimNames = Set([
        "@context",
        "credentialSchema",
        "credential_status",
        "credentialStatus",
        "evidence",
        "expirationDate",
        "id",
        "issuanceDate",
        "issuer",
        "proof",
        "refreshService",
        "termsOfUse",
        "type",
        "validFrom",
        "validUntil"
    ].map(NormalizedClaimKey.init))
    private static let technicalContainerNames = Set([
        "@context",
        "credentialSchema",
        "credential_status",
        "credentialStatus",
        "evidence",
        "proof",
        "refreshService",
        "termsOfUse"
    ].map(NormalizedClaimKey.init))

    static func groupKind(for components: [String], format: String? = nil) -> ClaimGroupKind {
        let path = ClaimPath(components: components)
        if let semantics = MdocClaimDisplaySemantics.describe(format: format, path: components) {
            switch semantics.group {
            case .ageAttestations: return .ageAttestations
            case .travelDocumentData: return .travelDocumentData
            }
        }
        if isW3CMetadataClaimPath(path) {
            return .technical
        }

        if let descriptor = descriptor(for: path.topLevel) {
            return descriptor.group
        }

        if isCredentialSubjectWrapped(path) {
            return descriptor(for: path.leaf)?.group ?? .other
        }

        if hasTechnicalContainer(path) {
            return .technical
        }

        return .other
    }

    static func humanizedLabel(_ key: String) -> String {
        descriptor(for: key)?.label ?? ClaimLabelFormatter.humanize(key)
    }

    static func disclosureLabel(name: String?, path: String) -> String {
        if let name = name?.trimmingCharacters(in: .whitespacesAndNewlines), !name.isEmpty {
            return humanizedLabel(name)
        }
        if let leafKey = ClaimPathExpression.parse(path).leafKey {
            return humanizedLabel(leafKey)
        }
        return path
    }

    static func transactionDataLabel(_ field: DisplayTransactionDataField) -> String {
        switch field {
        case .type: return transactionDataTypeLabel
        case .credentialQueryIDs: return transactionDataCredentialQueriesLabel
        case .details: return transactionDataDetailsLabel
        case .raw: return transactionDataRequestDataLabel
        }
    }

    static func roles(for components: [String]) -> Set<ClaimRole> {
        roles(for: ClaimPath(components: components))
    }

    static func claimPathCompare(_ lhs: [String], _ rhs: [String], format: String? = nil) -> ComparisonResult {
        let lhsSemanticOrder = MdocClaimDisplaySemantics.sortOrder(format: format, path: lhs)
        let rhsSemanticOrder = MdocClaimDisplaySemantics.sortOrder(format: format, path: rhs)
        if lhsSemanticOrder < rhsSemanticOrder { return .orderedAscending }
        if lhsSemanticOrder > rhsSemanticOrder { return .orderedDescending }

        let lhsSegments = displayOrderSegments(for: lhs)
        let rhsSegments = displayOrderSegments(for: rhs)
        for index in 0..<min(lhsSegments.count, rhsSegments.count) {
            if lhsSegments[index] < rhsSegments[index] { return .orderedAscending }
            if lhsSegments[index] > rhsSegments[index] { return .orderedDescending }
        }
        if lhsSegments.count < rhsSegments.count { return .orderedAscending }
        if lhsSegments.count > rhsSegments.count { return .orderedDescending }

        let lhsPath = lhs.joined(separator: ".")
        let rhsPath = rhs.joined(separator: ".")
        let caseInsensitiveComparison = lhsPath.compare(rhsPath, options: [.caseInsensitive])
        if caseInsensitiveComparison != .orderedSame {
            return caseInsensitiveComparison
        }
        return lhsPath.compare(rhsPath)
    }

    private static func roles(for claimPath: ClaimPath) -> Set<ClaimRole> {
        let leaf = claimPath.leaf
        var roles = descriptor(for: leaf)?.roles ?? []
        if isCredentialTypeClaimPath(claimPath) {
            roles.insert(.credentialType)
        }
        if pathHasRole(claimPath, role: .image) {
            roles.insert(.image)
        }
        return roles
    }

    static func isGenericCredentialType(_ value: String) -> Bool {
        CredentialTypeIdentifier.token(value)?.compare(genericVerifiableCredentialType, options: .caseInsensitive) == .orderedSame
    }

    static func readableCredentialType(_ value: String) -> String? {
        guard let token = CredentialTypeIdentifier.token(value),
            !isGenericCredentialType(token) else {
            return nil
        }

        let label = ClaimLabelFormatter.humanize(String(token))
        return label.isEmpty ? nil : label
    }

    private static func pathHasRole(_ path: ClaimPath, role: ClaimRole) -> Bool {
        path.components.contains { component in
            descriptor(for: component)?.roles.contains(role) == true
        }
    }

    private static func isCredentialTypeClaimPath(_ path: ClaimPath) -> Bool {
        guard topLevelCredentialTypeClaimNames.contains(NormalizedClaimKey(path.leaf)) else {
            return false
        }
        return path.isTopLevel || path.components == ["vc", path.leaf]
    }

    private static func isCredentialSubjectWrapped(_ path: ClaimPath) -> Bool {
        if credentialSubjectContainerNames.contains(NormalizedClaimKey(path.topLevel)) {
            return true
        }

        guard path.topLevel == "vc", path.components.count > 1 else {
            return false
        }
        return credentialSubjectContainerNames.contains(NormalizedClaimKey(path.components[1]))
    }

    private static func isW3CMetadataClaimPath(_ path: ClaimPath) -> Bool {
        if path.isTopLevel, w3cMetadataClaimNames.contains(NormalizedClaimKey(path.topLevel)) {
            return true
        }

        guard path.topLevel == "vc", path.components.count > 1 else {
            return false
        }
        return w3cMetadataClaimNames.contains(NormalizedClaimKey(path.components[1]))
    }

    private static func hasTechnicalContainer(_ path: ClaimPath) -> Bool {
        if technicalContainerNames.contains(NormalizedClaimKey(path.topLevel)) {
            return true
        }

        guard path.topLevel == "vc", path.components.count > 1 else {
            return false
        }
        return technicalContainerNames.contains(NormalizedClaimKey(path.components[1]))
    }

    private static func descriptor(for name: String) -> ClaimDescriptor? {
        descriptorsByName[NormalizedClaimKey(name)]
    }

    private static func displayOrder(for name: String) -> Int? {
        displayOrderByClaimName[NormalizedClaimKey(name)]
    }

    private static func displayOrderSegments(for components: [String]) -> [Int] {
        let segments = components.compactMap(displayOrder(for:))
        return segments.isEmpty ? [unknownClaimDisplayOrder] : segments
    }

    private static let unknownClaimDisplayOrder = 10_000
}

enum MdocClaimDisplayGroup: String, Hashable {
    case ageAttestations = "Age attestations"
    case travelDocumentData = "Travel document data"

    var order: Int {
        switch self {
        case .ageAttestations: return 1
        case .travelDocumentData: return 2
        }
    }
}

enum MdocClaimValueKind {
    case bool
    case binary
    case other
}

struct MdocClaimDisplaySemantics {
    let group: MdocClaimDisplayGroup
    let label: String
    let sortOrder: Int
    let valueKind: MdocClaimValueKind

    static func describe(format: String?, path: [String]) -> MdocClaimDisplaySemantics? {
        guard format == mdocFormat else { return nil }

        if path.contains(where: ageAttestationNamespaces.contains),
           let age = path.reversed().compactMap(ageThreshold).first {
            return MdocClaimDisplaySemantics(
                group: .ageAttestations,
                label: "\(age) or older",
                sortOrder: age,
                valueKind: .bool
            )
        }

        guard path.contains(dataGroupNamespace) else { return nil }
        guard let element = path.reversed().first(where: isTravelDocumentElement) else { return nil }
        if element == "version" || element == "dtc_version" {
            return MdocClaimDisplaySemantics(
                group: .travelDocumentData,
                label: "Specification version",
                sortOrder: 0,
                valueKind: .other
            )
        }
        if element == "sod" || element == "dtc_sod" {
            return MdocClaimDisplaySemantics(
                group: .travelDocumentData,
                label: "Document security object (SOD)",
                sortOrder: 1,
                valueKind: .binary
            )
        }
        if element == "dg_content_info" {
            return MdocClaimDisplaySemantics(
                group: .travelDocumentData,
                label: "Document content information",
                sortOrder: 18,
                valueKind: .binary
            )
        }

        guard let number = dataGroupNumber(element) else { return nil }
        return MdocClaimDisplaySemantics(
            group: .travelDocumentData,
            label: "DG\(number): \(dataGroupNames[number] ?? "Data group")",
            sortOrder: number + 1,
            valueKind: .binary
        )
    }

    static func sortOrder(format: String?, path: [String]) -> Int {
        describe(format: format, path: path)?.sortOrder ?? .max
    }

    private static func ageThreshold(_ component: String) -> Int? {
        let prefix = "age_over_"
        guard component.hasPrefix(prefix) else { return nil }
        let value = component.dropFirst(prefix.count)
        guard value.count == 2, value.allSatisfy(\.isNumber), let age = Int(value), (1...99).contains(age) else {
            return nil
        }
        return age
    }

    private static func isTravelDocumentElement(_ component: String) -> Bool {
        component == "version" ||
            component == "dtc_version" ||
            component == "sod" ||
            component == "dtc_sod" ||
            component == "dg_content_info" ||
            dataGroupNumber(component) != nil
    }

    private static func dataGroupNumber(_ component: String) -> Int? {
        let value: Substring
        if component.hasPrefix("dtc_dg") {
            value = component.dropFirst("dtc_dg".count)
        } else if component.hasPrefix("dg") {
            value = component.dropFirst(2)
        } else {
            return nil
        }
        guard let number = Int(value), value == String(number), (1...16).contains(number) else { return nil }
        return number
    }

    private static let mdocFormat = "mso_mdoc"
    private static let ageAttestationNamespaces = Set(["org.iso.18013.5.1", "org.iso.23220.1"])
    private static let dataGroupNamespace = "org.iso.23220.dtc.1"
    private static let dataGroupNames = [
        1: "Machine-readable zone",
        2: "Facial image",
        3: "Fingerprints",
        4: "Iris images",
        5: "Displayed portrait",
        6: "Reserved",
        7: "Signature or usual mark",
        8: "Data features",
        9: "Structure features",
        10: "Substance features",
        11: "Additional personal details",
        12: "Additional document details",
        13: "Optional details",
        14: "Security options",
        15: "Active authentication public key",
        16: "Persons to notify"
    ]
}

private enum ClaimLabelFormatter {
    private static let camelCaseBoundaryPattern = #"([a-z])([A-Z])"#
    private static let wordSeparators = CharacterSet(charactersIn: "_-. ")

    static func humanize(_ key: String) -> String {
        let words = key
            .replacingOccurrences(of: camelCaseBoundaryPattern, with: "$1 $2", options: .regularExpression)
            .components(separatedBy: wordSeparators)
            .filter { !$0.isEmpty }
            .map { $0.lowercased() }
            .joined(separator: " ")
        return words.prefix(1).uppercased() + words.dropFirst()
    }
}
