package id.walt.policies2.policies.status.content

interface ContentParser<in K,out T> {
    fun parse(response: K): T
}