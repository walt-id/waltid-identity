package id.walt.policies2.vc.policies.status.reader.format

import id.walt.policies2.vc.policies.status.StatusListContent

/**
 * Interface for matching status list content formats.
 */
interface FormatMatcher {
    /**
     * Checks if this matcher can handle the given content.
     */
    fun matches(content: StatusListContent): Boolean
}