import Foundation

/// Result of responding to an OpenID4VP presentation request.
///
/// Each case represents the next action required from the host app.
public enum PresentationResult: Equatable, Sendable {
    /// A protocol response that still requires a host-app delivery action.
    public enum Prepared: Equatable, Sendable {
        /// The host app must open the URL to deliver the protocol response.
        case openURL(URL)

        /// The host app must render the HTML so its self-submitting form can deliver the protocol response.
        case submitForm(html: String)
    }

    /// A protocol response that was transmitted and received a JSON verifier response.
    public enum Transmitted: Equatable, Sendable {
        /// The verifier accepted the protocol response.
        case succeeded(verifierResponseJSON: String, redirectURL: URL? = nil)

        /// The verifier rejected or could not process the protocol response.
        case failed(verifierResponseJSON: String)
    }

    /// The host app still needs to deliver the prepared response.
    case prepared(Prepared)

    /// The verifier returned a response after protocol transmission.
    case transmitted(Transmitted)

}
