package id.walt.openid4vp.clientidprefix

expect fun extractSanDnsNamesFromDer(der: ByteArray): Result<List<String>>
