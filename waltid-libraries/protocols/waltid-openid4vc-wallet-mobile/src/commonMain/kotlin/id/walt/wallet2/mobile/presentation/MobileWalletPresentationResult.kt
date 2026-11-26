package id.walt.wallet2.mobile.presentation

/**
 * Result of answering an OpenID4VP presentation request.
 *
 * Each subtype represents the next action required from the host app. This keeps
 * mutually exclusive response artifacts out of the same result instance.
 */
public sealed interface MobileWalletPresentationResult {
    /** The protocol response still requires a host-app delivery action. */
    public sealed interface Prepared : MobileWalletPresentationResult {
        /** The host app must open [url] to deliver the protocol response. */
        public data class OpenUrl(
            /** URL the host app must open to deliver the protocol response. */
            public val url: String,
        ) : Prepared

        /** The host app must render [html] so its self-submitting form can deliver the protocol response. */
        public data class SubmitForm(
            /** HTML document the host app must render to deliver the protocol response. */
            public val html: String,
        ) : Prepared
    }

    /** The protocol response was transmitted and the verifier returned a JSON response. */
    public sealed interface Transmitted : MobileWalletPresentationResult {
        /** Raw verifier response body encoded as JSON. */
        public val verifierResponseJson: String

        /** The verifier accepted the protocol response. */
        public data class Succeeded(
            override val verifierResponseJson: String,
            /** Optional post-response redirect for the host app to open. */
            public val redirectUrl: String? = null,
        ) : Transmitted

        /** The verifier rejected or could not process the protocol response. */
        public data class Failed(
            override val verifierResponseJson: String,
        ) : Transmitted
    }
}
