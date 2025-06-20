package id.walt.oid4vc.errors

import id.walt.oid4vc.providers.TokenTarget

class TokenVerificationError(val token: String, val target: TokenTarget, override val message: String? = null) :
    Exception()
