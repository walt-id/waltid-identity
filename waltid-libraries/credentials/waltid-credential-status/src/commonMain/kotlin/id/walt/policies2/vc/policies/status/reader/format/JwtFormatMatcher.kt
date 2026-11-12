package id.walt.policies2.vc.policies.status.reader.format

import id.walt.credentials.utils.JwtUtils.isJwt
import id.walt.policies2.vc.policies.status.StatusListContent

/**
 * Matcher for JWT (JSON Web Token) status list format.
 * Matches text content that is a valid JWT.
 */
class JwtFormatMatcher : FormatMatcher {
    override fun matches(content: StatusListContent): Boolean = when (content) {
        is StatusListContent.Text -> content.content.isJwt()
        is StatusListContent.Binary -> false
    }
}