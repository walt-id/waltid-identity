package id.walt.policies.policies.status.reader

import id.walt.policies.policies.status.model.StatusContent
import id.walt.policies.policies.status.reader.format.FormatMatcher

abstract class StatusValueReaderBase<T : StatusContent>(
    private val formatMatcher: FormatMatcher,
) : StatusValueReader<T> {
    override fun canHandle(content: String): Boolean = formatMatcher.matches(content)
}