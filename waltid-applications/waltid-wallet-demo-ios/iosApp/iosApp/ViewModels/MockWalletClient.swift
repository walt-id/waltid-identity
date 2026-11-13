import Foundation
#if DEBUG
@testable import WalletSDK
#else
import WalletSDK
#endif

actor MockWalletClient: WalletClient {
    enum VerifierStyle {
        case named
        case did
    }

    private var storedCredentials: [Credential]
    private var statusSnapshot: CredentialStatusSnapshot?
    private var statusCollectors: [UUID: AsyncThrowingStream<CredentialStatusSnapshot, Error>.Continuation] = [:]
    var statusCollectorCount: Int { statusCollectors.count }
    private(set) var statusRefreshCalls = 0
    private var statusRefreshResults: [CredentialStatusRefreshResult] = []
    private var statusRefreshFails = false
    private var initialCredentialRead: (@Sendable () async -> Void)?
    private let operationDelayNanoseconds: UInt64
    private let verifierStyle: VerifierStyle
    private let duplicatePresentationOptions: Bool
    private let emptyPresentationOptions: Bool
    private let transactionCodeRequired: Bool
    private let issuanceGrant: IssuanceGrant
    private let presentationPreviewResultOverride: PresentationPreviewResult?
    private let paymentConsent: @Sendable () async throws -> PaymentConsent?
    private let paymentTransaction: Bool
    private(set) var submittedPaymentConsentRevisions: [String?] = []
    private let rejectionResult: PresentationResult
    private let responseEncryptionRequired: Bool
    private let mdocMetadata: Bool
    private let sampleCredentialDataJSON: String?
    private let samplePortraitDisclosureValueJSON: String?
    private let deleteLocalDataDelayNanoseconds: UInt64
    private let deleteLocalDataError: Error?
    private(set) var rejectedPresentationPreviewHandles: [PresentationPreviewHandle] = []
    private(set) var discardedPresentationPreviewHandles: [PresentationPreviewHandle] = []

    init(
        storedCredentials: [Credential] = [],
        initialCredentialRead: (@Sendable () async -> Void)? = nil,
        operationDelayMilliseconds: UInt64 = 0,
        verifierStyle: VerifierStyle = .named,
        duplicatePresentationOptions: Bool = false,
        emptyPresentationOptions: Bool = false,
        transactionCodeRequired: Bool = false,
        issuanceGrant: IssuanceGrant = .preAuthorizedCode,
        presentationPreviewResult: PresentationPreviewResult? = nil,
        paymentConsent: @escaping @Sendable () async throws -> PaymentConsent? = { nil },
        paymentTransaction: Bool = true,
        rejectionResult: PresentationResult = .transmitted(.succeeded(verifierResponseJSON: "{}")),
        responseEncryptionRequired: Bool = true,
        mdocMetadata: Bool = false,
        sampleCredentialDataJSON: String? = nil,
        samplePortraitDisclosureValueJSON: String? = nil,
        deleteLocalDataDelayMilliseconds: UInt64 = 0,
        deleteLocalDataError: Error? = nil,
        credentialStatusSnapshot: CredentialStatusSnapshot? = nil
    ) {
        self.statusSnapshot = credentialStatusSnapshot
        self.storedCredentials = storedCredentials
        self.initialCredentialRead = initialCredentialRead
        self.operationDelayNanoseconds = operationDelayMilliseconds * 1_000_000
        self.verifierStyle = verifierStyle
        self.duplicatePresentationOptions = duplicatePresentationOptions
        self.emptyPresentationOptions = emptyPresentationOptions
        self.transactionCodeRequired = transactionCodeRequired
        self.issuanceGrant = issuanceGrant
        self.presentationPreviewResultOverride = presentationPreviewResult
        self.paymentConsent = paymentConsent
        self.paymentTransaction = paymentTransaction
        self.rejectionResult = rejectionResult
        self.responseEncryptionRequired = responseEncryptionRequired
        self.mdocMetadata = mdocMetadata
        self.sampleCredentialDataJSON = sampleCredentialDataJSON
        self.samplePortraitDisclosureValueJSON = samplePortraitDisclosureValueJSON
        self.deleteLocalDataDelayNanoseconds = deleteLocalDataDelayMilliseconds * 1_000_000
        self.deleteLocalDataError = deleteLocalDataError
    }

    #if DEBUG
    nonisolated static func uiTestCredentialStatusSnapshot(_ json: String?) -> CredentialStatusSnapshot? {
        guard let json else { return nil }
        return try? decodeCredentialStatusSnapshot(json)
    }

    nonisolated static func uiTestPaymentConsent(_ scenario: String?) async throws -> PaymentConsent? {
        guard let scenario, scenario != "none" else { return nil }
        if scenario == "blocked" { throw WalletError.internalFailure("Required issuer payment labels are missing.") }
        if scenario == "recurring" {
            let fields: [PaymentConsentField] = [
                .init(label: "Payee", description: nil, value: "Super Store", placement: .prominent, path: ["payee", "name"]),
                .init(label: "Payee ID", description: nil, value: "merchant-001", placement: .main, path: ["payee", "id"]),
                .init(label: "Payee website", description: nil, value: "https://shop.example", placement: .details, path: ["payee", "website"]),
                .init(label: "Payee logo", description: nil, value: "data:image/png;base64,AA==", placement: .details, path: ["payee", "logo"]),
                .init(label: "Amount", description: nil, value: "11.56", placement: .prominent, path: ["amount"]),
                .init(label: "Currency", description: nil, value: "EUR", placement: .prominent, path: ["currency"]),
                .init(label: "Payment frequency", description: nil, value: "Monthly", placement: .main, path: ["recurrence", "frequency"]),
                .init(label: "First payment", description: nil, value: "2099-01-01T12:00:00Z", placement: .main, path: ["recurrence", "start_date"]),
                .init(label: "Last payment", description: nil, value: "2100-01-01T12:00:00Z", placement: .main, path: ["recurrence", "end_date"]),
                .init(label: "Payment count", description: nil, value: "12", placement: .main, path: ["recurrence", "number"]),
                .init(label: "Maximum payment amount", description: nil, value: "9007199254740993.01", placement: .main, path: ["recurrence", "mit_options", "max_amount"]),
                .init(label: "Annual percentage rate", description: nil, value: "0.1234567890123456789", placement: .main, path: ["recurrence", "mit_options", "apr"]),
                .init(label: "PISP legal name", description: nil, value: "Example Payments Ltd", placement: .details, path: ["pisp", "legal_name"]),
                .init(label: "Transaction ID", description: nil, value: "recurring-ui-test", placement: .details, path: ["transaction_id"]),
            ]
            return PaymentConsent(revision: "ui-test-recurring", locale: "en", title: "Confirm payment", securityHint: nil,
                affirmativeAction: "Confirm payment", denialAction: "Cancel payment", requiresUnsignedRequestWarning: false, fields: fields)
        }
        let english = scenario == "english"
        return PaymentConsent(revision: "ui-test-payment", locale: english ? "en" : "de",
            title: english ? "Confirm this payment" : "Zahlung prüfen", securityHint: nil,
            affirmativeAction: english ? "Confirm payment" : "Zahlen",
            denialAction: english ? "Cancel payment" : "Ablehnen", requiresUnsignedRequestWarning: scenario == "unsigned",
            fields: [
                .init(label: english ? "Amount" : "Betrag", description: nil, value: "11.56", placement: .prominent, path: ["amount"]),
                .init(label: english ? "Currency" : "Währung", description: nil, value: "EUR", placement: .prominent, path: ["currency"]),
                .init(label: english ? "Payee" : "Empfänger", description: nil, value: "Super Store", placement: .prominent, path: ["payee", "name"]),
                .init(label: english ? "Payee ID" : "Empfänger-ID", description: nil, value: "merchant-001", placement: .main, path: ["payee", "id"]),
                .init(label: english ? "Transaction ID" : "Transaktions-ID", description: nil, value: "txn-1", placement: .details, path: ["transaction_id"]),
                .init(label: "Omitted", description: nil, value: "bound-but-hidden", placement: .omitted),
            ])
    }
    #endif

    private(set) var bootstrapCalls = 0

    func bootstrap(signingProtection: WalletDemoSigningProtection) async throws -> WalletDemoBootstrapResult {
        bootstrapCalls += 1
        return WalletDemoBootstrapResult(
            keyID: "mock-key-1",
            did: "did:key:mock",
            publicJWK: #"{"kty":"OKP","crv":"Ed25519","x":"test"}"#,
            keyUseAuthorizationPolicy: signingProtection.authorizationPolicy
        )
    }

    func signingProtectionAvailability(
        _ signingProtection: WalletDemoSigningProtection
    ) async throws -> WalletDemoSigningProtectionAvailability {
        .available
    }

    func credentials() async throws -> [Credential] {
        if let initialCredentialRead {
            self.initialCredentialRead = nil
            await initialCredentialRead()
        }
        try await delayOperation()
        return storedCredentials
    }

    func listDeferredIssuance() async throws -> [DeferredCredential] { [] }

    func startIssuance(_ request: IssuanceRequest) async throws -> IssuanceSession {
        try await delayOperation()
        return IssuanceSession(
            id: "mock-session",
            offer: .init(
                grant: issuanceGrant,
                issuer: .init(
                    identifier: "https://issuer.example",
                    name: "Example Issuer",
                    locale: nil,
                    logoURI: nil,
                    logoAltText: nil,
                    metadataProvenance: .unsigned
                ),
                credentials: [.init(configurationID: "ExampleCredential", format: "dc+sd-jwt", name: "Example", descriptionText: nil, logoURI: nil)],
                transactionCode: transactionCodeRequired
                    ? .init(inputMode: "numeric", length: 6, descriptionText: "Enter the six-digit code")
                    : nil
            ),
        )
    }

    func beginAuthorizationIssuance(sessionID: String, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceAuthorization {
        guard issuanceGrant == .authorizationCode else {
            throw WalletError.internalFailure("Mock issuance session is not authorization-code based")
        }
        return IssuanceAuthorization(
            url: URL(string: "https://issuer.example/authorize")!,
            state: "mock-state",
            redirectURI: URL(string: "openid://")!,
            pkce: .init(codeChallenge: "mock-challenge", codeChallengeMethod: "S256"),
            pushedAuthorizationRequestUsed: false
        )
    }

    func continuePreAuthorizedIssuance(sessionID: String, transactionCode: String?, credentials: [IssuanceCredentialSelection]?) async throws -> IssuanceOutcome {
        try await delayOperation()
        storedCredentials = [mdocMetadata ? Self.photoIDCredential : sampleCredential]
        return .stored(sessionID: sessionID, credentialIDs: storedCredentials.map(\.id))
    }

    func continueAuthorizationIssuance(sessionID: String, callbackURI: URL) async throws -> IssuanceOutcome {
        try await continuePreAuthorizedIssuance(sessionID: sessionID, transactionCode: nil, credentials: nil)
    }

    func cancelIssuance(sessionID: String) async throws -> IssuanceOutcome { .cancelled(sessionID: sessionID) }

    func resumeDeferredIssuance(deferredCredentialID: String) async throws -> IssuanceOutcome {
        .failed(
            sessionID: "mock-session",
            error: .init(code: .invalidSession, message: "No mock deferred credential"),
            storedCredentialIDs: [], deferredCredentials: []
        )
    }

    func previewPresentation(request: URL) async throws -> PresentationPreviewResult {
        try await delayOperation()
        if let presentationPreviewResultOverride {
            return presentationPreviewResultOverride
        }
        return .ready(
            PresentationPreview(
                previewHandle: PresentationPreviewHandle(value: "mock-presentation-preview"),
                request: previewRequestInfo,
                credentialOptions: emptyPresentationOptions ? [] : (duplicatePresentationOptions ? duplicateOptions : [defaultOption]),
                credentialRequirements: [
                    PresentationCredentialRequirement(options: [duplicatePresentationOptions ? ["identity", "age"] : ["pid"]])
                ]
            )
        )
    }

    func preparePaymentConsent(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?
    ) async throws -> PaymentConsent? { try await paymentConsent() }

    func submitPresentation(
        previewHandle: PresentationPreviewHandle,
        selectedCredentialOptions: [PresentationCredentialSelection],
        selectedDisclosureOptions: [PresentationDisclosureSelection],
        did: String?,
        paymentConsentRevision: String?
    ) async throws -> PresentationResult {
        submittedPaymentConsentRevisions.append(paymentConsentRevision)
        try await delayOperation()
        return .transmitted(.succeeded(verifierResponseJSON: "{}"))
    }

    func rejectPresentation(previewHandle: PresentationPreviewHandle) async throws -> PresentationResult {
        try await delayOperation()
        rejectedPresentationPreviewHandles.append(previewHandle)
        return rejectionResult
    }

    func discardPresentationPreview(_ previewHandle: PresentationPreviewHandle) async throws {
        discardedPresentationPreviewHandles.append(previewHandle)
    }

    func deleteCredential(id: String) async throws -> Bool {
        let remaining = storedCredentials.filter { $0.id != id }
        let removed = remaining.count != storedCredentials.count
        storedCredentials = remaining
        return removed
    }

    func deleteLocalData() async throws {
        if deleteLocalDataDelayNanoseconds > 0 {
            try await Task.sleep(nanoseconds: deleteLocalDataDelayNanoseconds)
        }
        if let deleteLocalDataError {
            throw deleteLocalDataError
        }
        storedCredentials = []
    }

    func credentialStatusSnapshot() async throws -> CredentialStatusSnapshot? { statusSnapshot }
    func credentialStatusUpdates() async throws -> AsyncThrowingStream<CredentialStatusSnapshot, Error> {
        let id = UUID()
        return AsyncThrowingStream(bufferingPolicy: .bufferingNewest(1)) { continuation in
            statusCollectors[id] = continuation
            if let statusSnapshot { continuation.yield(statusSnapshot) }
            continuation.onTermination = { [weak self] _ in Task { await self?.removeStatusCollector(id) } }
        }
    }
    private func removeStatusCollector(_ id: UUID) { statusCollectors.removeValue(forKey: id) }
    func setCredentialStatusSnapshot(_ snapshot: CredentialStatusSnapshot) {
        statusSnapshot = snapshot
        for continuation in statusCollectors.values { continuation.yield(snapshot) }
    }
    func failCredentialStatusObservation() {
        for continuation in statusCollectors.values {
            continuation.finish(throwing: NSError(domain: "MockWalletStatusRead", code: 1))
        }
    }
    func refreshCredentialStatuses(ids: [String]?, force: Bool) async throws -> [CredentialStatusRefreshResult] {
        statusRefreshCalls += 1
        try await delayOperation()
        if statusRefreshFails { throw NSError(domain: "MockWalletStatusTransport", code: 1) }
        return statusRefreshResults
    }
    func setStatusRefreshResults(_ results: [CredentialStatusRefreshResult], fails: Bool = false) {
        statusRefreshResults = results; statusRefreshFails = fails
    }

    private func delayOperation() async throws {
        guard operationDelayNanoseconds > 0 else { return }
        try await Task.sleep(nanoseconds: operationDelayNanoseconds)
    }

    private var previewRequestInfo: PresentationRequestInfo {
        PresentationRequestInfo(
            clientID: verifierClientID,
            verifierMetadata: verifierName.map {
                VerifierMetadata(
                    display: MetadataDisplay(
                        name: $0,
                        locale: "en",
                        logoURI: nil,
                        logoAltText: nil
                    ),
                    clientURI: "https://verifier.example",
                    policyURI: "https://verifier.example/privacy",
                    termsOfServiceURI: "https://verifier.example/terms"
                )
            },
            requestAuthentication: .unauthenticated,
            responseURI: URL(string: "https://verifier.example/response"),
            state: "state-123",
            nonce: "nonce-456",
            responseEncryption: responseEncryption,
            transactionData: paymentTransaction ? [Self.paymentAuthorizationTransactionData] : []
        )
    }

    private var verifierClientID: String {
        switch verifierStyle {
        case .named: return "https://verifier.example/client"
        case .did: return Self.didClientID
        }
    }

    private var responseEncryption: PresentationResponseEncryption {
        guard responseEncryptionRequired else { return .notRequired }
        return .required(
            ResponseEncryptionDetails(
                keyManagementAlgorithm: "ECDH-ES",
                contentEncryptionAlgorithm: "A256GCM",
                verifierKeyID: "verifier-key-1",
                verifierKeyThumbprint: "thumbprint-1"
            )
        )
    }

    private var verifierName: String? {
        switch verifierStyle {
        case .named: return "Example Verifier"
        case .did: return nil
        }
    }

    private static let didClientID = "decentralized_identifier:did:jwk:abc"

    private static let paymentAuthorizationTransactionData = PresentationTransactionData(
        type: "org.waltid.transaction-data.payment-authorization",
        displayName: "Payment Authorization",
        credentialQueryIDs: ["pid"],
        supportedFields: ["amount", "currency", "payee"],
        rawJSON: """
        {
          "type": "org.waltid.transaction-data.payment-authorization",
          "credential_ids": ["pid"],
          "transaction_data_hashes_alg": ["sha-256"],
          "amount": "129.90",
          "currency": "EUR",
          "payee": "Example Merchant"
        }
        """,
        detailsJSON: """
        {
          "amount": "129.90",
          "currency": "EUR",
          "payee": "Example Merchant"
        }
        """
    )

    private var defaultOption: PresentationCredentialOption {
        var disclosures = [
            PresentationDisclosure(
                path: "$.given_name",
                name: "given_name",
                valueJSON: "\"Ada\"",
                displayValue: "Ada",
                selectivelyDisclosable: true, required: true, selectable: false
            )
        ]
        if let samplePortraitDisclosureValueJSON {
            disclosures.append(PresentationDisclosure(
                path: "$.portrait",
                name: nil,
                valueJSON: samplePortraitDisclosureValueJSON,
                displayValue: nil,
                selectivelyDisclosable: true
            ))
        }
        return PresentationCredentialOption(
            queryID: "pid",
            credentialID: sampleCredential.id,
            format: sampleCredential.format,
            issuer: sampleCredential.issuer,
            subject: sampleCredential.subject,
            label: sampleCredential.label,
            credentialDataJSON: sampleCredential.credentialDataJSON,
            disclosures: disclosures
        )
    }

    private var duplicateOptions: [PresentationCredentialOption] {
        [
            defaultOption.with(queryID: "identity", disclosures: [
                PresentationDisclosure(
                    path: "$.given_name",
                    name: "Identity disclosure",
                    valueJSON: "\"Ada\"",
                    displayValue: "Ada",
                    selectivelyDisclosable: true, required: true, selectable: false
                )
            ]),
            defaultOption.with(queryID: "age", disclosures: [
                PresentationDisclosure(
                    path: "$.age_over_18",
                    name: "Age disclosure",
                    valueJSON: "\"Over 18\"",
                    displayValue: "Over 18",
                    selectivelyDisclosable: true, required: true, selectable: false
                )
            ])
        ]
    }

    private var sampleCredential: Credential {
        Credential(
            id: "cred-1",
            format: "jwt_vc_json",
            issuer: "Example Issuer",
            subject: nil,
            label: "Example Credential",
            addedAt: ISO8601DateFormatter().date(from: "2026-07-09T12:00:00Z"),
            credentialDataJSON: sampleCredentialDataJSON ?? Self.defaultSampleCredentialDataJSON
        )
    }

    private static let defaultSampleCredentialDataJSON = """
    {
      "vct": "https://issuer.example/credential-types/mobile-driving-licence",
      "given_name": "Ada",
      "age_over_18": true,
      "family_name": "Lovelace",
      "valid_to": 1781654400,
      "resident_address": {
        "street_address": "Main Street 1",
        "locality": "Vienna"
      }
    }
    """

    private static let photoIDCredential = Credential(
        id: "photo-id-1",
        format: "mso_mdoc",
        issuer: "Example Issuer",
        subject: nil,
        label: "Photo ID",
        addedAt: ISO8601DateFormatter().date(from: "2026-07-09T12:00:00Z"),
        credentialDataJSON: """
        {
          "org.iso.23220.1": {
            "given_name": "Erika",
            "family_name": "Mustermann",
            "age_over_18": true,
            "age_over_62": null,
            "age_over_65": false
          },
          "org.iso.23220.dtc.1": {
            "dtc_version": "1.0",
            "dtc_sod": [1, 2, 3],
            "dtc_dg1": [1, 2, 3, 4],
            "dtc_dg2": null
          }
        }
        """
    )
}

private extension PresentationCredentialOption {
    func with(queryID: String, disclosures: [PresentationDisclosure]) -> PresentationCredentialOption {
        PresentationCredentialOption(
            queryID: queryID,
            credentialID: credentialID,
            format: format,
            issuer: issuer,
            subject: subject,
            label: label,
            credentialDataJSON: credentialDataJSON,
            disclosures: disclosures
        )
    }
}
