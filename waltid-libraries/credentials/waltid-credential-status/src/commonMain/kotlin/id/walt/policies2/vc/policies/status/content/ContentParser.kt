package id.walt.policies2.vc.policies.status.content

interface ContentParser<in K, out T> {
    fun parse(response: K): T
}
