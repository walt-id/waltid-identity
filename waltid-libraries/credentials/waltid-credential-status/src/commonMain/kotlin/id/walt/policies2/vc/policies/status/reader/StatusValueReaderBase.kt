package id.walt.policies2.vc.policies.status.reader

import id.walt.policies2.vc.policies.status.StatusListContent
import id.walt.policies2.vc.policies.status.model.StatusContent
import id.walt.policies2.vc.policies.status.reader.format.FormatMatcher

abstract class StatusValueReaderBase<T : StatusContent>(
    private val formatMatcher: FormatMatcher,
) : StatusValueReader<T> {
    override fun canHandle(content: StatusListContent): Boolean = formatMatcher.matches(content)
}