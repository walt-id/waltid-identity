package id.walt.sdjwt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression test for a concurrency bug in [SDPayload]: the disclosure digest was computed with a
 * SHARED, stateful `org.kotlincrypto` SHA256 instance. When the verifier resolved multiple SD-JWTs'
 * full payloads in parallel (parallel policy execution), the shared instance's internal state raced,
 * producing wrong digests and spurious "N disclosure(s) not referenced by any digest" failures.
 *
 * This reproduces the parallelism: many SDPayload.fullPayload resolutions concurrently. With the
 * shared instance it failed intermittently; with a per-call SHA256 it is correct and deterministic.
 */
class SDPayloadConcurrencyTest {

    private fun sample(i: Int): SDPayload = SDPayload.createSDPayload(
        fullPayload = buildJsonObject {
            put("iss", "https://issuer.example")
            put("sub", "subject-$i")
            put("given_name", "Name$i")
            put("family_name", "Family$i")
            put("birthdate", "19${10 + (i % 80)}-01-01")
        },
        disclosureMap = SDMap.fromJSON(
            """{ "fields": { "given_name": { "sd": true }, "family_name": { "sd": true }, "birthdate": { "sd": true } } }"""
        )
    )

    @Test
    fun concurrentFullPayloadResolutionProducesCorrectDigests() = runBlocking {
        // Resolve many SD-JWTs' full payloads concurrently on a multi-threaded dispatcher.
        val results = withContext(Dispatchers.Default) {
            (0 until 200).map { i ->
                async {
                    val sd = sample(i)
                    // Must NOT throw "disclosure(s) not referenced by any digest".
                    val full = sd.fullPayload
                    Triple(
                        full["given_name"]?.jsonPrimitive?.content,
                        full["family_name"]?.jsonPrimitive?.content,
                        full["birthdate"]?.jsonPrimitive?.content,
                    )
                }
            }.awaitAll()
        }

        results.forEachIndexed { i, (given, family, birth) ->
            assertEquals("Name$i", given)
            assertEquals("Family$i", family)
            assertEquals("19${10 + (i % 80)}-01-01", birth)
        }
    }
}
