package id.walt.walletdemo.compose.logic

enum class WalletInteractionKind {
    Receive,
    Present,
}

enum class WalletCaptureMode {
    Scanner,
    Manual,
}

enum class WalletRequestSource {
    Qr,
    Manual,
    DeepLink,
}

data class WalletIncomingRequest(
    val kind: WalletInteractionKind,
    val value: String,
    val source: WalletRequestSource,
)

enum class WalletInteractionSuccessOutcome {
    CredentialAdded,
    InformationShared,
    OfferDeclined,
    PresentationRejected,
}

/**
 * Demo-only interaction coordinator state.
 *
 * Preview handles remain in the typed preview models; this state describes presentation and
 * ownership of the current decision without introducing UI concepts into the wallet SDK.
 */
sealed interface WalletInteractionState {
    data object Idle : WalletInteractionState
    data object SelectingInteraction : WalletInteractionState

    data class Capturing(
        val kind: WalletInteractionKind,
        val mode: WalletCaptureMode = WalletCaptureMode.Scanner,
        val error: String? = null,
    ) : WalletInteractionState

    data class Validating(
        val request: WalletIncomingRequest,
    ) : WalletInteractionState

    data class WrongRequestType(
        val expected: WalletInteractionKind,
        val detected: WalletIncomingRequest,
    ) : WalletInteractionState

    data class Resolving(
        val request: WalletIncomingRequest,
    ) : WalletInteractionState

    data class ReviewingOffer(
        val previewHandle: WalletDemoIssuancePreviewHandle,
    ) : WalletInteractionState

    data class ReviewingPresentation(
        val previewHandle: WalletDemoPresentationPreviewHandle,
    ) : WalletInteractionState

    data class InvalidReview(
        val kind: WalletInteractionKind,
        val message: String,
        val verifierCanBeNotified: Boolean,
    ) : WalletInteractionState

    data class ExternalAuthorization(val message: String) : WalletInteractionState
    data class HandlingAuthorizationCallback(val message: String) : WalletInteractionState
    data class DeferredIssuance(val message: String) : WalletInteractionState
    data class ProtectedKeyAuthorization(val message: String) : WalletInteractionState

    data class Submitting(
        val kind: WalletInteractionKind,
    ) : WalletInteractionState

    data class Declining(
        val kind: WalletInteractionKind,
    ) : WalletInteractionState

    data class PreparedResponse(val message: String) : WalletInteractionState
    data class FollowingContinuation(val message: String) : WalletInteractionState

    data class Success(
        val kind: WalletInteractionKind,
        val outcome: WalletInteractionSuccessOutcome,
        val message: String,
    ) : WalletInteractionState

    data class LocalCancellation(
        val kind: WalletInteractionKind,
    ) : WalletInteractionState

    data class Failure(
        val kind: WalletInteractionKind,
        val message: String,
        val recoverable: Boolean,
    ) : WalletInteractionState
}

val WalletInteractionState.kindOrNull: WalletInteractionKind?
    get() = when (this) {
        WalletInteractionState.Idle,
        WalletInteractionState.SelectingInteraction,
        is WalletInteractionState.ExternalAuthorization,
        is WalletInteractionState.HandlingAuthorizationCallback,
        is WalletInteractionState.DeferredIssuance,
        is WalletInteractionState.ProtectedKeyAuthorization,
        is WalletInteractionState.PreparedResponse,
        is WalletInteractionState.FollowingContinuation,
        -> null
        is WalletInteractionState.Capturing -> kind
        is WalletInteractionState.Validating -> request.kind
        is WalletInteractionState.WrongRequestType -> expected
        is WalletInteractionState.Resolving -> request.kind
        is WalletInteractionState.ReviewingOffer -> WalletInteractionKind.Receive
        is WalletInteractionState.ReviewingPresentation -> WalletInteractionKind.Present
        is WalletInteractionState.InvalidReview -> kind
        is WalletInteractionState.Submitting -> kind
        is WalletInteractionState.Declining -> kind
        is WalletInteractionState.Success -> kind
        is WalletInteractionState.LocalCancellation -> kind
        is WalletInteractionState.Failure -> kind
    }

internal fun String.toInteractionKind(): WalletInteractionKind? =
    when (WalletDeepLinkScheme.parse(trim())) {
        WalletDeepLinkScheme.CredentialOffer -> WalletInteractionKind.Receive
        WalletDeepLinkScheme.PresentationRequest -> WalletInteractionKind.Present
        null -> null
    }

internal fun WalletInteractionKind.toLegacyTab(): WalletDemoTab = when (this) {
    WalletInteractionKind.Receive -> WalletDemoTab.Receive
    WalletInteractionKind.Present -> WalletDemoTab.Present
}

internal fun WalletDemoTab.toInteractionKind(): WalletInteractionKind = when (this) {
    WalletDemoTab.Credentials,
    WalletDemoTab.Receive,
    -> WalletInteractionKind.Receive
    WalletDemoTab.Present -> WalletInteractionKind.Present
}
