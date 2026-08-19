package id.walt.certificate.x509

internal object IpAddressUtil {

    fun parseString(ipString: String): ByteArray {
        val result = ipV4AddressRegex.matchEntire(ipString)
        if (result != null) {
            return parseIpV4Address(result)
        }
        return parseIpV6Address(ipString)

    }

    fun byteArrayToIpAddress(value: ByteArray): String =
        if (value.size == 4) {
            byteArrayToIpV4Address(value)
        } else {
            throw IllegalArgumentException("Invalid IP address size: '${value.size}' (IPv6 addresses are not supported yet)")
        }


    private fun parseIpV4Address(ipMatch: MatchResult): ByteArray {
        check(ipMatch.groupValues.size == 5) { "Invalid IP address format" }
        return ipMatch.groupValues.subList(1, 5)
            .map { it.toInt().toByte() }
            .toByteArray()
    }

    private fun parseIpV6Address(ipString: String): ByteArray {
        TODO()
    }

    private fun byteArrayToIpV4Address(value: ByteArray): String =
        value.toList()
            .map { it.toUByte().toString() }
            .reduce { acc, s -> "$acc.$s" }

    private val ipV4AddressRegex = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\$")


}