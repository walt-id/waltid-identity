package id.walt.policies.policies.status.parser

interface ContentParser<in K,out T> {
    fun parse(response: K): T
}