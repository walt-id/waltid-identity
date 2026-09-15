import WalletSDK

/// Compiled examples checked against the public guides; not executed against radios.
@available(macOS 10.15, *)
@MainActor
private final class ProximityDocumentationSnippets {
    func session(wallet: Wallet) async throws {
        // doc-snippet:start swift-proximity-session
        let configuration = ProximityConfiguration()
        let capabilities = try await wallet.proximityPresentationCapabilities(
            configuration: configuration
        )
        showUnavailableMethods(capabilities)
        let session = try await wallet.startProximityPresentation(
            configuration: configuration
        )

        for await state in session.states {
            switch state {
            case .checkingPrerequisites(let current):
                showUnavailableMethods(current)
            case .engagementReady(let engagements):
                showEngagements(engagements)
            case .reviewRequired(let review, _):
                showReview(review)
            case .preparationRequired(let plan, let reason):
                showPreparationReview(plan, reason: reason)
            case .completed(let exchanges, _, _):
                showCompletion(exchanges: exchanges)
            case .noData(let exchange):
                showNoData(exchange: exchange)
            case .failed(let error):
                showFailure(error)
            default:
                showProgress(state)
            }
        }
        await session.close()
        // doc-snippet:end swift-proximity-session
    }

    func reconnect(
        wallet: Wallet, configuration: ProximityConfiguration,
        plan: ProximitySharingPlan, submission: ProximitySubmission
    ) async throws -> ProximitySession? {
        // doc-snippet:start swift-proximity-preparation
        switch try plan.approve(submission) {
        case .prepared(let sharing):
            return try await wallet.startProximityPresentation(
                configuration: configuration.withApproval(.prepared(sharing))
            )
        case .rejected(let error):
            showFailure(error)
            return nil
        }
        // doc-snippet:end swift-proximity-preparation
    }

    private func showUnavailableMethods(_ value: Any) {}
    private func showEngagements(_ value: Any) {}
    private func showReview(_ value: Any) {}
    private func showPreparationReview(_ plan: Any, reason: Any) {}
    private func showCompletion(exchanges: Any) {}
    private func showNoData(exchange: Any) {}
    private func showFailure(_ value: Any) {}
    private func showProgress(_ value: Any) {}
}
