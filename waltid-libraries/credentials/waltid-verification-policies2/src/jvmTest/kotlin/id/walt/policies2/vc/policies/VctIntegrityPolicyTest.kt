package id.walt.policies2.vc.policies

import id.walt.credentials.CredentialParser
import id.walt.sdjwt.metadata.type.TypeMetadataFixture
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.test.*

class VctIntegrityPolicyTest {
    private suspend fun credential(vct: String, pin: String) = CredentialParser.detectAndParse(
        listOf("""{"alg":"ES256","typ":"dc+sd-jwt"}""", buildJsonObject {
            put("vct", vct); put("vct#integrity", pin); put("_sd_alg", "sha-256"); put("_sd", JsonArray(emptyList()))
        }.toString(), "synthetic-signature").joinToString(".") {
            Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(it.encodeToByteArray())
        } + "~"
    ).second

    @Test fun syntaxOnlyModeCannotAcceptMalformedPinsOrClaimValueVerification() = runBlocking<Unit> {
        val policy = VctIntegrityPolicy(skipValueVerification = true)
        val invalid = policy.verify(credential("https://issuer.example/leaf", "sha512-broken"), PolicyExecutionContext.Empty)
        assertEquals(VctIntegrityException.Reason.MALFORMED, (invalid.exceptionOrNull() as VctIntegrityException).reason)
        val valid = policy.verify(credential("https://issuer.example/leaf", TypeMetadataFixture.LEAF_PIN), PolicyExecutionContext.Empty).getOrThrow().jsonObject
        assertEquals(JsonPrimitive(false), valid["vct_integrity_value_verified"])
    }

    @Test fun verifiesAllAlgorithmsAndSharesTheExactChainSnapshotWithQueryMatching() = runBlocking<Unit> {
        var calls = 0
        val http = HttpClient(MockEngine { request -> calls++; respond(TypeMetadataFixture.documents.getValue(request.url.toString())) })
        try {
            val resolver = CredentialTypeMetadataResolver(http)
            val context = PolicyExecutionContext(mapOf(PolicyServiceKey.TYPE_METADATA_RESOLVER to resolver))
            val leaf = credential("https://issuer.example/leaf", TypeMetadataFixture.LEAF_PIN)
            assertEquals(3, resolver.resolve(leaf).getOrThrow().types.size)
            val result = VctIntegrityPolicy().verify(leaf, context).getOrThrow().jsonObject
            assertEquals(3, calls)
            assertEquals(3, result.getValue("resolved_types").jsonArray.size)
            for (pin in listOf(TypeMetadataFixture.ROOT_SHA384, TypeMetadataFixture.ROOT_SHA512)) {
                assertTrue(VctIntegrityPolicy().verify(credential("https://types.example/sca", pin), context).isSuccess)
            }
        } finally { http.close() }
        val cancelled = HttpClient(MockEngine { throw CancellationException("cancelled") })
        try {
            val context = PolicyExecutionContext(mapOf(PolicyServiceKey.TYPE_METADATA_RESOLVER to CredentialTypeMetadataResolver(cancelled)))
            assertFailsWith<CancellationException> { VctIntegrityPolicy().verify(credential("https://issuer.example/leaf", TypeMetadataFixture.LEAF_PIN), context) }
        } finally { cancelled.close() }
    }
}
