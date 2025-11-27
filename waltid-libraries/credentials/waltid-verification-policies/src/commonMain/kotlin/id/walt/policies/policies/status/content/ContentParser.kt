package id.walt.policies.policies.status.content

interface ContentParser<in K,out T> {
    fun parse(response: K): T
}