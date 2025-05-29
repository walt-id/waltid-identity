package id.walt.policies.policies.status.parser

interface ContentParser<out T> {
    fun parse(response: String): T
}