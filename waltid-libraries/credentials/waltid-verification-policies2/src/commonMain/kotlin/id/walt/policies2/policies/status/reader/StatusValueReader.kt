package id.walt.policies2.policies.status.reader

interface StatusValueReader<out T> {
    fun read(response: String): Result<T>
}