package id.walt.policies2.vc.policies.status.reader

interface StatusValueReader<out T> {
    fun canHandle(content: String): Boolean
    fun read(response: String): Result<T>
}
