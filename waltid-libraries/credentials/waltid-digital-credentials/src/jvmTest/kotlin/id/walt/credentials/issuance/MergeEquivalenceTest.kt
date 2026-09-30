package id.walt.credentials.issuance

import id.walt.credentials.issuance.CredentialDataMergeUtils.mergeMdocPayloadWithMapping
import id.walt.credentials.issuance.CredentialDataMergeUtils.mergeSDJwtVCPayloadWithMapping
import id.walt.credentials.issuance.CredentialDataMergeUtils.mergeWithMapping
import id.walt.w3c.vc.vcs.W3CVC
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The unified merge engine against the behaviour of the two implementations it replaced, on every edge its policies
 * decide. The expected outcomes were recorded from the replaced implementations (WAL-1000); any difference means an
 * issued credential would change.
 */
class MergeEquivalenceTest {

    private val context = mapOf("subjectDid" to JsonPrimitive("did:example:holder"), "issuerId" to JsonPrimitive("https://issuer"))

    private var counter = 0
    private fun next() = JsonPrimitive("value-${++counter}")

    private val functions: Map<String, suspend (CredentialDataMergeUtils.FunctionCall) -> JsonElement> = mapOf(
        "uuid" to { next() },
        "timestamp" to { JsonPrimitive("2031-02-03T04:05:06Z") },
        "subjectDid" to { it.fromContext() },
        "echo" to { JsonPrimitive(it.args!!) },
        "last" to { it.history?.get(it.args!!) ?: throw IllegalArgumentException("no history for ${it.args}") },
    )
    private val data = Json.parseToJsonElement(
        """{"type":["VerifiableCredential"],"credentialSubject":{"name":"Jane","tags":["a"]},"scalar":"x","nothing":null,
           "ns":{"given":"Jane","list":[1,2]}}"""
    ).jsonObject

    private val mappings = listOf(
        """{"id":"<uuid>"}""",
        """{"credentialSubject":{"id":"<subjectDid>","issued":"<timestamp>"}}""",
        """{"credentialSubject":{"tags":["b","<uuid>"]}}""",
        """{"type":["ExampleCredential"]}""",
        """{"fresh":["<uuid>",{"inner":"<echo:hi>"},[["<timestamp>"]]]}""",
        """{"scalar":{"now":"object"}}""",
        """{"nothing":{"now":"object"}}""",
        """{"empty":{}}""",
        """{"credentialSubject":{}}""",
        """{"ns":{"list":["<uuid>"],"extra":{"deep":"<echo:x>"}}}""",
        """{"id":"<uuid>","again":"<last:uuid>"}""",
        """{"jwt:jti":"<uuid>","id":"<last:uuid>"}""",
        """{"number":5,"bool":true,"literal":"<not a template>","plain":"text"}""",
        """{"credentialSubject":{"tags":{"not":"an array"}}}""",
    ).map { Json.parseToJsonElement(it).jsonObject }

    private suspend fun outcome(block: suspend () -> Any?): String = runCatching { block().toString() }
        .getOrElse { "error: ${it::class.simpleName}: ${it.message}" }

    private val recorded: Map<String, List<String>> =
        javaClass.getResource("/merge-behaviour-before-unification.tsv")!!.readText().lines().filter { it.isNotBlank() }
            .associate { line -> line.split("\t").let { it[0] to it.drop(1) } }

    @Test
    fun `the unified engine merges exactly as the implementations it replaced`() = runTest {
        assertEquals(mappings.size, recorded.size, "every mapping has a recorded outcome")
        for (mapping in mappings) {
            val (w3c, sdJwt, mdoc) = recorded.getValue(mapping.toString())
            counter = 0
            assertEquals(w3c, outcome { W3CVC(data).mergeWithMapping(mapping, context, functions).let { it.vc.toString() + it.results } }, "W3C, mapping $mapping")
            counter = 0
            assertEquals(sdJwt, outcome { data.mergeSDJwtVCPayloadWithMapping(mapping, context, functions) }, "SD-JWT, mapping $mapping")
            counter = 0
            assertEquals(mdoc, outcome { data.mergeMdocPayloadWithMapping(mapping, context, functions) }, "mdoc, mapping $mapping")
        }
    }
}
