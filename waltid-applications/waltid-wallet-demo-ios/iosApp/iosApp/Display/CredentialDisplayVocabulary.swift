import Foundation

private struct ClaimDescriptor {
    let name: String
    let aliases: [String]
    let label: String?
    let group: ClaimGroupKind
    let roles: Set<ClaimRole>

    init(
        _ name: String,
        aliases: [String] = [],
        label: String? = nil,
        group: ClaimGroupKind = .other,
        roles: Set<ClaimRole> = []
    ) {
        self.name = name
        self.aliases = aliases
        self.label = label
        self.group = group
        self.roles = roles
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

    private static let givenNameClaim = "given_name"
    private static let familyNameClaim = "family_name"

    private static let descriptorsByName: [NormalizedClaimKey: ClaimDescriptor] = {
        let descriptors = [
            ClaimDescriptor(givenNameClaim, aliases: ["Given name"], label: "Given name", group: .personal, roles: [.givenName]),
            ClaimDescriptor(familyNameClaim, aliases: ["Family name"], label: "Family name", group: .personal, roles: [.familyName]),
            ClaimDescriptor("family_name_birth", label: "Family name at birth", group: .personal),
            ClaimDescriptor("birth_date", aliases: ["Date of birth", "birthdate"], label: "Date of birth", group: .personal),
            ClaimDescriptor("birth_place", aliases: ["Place of birth", "place_of_birth"], label: "Place of birth", group: .personal),
            ClaimDescriptor("age", group: .personal),
            ClaimDescriptor("age_over_18", label: "Age over 18", group: .personal),
            ClaimDescriptor("portrait", aliases: ["Portrait"], label: "Portrait", group: .personal, roles: [.image]),
            ClaimDescriptor("photo", roles: [.image]),
            ClaimDescriptor("picture", roles: [.image]),
            ClaimDescriptor("image", roles: [.image]),
            ClaimDescriptor("logo", roles: [.image]),
            ClaimDescriptor("nationality", label: "Nationality", group: .personal),
            ClaimDescriptor("nationalities", label: "Nationalities", group: .personal),
            ClaimDescriptor("resident_address", label: "Resident address", group: .address),
            ClaimDescriptor("resident_country", label: "Resident country", group: .address),
            ClaimDescriptor("resident_state", label: "Resident state", group: .address),
            ClaimDescriptor("resident_city", label: "Resident city", group: .address),
            ClaimDescriptor("resident_street", label: "Resident street", group: .address),
            ClaimDescriptor("resident_house_number", label: "Resident house number", group: .address),
            ClaimDescriptor("resident_postal_code", label: "Resident postal code", group: .address),
            ClaimDescriptor("street_address", label: "Street address", group: .address),
            ClaimDescriptor("locality", group: .address),
            ClaimDescriptor("region", group: .address),
            ClaimDescriptor("postal_code", label: "Postal code", group: .address),
            ClaimDescriptor("country", group: .address),
            ClaimDescriptor("document_number", label: "Document number"),
            ClaimDescriptor("personal_administrative_number", label: "Personal administrative number"),
            ClaimDescriptor("issuing_authority", label: "Issuing authority"),
            ClaimDescriptor("issuing_country", label: "Issuing country"),
            ClaimDescriptor("attestation_legal_category", label: "Attestation legal category"),
            ClaimDescriptor("iss", label: "Issuer", group: .technical),
            ClaimDescriptor("vct", label: "Credential type", group: .technical, roles: [.credentialType]),
            ClaimDescriptor("_sd", label: "Hidden claims", group: .technical),
            ClaimDescriptor("_sd_alg", label: "Selective disclosure algorithm", group: .technical),
            ClaimDescriptor("status", group: .technical),
            ClaimDescriptor("credential_type", aliases: ["Credential type", "credentialType"], roles: [.credentialType]),
            ClaimDescriptor("exp", label: "Expires", group: .technical, roles: [.temporal, .expiryDate]),
            ClaimDescriptor("expires", aliases: ["Expires"], roles: [.temporal, .expiryDate]),
            ClaimDescriptor("expiry", aliases: ["Expiry"], roles: [.temporal, .expiryDate]),
            ClaimDescriptor("expiry_date", aliases: ["Expiry date"], roles: [.temporal, .expiryDate]),
            ClaimDescriptor("expiration", aliases: ["Expiration"], roles: [.temporal, .expiryDate]),
            ClaimDescriptor("expiration_date", aliases: ["Expiration date"], roles: [.temporal, .expiryDate]),
            ClaimDescriptor("valid_until", aliases: ["Valid until"], roles: [.temporal, .expiryDate]),
            ClaimDescriptor("valid_to", aliases: ["Valid to"], roles: [.temporal, .expiryDate]),
            ClaimDescriptor("valid_from", aliases: ["Valid from"], roles: [.temporal]),
            ClaimDescriptor("nbf", label: "Valid from", group: .technical, roles: [.temporal]),
            ClaimDescriptor("not_before", roles: [.temporal]),
            ClaimDescriptor("iat", label: "Issued at", group: .technical, roles: [.temporal]),
            ClaimDescriptor("issued_at", roles: [.temporal]),
            ClaimDescriptor("issuance_date", roles: [.temporal]),
            ClaimDescriptor("cnf", label: "Confirmation key", group: .technical)
        ]
        return descriptors.reduce(into: [:]) { result, descriptor in
            for name in descriptor.recognizedNames {
                result[NormalizedClaimKey(name)] = descriptor
            }
        }
    }()

    private static let topLevelCredentialTypeClaimNames = Set(["type"].map(NormalizedClaimKey.init))

    static func groupKind(for components: [String]) -> ClaimGroupKind {
        descriptor(for: ClaimPath(components: components).topLevel)?.group ?? .other
    }

    static func humanizedLabel(_ key: String) -> String {
        descriptor(for: key)?.label ?? ClaimLabelFormatter.humanize(key)
    }

    static func roles(for components: [String]) -> Set<ClaimRole> {
        roles(for: ClaimPath(components: components))
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

    private static func descriptor(for name: String) -> ClaimDescriptor? {
        descriptorsByName[NormalizedClaimKey(name)]
    }
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
