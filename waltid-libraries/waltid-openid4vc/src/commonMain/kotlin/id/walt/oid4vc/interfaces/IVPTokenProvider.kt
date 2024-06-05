package id.walt.oid4vc.interfaces

import id.walt.oid4vc.data.dif.PresentationSubmission
import id.walt.oid4vc.errors.PresentationError
import id.walt.oid4vc.providers.SIOPSession
import id.walt.oid4vc.requests.TokenRequest
import kotlinx.serialization.json.JsonElement

interface IVPTokenProvider<S : SIOPSession> {

    /**
     * Generates and signs the verifiable presentation as requested in the presentation definition for the vp_token response of the given TokenRequest.
     * Throws a [PresentationError] exception if an error occurs.
     * @param session The [SIOPSession] object, containing the presentation definition with the required credentials and claims to be presented, and the nonce for key holder proof
     * @param tokenRequest The token request, which triggered the vp_token generation
     * @return A [PresentationResult] object containing the generated presentation, and the presentation submission data structure, describing the submitted presentation
     * @throws PresentationError
     */
    fun generatePresentationForVPToken(session: S, tokenRequest: TokenRequest): PresentationResult
}

data class PresentationResult(
    val presentations: List<JsonElement>,
    val presentationSubmission: PresentationSubmission
)
