package id.walt.oid4vc.errors

import id.walt.oid4vc.data.dif.PresentationDefinition
import id.walt.oid4vc.requests.TokenRequest
import id.walt.oid4vc.responses.TokenErrorCode

class PresentationError(
    val errorCode: TokenErrorCode,
    val tokenRequest: TokenRequest,
    val presentationDefinition: PresentationDefinition?,
    override val message: String? = null
) : Exception()
