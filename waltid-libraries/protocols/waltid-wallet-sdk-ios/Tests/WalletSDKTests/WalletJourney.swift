import Foundation
import WalletSDK

// Implement these callbacks in the app. Render on the main actor and freeze each consent selection.
@MainActor private protocol JourneyScreen {
    func progress(_ event: WalletEvent)
    func setup(_ result: SigningIdentityOperationResult)
    func accept(_ offer: IssuanceOfferPreview) async -> Bool
    func code(_ requirement: IssuanceTransactionCode) async -> String
    func saveAuthorization(_ id: IssuanceAuthorizationID) async throws
    func browser(_ url: URL) async throws -> URL? // Dismissal returns nil.
    func received(_ outcome: IssuanceOutcome)
    func stored(_ credentials: [Credential])
    func choose(_ preview: PresentationPreview) async -> PresentationSelection
    func confirm(_ claims: ReviewedPresentationClaims, _ warnings: [CredentialStatusRecord], _ payment: PaymentConsent?) async -> Bool
    func deliver(_ result: PresentationResult)
    func reviewChanged()
}

@MainActor private func walletJourney(offer: URL, request: URL, registrationFor: (String) throws -> IssuanceClientRegistration,
                                     trust: WalletClientIDTrustConfiguration, ui: any JourneyScreen) async throws {
    let config = WalletConfiguration(walletID: "member", presentation: .init(verifierTrust: trust))
    func useWallet(_ block: (Wallet) async throws -> Void) async throws {
        let wallet = try await Wallet(configuration: config)
        let observer = Task {
            for await event in await wallet.events { ui.progress(event) }
        }
        do {
            try await block(wallet)
        } catch {
            observer.cancel()
            await observer.value
            // Cleanup must still run if the app task was cancelled.
            try? await Task { try await wallet.close() }.value
            throw error
        }
        observer.cancel()
        await observer.value
        try await Task { try await wallet.close() }.value
    }

    try await useWallet { wallet in
        let identity = try await wallet.signingIdentity.initialize()
        guard case .active = identity else {
            ui.setup(identity)
            return
        }
        let session = try await wallet.startIssuance(.init(offer: offer))
        let outcome: IssuanceOutcome
        if await ui.accept(session.offer) {
            switch session.grant {
            case .preAuthorized(let grant):
                var code: String?
                if let requirement = grant.transactionCode { code = await ui.code(requirement) }
                let clientID = grant.clientIdentification == .clientIDRequired ? try registrationFor(grant.authorizationServer).clientID : nil
                outcome = try await grant.accept(transactionCode: code, clientID: clientID)
            case .authorizationCode(let grant):
                let authorization = try await grant.beginAuthorization(using: registrationFor(grant.authorizationServer))
                try await ui.saveAuthorization(authorization.id)
                if let redirected = try await ui.browser(authorization.request.url) {
                    outcome = try await authorization.complete(redirected)
                } else {
                    outcome = try await wallet.cancelIssuance(sessionID: session.id)
                }
            }
        } else {
            outcome = try await wallet.cancelIssuance(sessionID: session.id)
        }
        ui.received(outcome)
    }

    try await useWallet { wallet in
        ui.stored(try await wallet.credentials())
        switch try await wallet.previewPresentation(request: request) {
        case .invalid(let error):
            try await wallet.discardPresentationPreview(error.previewHandle)
        case .ready(let preview):
            let handle = preview.previewHandle
            do {
                let selection = await ui.choose(preview)
                let review = try await wallet.reviewPresentation(previewHandle: handle, selection: selection)
                let result: PresentationResult
                if await ui.confirm(review.claims, review.statuses, review.payment) {
                    result = try await wallet.submitPresentation(review)
                } else {
                    result = try await wallet.rejectPresentation(previewHandle: handle)
                }
                ui.deliver(result)
            } catch WalletError.credentialStatusReviewChanged, WalletError.presentationReviewChanged {
                try? await wallet.discardPresentationPreview(handle)
                ui.reviewChanged()
            } catch {
                try? await Task { try await wallet.discardPresentationPreview(handle) }.value
                throw error
            }
        }
    }
}

// Called by the application's redirect handler after opening the same wallet.
private func completeSavedAuthorization(wallet: Wallet, savedID: String, callback: URL) async throws -> IssuanceOutcome {
    guard let id = IssuanceAuthorizationID(rawValue: savedID) else {
        throw WalletError.invalidInput("Invalid saved authorization ID")
    }
    return try await wallet.completeAuthorization(id, callback: callback)
}
