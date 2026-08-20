import Foundation
import WalletSDK

/// Everything the user reviews before one presentation is shared, independent of the transport that
/// delivered the request.
///
/// This is deliberately a UI-layer model rather than a protocol abstraction. It carries no preview
/// handle, no request ID, no raw authorization request, no Annex C `DeviceRequest` and no platform
/// objects: the flow owner keeps those and submits with them. Without that boundary the review would
/// grow into a second protocol implementation, and every transport added later would have to
/// impersonate an OpenID4VP preview to be renderable.
public struct SharingReviewModel: Equatable {
    /// What is worth reviewing about who is asking and how the answer is protected.
    public let request: SharingRequest
    /// Wallet credentials that can satisfy the request.
    public let credentialOptions: [PresentationCredentialOption]
    /// Credential-query combinations that must be satisfied before Share is offered.
    public let credentialRequirements: [PresentationCredentialRequirement]

    /// Creates a sharing review.
    public init(
        request: SharingRequest,
        credentialOptions: [PresentationCredentialOption],
        credentialRequirements: [PresentationCredentialRequirement] = []
    ) {
        self.request = request
        self.credentialOptions = credentialOptions
        self.credentialRequirements = credentialRequirements
    }
}

/// Request metadata broken into concepts instead of protocol fields.
///
/// Each transport supplies only the concepts it actually has, and an absent concept stays `nil` or
/// empty so the UI can omit it. Filling an absent concept with a placeholder would claim the request
/// said something it never said - a fabricated `client_id` for an unsigned Digital Credentials
/// request, or an OpenID4VP `state` for an Annex C `DeviceRequest`.
public struct SharingRequest: Equatable {
    /// Who is asking, or `nil` when the transport proves nothing about the Verifier.
    public let verifier: SharingVerifier?
    /// Reader-authentication state, or `nil` when the protocol has no reader authentication.
    public let readerTrust: SharingReaderTrust?
    /// Protection applied to the response the wallet is about to produce.
    public let responseProtection: SharingResponseProtection
    /// Transactions this presentation will authorize, already display-grouped.
    public let transactionData: [ClaimGroup]
    /// Protocol-level values shown behind a disclosure for inspection.
    public let technicalDetails: [SharingDetail]

    /// Creates a sharing request description.
    public init(
        verifier: SharingVerifier?,
        readerTrust: SharingReaderTrust? = nil,
        responseProtection: SharingResponseProtection = .none,
        transactionData: [ClaimGroup] = [],
        technicalDetails: [SharingDetail] = []
    ) {
        self.verifier = verifier
        self.readerTrust = readerTrust
        self.responseProtection = responseProtection
        self.transactionData = transactionData
        self.technicalDetails = technicalDetails
    }

    /// Source-compatibility alias for callers that have not adopted Verifier terminology yet.
    @available(*, deprecated, renamed: "verifier")
    public var requester: SharingVerifier? { verifier }

    /// Source-compatible initializer retained while downstream fixtures adopt Verifier terminology.
    @available(*, deprecated, message: "Use init(verifier:readerTrust:responseProtection:transactionData:technicalDetails:)")
    public init(
        requester: SharingVerifier?,
        readerTrust: SharingReaderTrust? = nil,
        responseProtection: SharingResponseProtection = .none,
        transactionData: [ClaimGroup] = [],
        technicalDetails: [SharingDetail] = []
    ) {
        self.init(
            verifier: requester,
            readerTrust: readerTrust,
            responseProtection: responseProtection,
            transactionData: transactionData,
            technicalDetails: technicalDetails
        )
    }
}

/// The identity shown to the user for the party requesting the presentation.
///
/// ``verifiedOrigin`` is separate from ``display`` because they have different weight: display
/// metadata is self-asserted by the request, while a verified origin was authenticated by the
/// platform or by request signing. A wallet that renders them identically invites the user to trust
/// the wrong one.
public struct SharingVerifier: Equatable {
    /// Self-asserted Verifier display metadata, when the request carried any.
    public let display: MetadataDisplay?
    /// Name to show when ``display`` has none.
    public let fallbackName: String?
    /// Origin authenticated outside the request itself, when there is one.
    public let verifiedOrigin: String?
    /// Additional Verifier-published links, such as privacy policy or terms.
    public let details: [SharingDetail]

    /// Creates a Verifier identity.
    public init(
        display: MetadataDisplay? = nil,
        fallbackName: String? = nil,
        verifiedOrigin: String? = nil,
        details: [SharingDetail] = []
    ) {
        self.display = display
        self.fallbackName = fallbackName
        self.verifiedOrigin = verifiedOrigin
        self.details = details
    }

    /// Whether anything about the Verifier is worth rendering.
    public var hasContent: Bool {
        display?.name?.isPresentableValue == true ||
            fallbackName?.isPresentableValue == true ||
            verifiedOrigin?.isPresentableValue == true ||
            details.contains { $0.value?.isPresentableValue == true }
    }

    /// Names the one Verifier claim the transport authenticated, wherever it is shown.
    public static let verifiedOriginLabel = "Verified website"

    /// Describes the authenticated origin without treating a native app as a website.
    public static func verifiedOriginCaption(for origin: String?) -> String {
        guard let origin = origin?.presentableValue else { return "Verified origin" }
        if origin.hasPrefixIgnoringCase("https://") || origin.hasPrefixIgnoringCase("http://") {
            return verifiedOriginLabel
        }
        if origin.hasPrefixIgnoringCase("android:apk-key-hash:") {
            return "Verified app"
        }
        return "Verified origin"
    }

    /// The name the review heads the Verifier island with, or `nil` when the request named nobody.
    public var identityName: String? {
        display?.name?.presentableValue ?? fallbackName?.presentableValue
    }

    /// Whether ``verifiedOrigin`` is itself the name heading the section.
    ///
    /// True is what a request carrying no verifier metadata looks like: there is nothing self-asserted
    /// to head the section with, so the origin does.
    public var verifiedOriginIsIdentityName: Bool {
        guard let verifiedOrigin = verifiedOrigin?.presentableValue else { return false }
        return verifiedOrigin == identityName
    }

    /// Caption for ``identityName``, or `nil` when the heading needs none.
    ///
    /// The origin is the only Verifier claim that was authenticated rather than self-asserted, so it
    /// is always marked as one. When it is itself the heading it is captioned there, because an
    /// unlabelled origin reads as one more self-asserted Verifier claim.
    public var identityNameCaption: String? {
        verifiedOriginIsIdentityName ? Self.verifiedOriginCaption(for: verifiedOrigin) : nil
    }

    /// The labelled rows shown under ``identityName``.
    ///
    /// The origin leads them, but only when it is not already the heading: one string, one statement
    /// about it. Repeating it would read as two independent facts about the Verifier.
    public var detailRows: [SharingDetail] {
        let leadingOrigin = verifiedOriginIsIdentityName ? nil : verifiedOrigin?.presentableValue
        return [SharingDetail(label: Self.verifiedOriginCaption(for: leadingOrigin), value: leadingOrigin)] + details
    }
}

private extension String {
    func hasPrefixIgnoringCase(_ prefix: String) -> Bool {
        range(of: prefix, options: [.anchored, .caseInsensitive]) != nil
    }
}

/// Temporary source-compatibility alias while downstream fixtures adopt Verifier terminology.
@available(*, deprecated, renamed: "SharingVerifier")
public typealias SharingRequester = SharingVerifier

/// One labelled value in a Verifier or technical-details list.
public struct SharingDetail: Equatable {
    /// Row label.
    public let label: String
    /// Row value, or `nil` when the request did not carry it.
    public let value: String?
    /// URI the value links to, when it is a link.
    public let linkURI: String?

    /// Creates a labelled detail row.
    public init(label: String, value: String?, linkURI: String? = nil) {
        self.label = label
        self.value = value
        self.linkURI = linkURI
    }
}

/// Reader-authentication state as the user needs to understand it.
///
/// A protocol without reader authentication is represented by a `nil` reader trust rather than by a
/// state here, so no section is rendered at all: an OpenID4VP request has no reader to be trusted or
/// untrusted, and showing a reassuring or alarming reader row for it would be a statement about
/// something the request does not contain.
///
/// The states that do exist all describe a request the wallet is still willing to process. A request
/// whose reader authentication fails cryptographic verification is rejected before any preview, so
/// none of these means "bad signature".
public enum SharingReaderTrust: Equatable {
    /// The protocol supports reader authentication but the request carried none.
    case notAuthenticated
    /// Not checked yet: the raw request is withheld until the user consents, and the signature is
    /// verified before any credential data leaves the wallet.
    case pendingVerification
    /// The reader's signature verified, but no trust policy accepts the reader.
    case untrusted(reason: String)
    /// Reader authentication verified and a trust policy accepted the reader.
    case trusted(readerIdentity: String)
}

/// Protection applied to the response the wallet is about to produce.
public enum SharingResponseProtection: Equatable {
    /// The response is returned without message-level encryption.
    case none
    /// The response is encrypted to a key the request supplied.
    ///
    /// The algorithm values are optional because transports differ in what they publish before
    /// consent: OpenID4VP names the JWE algorithms in verifier metadata, while a Digital Credentials
    /// request states only its response mode.
    case encrypted(
        mechanism: SharingEncryptionMechanism,
        keyManagementAlgorithm: String? = nil,
        contentEncryptionAlgorithm: String? = nil,
        verifierKeyID: String? = nil,
        verifierKeyThumbprint: String? = nil
    )
}

/// Encryption schemes the demo's transports can apply to a response.
public enum SharingEncryptionMechanism: Equatable {
    /// OpenID4VP encrypted response returned over a verifier response URI.
    case jwe
    /// OpenID4VP Digital Credentials API `dc_api.jwt` response mode.
    case dcAPIJWT
    /// ISO 18013-7 Annex C HPKE session encryption.
    case annexCHPKE

    /// User-facing name of the mechanism.
    public var displayName: String {
        switch self {
        case .jwe: return "JWE encrypted response"
        case .dcAPIJWT: return "OpenID4VP encrypted response"
        case .annexCHPKE: return "ISO 18013-7 Annex C HPKE"
        }
    }
}

/// Credential and disclosure choices made in a sharing review.
///
/// Kept apart from the review itself so a transport can hold selection state in whatever lifecycle
/// it owns - an app view model, or a provider extension the operating system started - while the
/// selection *rules* stay in one place.
public struct SharingSelection: Equatable {
    /// Credentials the user chose to share.
    public var credentials: Set<PresentationCredentialSelection>
    /// Optional disclosures the user chose to include.
    public var disclosures: Set<PresentationDisclosureSelection>

    /// Creates a selection.
    public init(
        credentials: Set<PresentationCredentialSelection> = [],
        disclosures: Set<PresentationDisclosureSelection> = []
    ) {
        self.credentials = credentials
        self.disclosures = disclosures
    }
}

public extension SharingReviewModel {
    /// The selection a review opens with: one credential per query needed to satisfy the request.
    func defaultCredentialSelection() -> Set<PresentationCredentialSelection> {
        var firstSelectionByQueryID: [String: PresentationCredentialSelection] = [:]
        var orderedQueryIDs: [String] = []
        for option in credentialOptions where firstSelectionByQueryID[option.queryID] == nil {
            firstSelectionByQueryID[option.queryID] = option.selection
            orderedQueryIDs.append(option.queryID)
        }
        guard let firstQueryID = orderedQueryIDs.first else { return [] }
        if credentialRequirements.isEmpty {
            return Set([firstSelectionByQueryID[firstQueryID]].compactMap { $0 })
        }

        var selectedQueryIDs: [String] = []
        for requirement in credentialRequirements {
            let queryIDs = requirement.options.first { option in
                !option.isEmpty && option.allSatisfy { firstSelectionByQueryID[$0] != nil }
            } ?? requirement.options.first?.filter { firstSelectionByQueryID[$0] != nil }
            for queryID in queryIDs ?? [] where !selectedQueryIDs.contains(queryID) {
                selectedQueryIDs.append(queryID)
            }
        }
        return Set(selectedQueryIDs.compactMap { firstSelectionByQueryID[$0] })
    }

    /// Whether `selectedCredentialOptions` satisfies every credential requirement exactly once.
    func hasCompleteCredentialSelection(
        _ selectedCredentialOptions: Set<PresentationCredentialSelection>
    ) -> Bool {
        let optionBySelection = Dictionary(
            credentialOptions.map { ($0.selection, $0) },
            uniquingKeysWith: { first, _ in first }
        )
        let selectedOptions = selectedCredentialOptions.compactMap { optionBySelection[$0] }
        guard !selectedOptions.isEmpty else { return false }
        let selectedCountsByQueryID = Dictionary(grouping: selectedOptions, by: \.queryID).mapValues(\.count)
        guard !selectedOptions.contains(where: { option in
            (selectedCountsByQueryID[option.queryID] ?? 0) > 1 && !option.multiple
        }) else {
            return false
        }

        let selectedQueryIDs = Set(selectedOptions.map(\.queryID))
        if credentialRequirements.isEmpty { return true }
        return credentialRequirements.allSatisfy { requirement in
            requirement.options.contains { option in
                !option.isEmpty && option.allSatisfy { selectedQueryIDs.contains($0) }
            }
        }
    }

    /// Applies a credential toggle.
    ///
    /// Selecting a credential for a query that does not allow multiple matches replaces that query's
    /// previous choice, and drops the disclosures selected for the credential that is no longer
    /// chosen - otherwise a disclosure the user approved for one credential would silently travel
    /// with another.
    func toggling(
        credential selection: PresentationCredentialSelection,
        in current: SharingSelection
    ) -> SharingSelection {
        let option = credentialOptions.first { $0.selection == selection }
        var credentials = current.credentials
        if credentials.contains(selection) {
            credentials.remove(selection)
        } else {
            if option?.multiple != true {
                credentials = Set(credentials.filter { $0.queryID != selection.queryID })
            }
            credentials.insert(selection)
        }

        let retained: Set<PresentationDisclosureSelection>
        if option?.multiple == true {
            retained = Set(current.disclosures.filter {
                $0.queryID != selection.queryID || $0.credentialID != selection.credentialID
            })
        } else {
            retained = Set(current.disclosures.filter { $0.queryID != selection.queryID })
        }

        return SharingSelection(
            credentials: credentials,
            disclosures: retained.forSelectedCredentials(credentials)
        )
    }

    /// Applies a disclosure toggle, keeping only disclosures that belong to a selected credential.
    func toggling(
        disclosure selection: PresentationDisclosureSelection,
        in current: SharingSelection
    ) -> SharingSelection {
        var disclosures = current.disclosures
        if disclosures.contains(selection) {
            disclosures.remove(selection)
        } else {
            disclosures.insert(selection)
        }
        return SharingSelection(
            credentials: current.credentials,
            disclosures: disclosures.forSelectedCredentials(current.credentials)
        )
    }
}

public extension Set where Element == PresentationDisclosureSelection {
    /// Drops disclosures whose credential is no longer selected.
    func forSelectedCredentials(
        _ selectedCredentialOptions: Set<PresentationCredentialSelection>
    ) -> Set<PresentationDisclosureSelection> {
        filter { disclosure in
            selectedCredentialOptions.contains {
                $0.queryID == disclosure.queryID && $0.credentialID == disclosure.credentialID
            }
        }
    }
}

extension String {
    /// Whether a value is worth showing rather than an empty or whitespace-only placeholder.
    var isPresentableValue: Bool {
        !trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// The trimmed value, or `nil` when there is nothing to show.
    var presentableValue: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
