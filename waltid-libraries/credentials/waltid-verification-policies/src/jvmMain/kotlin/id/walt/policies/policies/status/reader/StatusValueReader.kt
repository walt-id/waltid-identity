package id.walt.policies.policies.status.reader

interface StatusValueReader {
    fun read(response: String, statusListIndex: ULong): Result<List<Char>>
}