package id.walt.policies.policies.status.reader

interface StatusValueReader<out T> {
    fun read(response: String): Result<T>
}