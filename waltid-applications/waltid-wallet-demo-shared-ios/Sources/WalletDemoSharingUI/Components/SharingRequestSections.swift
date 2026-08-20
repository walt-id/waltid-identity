import SwiftUI

/// Renders request-only content in a failed sharing flow with the same island grammar as the
/// interactive review. Available request facts never get promoted into proof of Verifier identity.
public struct SharingRequestSections: View {
    private let request: SharingRequest

    public init(request: SharingRequest) {
        self.request = request
    }

    public var body: some View {
        ReviewIslandNavigationView(
            islands: SharingReviewModel(request: request, credentialOptions: [])
                .reviewIslands(context: .selectedForSharing)
        )
    }
}
