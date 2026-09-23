package id.walt.certificate.x509.dn

import id.walt.certificate.x509.dn.RelativeDistinguishedNameEscapingUtil.unescapeString

/**
 * Parse DN Strings RFC 4514
 * https://datatracker.ietf.org/doc/html/rfc4514
 * https://datatracker.ietf.org/doc/html/rfc4517 (examples)
 */
internal class DnStringParser(val dnString: String) {

    var cursor = 0
    fun parse(): DistinguishedName {
        val dnList = mutableListOf<List<AttributeTypeAndValue>>()
        dnList.add(readRdnList())
        skipWhitespace()
        while (!guessEnd()) {
            expectString(",")
            dnList.add(readRdnList())
            skipWhitespace()
        }
        return DistinguishedName(dnList.toList())
    }

    private fun readRdnList(): List<AttributeTypeAndValue> {
        val typeAndValueList = mutableListOf<AttributeTypeAndValue>()
        do {
            skipWhitespace()
            val attributeName = readAttributeName()
            val attributeType = AttributeType.find(attributeName)
            skipWhitespace()
            expectString("=")
            skipWhitespace()
            val attributeValue = readAttributeValue()
            typeAndValueList.add(AttributeTypeAndValue(attributeType, attributeValue))
            if (!guessString("+")) {
                break
            }
            expectString("+")
        } while (true)
        return typeAndValueList.toList()
    }

    private fun readAttributeName(): String {
        val result = attributeNameRegex.find(dnString, cursor)
        require(result != null) { "Invalid DN String: '${dnString}'" }
        cursor += result.value.length
        return result.value
    }

    /**
     * attributeValue = string / hexstring
     */
    private fun readAttributeValue(): String {
        return if (guessString("\"")) {
            readQuotedAttributeValue()
        } else {
            readNotQuotedAttributeValue()
        }
    }


    fun readQuotedAttributeValue(): String {
        TODO()
    }

    fun readNotQuotedAttributeValue(): String {
        val valueNotTrimmed = readStringTillEndPattern(unquotedAttributeValueEndRegex)
        val valueTrimmed = valueNotTrimmed.trim()
        val value = if (valueTrimmed.length > 1
            && valueTrimmed.length < valueNotTrimmed.length
            && valueTrimmed[valueTrimmed.length - 1] == '\\'
        ) {
            // if the last char in the trimmed string is the escape ('\') char we need to
            // append the escaped char again
            valueTrimmed.plus(valueNotTrimmed[valueTrimmed.length])
        } else {
            valueTrimmed
        }
        require(value.isNotBlank()) { "Invalid DN String: '${dnString}'" }
        return unescapeString(value)
    }

    private fun readStringTillEndPattern(endPattern: Regex): String {
        val result = endPattern.find(dnString, cursor)
        return if (result != null) {
            //pattern found
            val value = dnString.substring(cursor, result.range.first)
            cursor = result.range.first
            value
        } else {
            //pattern not found, assume value is till end of string
            val value = dnString.substring(cursor, dnString.length)
            cursor = dnString.length
            value
        }
    }


    private fun skipWhitespace() {
        val result = whitespaceRegex.find(dnString, cursor)
        if (result != null && result.range.first == cursor) {
            cursor += result.value.length
        }
    }

    private fun expectString(expected: String) {
        require(guessString(expected)) { "Invalid DN String: '${dnString}'" }
        cursor += expected.length
    }

    private fun guessString(expected: String): Boolean {
        return guessMinimumChars(expected.length)
                && expected == dnString.substring(cursor, cursor + expected.length)
    }

    private fun expectMinimumChars(minimumChars: Int) {
        require(guessMinimumChars(minimumChars)) { "Invalid DN String: '${dnString}'" }
    }

    private fun guessMinimumChars(minimumChars: Int): Boolean {
        return dnString.length - cursor >= minimumChars
    }

    private fun guessEnd(): Boolean =
        cursor == dnString.length

    companion object {
        val whitespaceRegex = Regex("\\s+", RegexOption.MULTILINE)
        val attributeNameRegex = Regex("[a-z][a-z0-9\\-]*", RegexOption.IGNORE_CASE)
        private val unquotedAttributeValueEndRegex = "(?<!\\\\)[,+]".toRegex()
    }
}