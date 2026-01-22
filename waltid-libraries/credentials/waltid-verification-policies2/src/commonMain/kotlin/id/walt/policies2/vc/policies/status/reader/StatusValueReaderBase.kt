package id.walt.policies2.vc.policies.status.reader

import id.walt.policies2.vc.policies.status.model.StatusContent
import id.walt.policies2.vc.policies.status.reader.format.FormatDetector

abstract class StatusValueReaderBase<T : StatusContent>(
    private val formatDetector: FormatDetector,
) : StatusValueReader<T> {
    override fun canHandle(content: String): Boolean = formatDetector.matches(content)
}