package id.walt.policies.policies.status.parser

interface ContentParser<T> {
    fun parse(response: String): T
}