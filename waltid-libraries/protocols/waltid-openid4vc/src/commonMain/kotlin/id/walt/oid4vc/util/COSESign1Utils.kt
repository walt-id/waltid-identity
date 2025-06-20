package id.walt.oid4vc.util

import id.walt.oid4vc.providers.TokenTarget

expect object COSESign1Utils {
    fun verifyCOSESign1Signature(target: TokenTarget, token: String): Boolean
}
