package id.walt.openid4vp.clientidprefix

actual fun extractSanDnsNamesFromDer(der: ByteArray): Result<List<String>> =
    Result.failure(
        UnsupportedOperationException("X.509 SAN DNS extraction is not implemented on iOS yet.")
    )
