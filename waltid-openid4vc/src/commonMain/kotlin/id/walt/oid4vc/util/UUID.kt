package id.walt.oid4vc.util

expect fun randomUUID(): String
expect fun sha256(data: ByteArray): ByteArray

expect suspend fun httpGet(url: String): String