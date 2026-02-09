package id.walt.policies.policies.status.reader

interface StatusValueReader<out T> {
    fun canHandle(content: String): Boolean
    fun read(response: String): Result<T>
}