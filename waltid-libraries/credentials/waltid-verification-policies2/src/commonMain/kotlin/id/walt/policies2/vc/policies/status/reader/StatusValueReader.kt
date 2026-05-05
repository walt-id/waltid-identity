package id.walt.policies2.vc.policies.status.reader

import id.walt.policies2.vc.policies.status.StatusListContent

/**
 * Interface for reading and parsing status list content.
 */
interface StatusValueReader<out T> {
    /**
     * Checks if this reader can handle the given content.
     */
    fun canHandle(content: StatusListContent): Boolean
    
    /**
     * Reads and parses the status list content.
     */
    fun read(content: StatusListContent): Result<T>
}
