package id.walt.policies2.vc.policies.status.reader.format

import id.walt.credentials.utils.JwtUtils.isJwt

class JwtFormatMatcher : FormatMatcher {
    override fun matches(input: String) = input.isJwt()
}