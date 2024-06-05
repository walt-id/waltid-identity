package resolvers

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.resolver.local.DidJwkResolver
import id.walt.did.dids.resolver.local.LocalResolverMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class DidJwkResolverTest : DidResolverTestBase() {
    override val sut: LocalResolverMethod = DidJwkResolver()

    @ParameterizedTest
    @MethodSource
    override fun `given a did String, when calling resolve, then the result is a valid did document`(
        did: String,
        key: JsonObject,
        assert: resolverAssertion<DidDocument>
    ) {
        super.`given a did String, when calling resolve, then the result is a valid did document`(did, key, assert)
    }

    @ParameterizedTest
    @MethodSource
    override fun `given a did String, when calling resolveToKey, then the result is valid key`(
        did: String,
        key: JsonObject,
        assert: resolverAssertion<Key>
    ) {
        super.`given a did String, when calling resolveToKey, then the result is valid key`(did, key, assert)
    }

    companion object {
        @JvmStatic
        fun `given a did String, when calling resolve, then the result is a valid did document`(): Stream<Arguments> =
            testData(
                secp256DidAssertions, ed25519DidAssertions, rsaDidAssertions
            )

        @JvmStatic
        fun `given a did String, when calling resolveToKey, then the result is valid key`(): Stream<Arguments> =
            testData(
                secp256KeyAssertions, ed25519KeyAssertions, rsaKeyAssertions
            )

        private fun <T> testData(
            secpAssertions: resolverAssertion<T>,
            ed25519Assertions: resolverAssertion<T>,
            rsaAssertions: resolverAssertion<T>
        ) =
            Stream.of(
                //secp256k1
                arguments(
                    "did:jwk:eyJrdHkiOiJFQyIsInVzZSI6InNpZyIsImNydiI6InNlY3AyNTZrMSIsImtpZCI6ImQ1YzUwNDhkMGU3NDQwYTQ4MzBlYTBiNDA3MTc0ZjgzIiwieCI6ImVLeF9GYUx6UE1UNG5kd3ZJbWRWX3BUdi1KWDFTUXBKOHRESzZHTGlZSUUiLCJ5IjoiLVR6cEd4R0xQblhXTUpXVHFZdnFuNTVaOFhpLUpfWk00MGJqdGpHYUVMcyIsImFsZyI6IkVTMjU2SyJ9",
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"use\":\"sig\",\"crv\":\"secp256k1\",\"kid\":\"d5c5048d0e7440a4830ea0b407174f83\",\"x\":\"eKx_FaLzPMT4ndwvImdV_pTv-JX1SQpJ8tDK6GLiYIE\",\"y\":\"-TzpGxGLPnXWMJWTqYvqn55Z8Xi-J_ZM40bjtjGaELs\",\"alg\":\"ES256K\"}"),
                    secpAssertions
                ),
                //secp256r1
                arguments(
                    "did:jwk:eyJrdHkiOiJFQyIsInVzZSI6InNpZyIsImNydiI6IlAtMjU2Iiwia2lkIjoiMjkyZGZhYmQxZjlkNDc3ZWI1Y2VmMjM5OTA5MTExYTEiLCJ4IjoiTk45akRUM0NMOXl4Z1lFamFFa1A4MENCOHExV0JiVUhJbFZ1UElCUWhpOCIsInkiOiJvcDQwT2Fla1NVSnluZm8xaENsWld1OFNBWU1UZjFPY2FDVXIwWUVyTlNjIiwiYWxnIjoiRVMyNTYifQ",
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"use\":\"sig\",\"crv\":\"P-256\",\"kid\":\"292dfabd1f9d477eb5cef239909111a1\",\"x\":\"NN9jDT3CL9yxgYEjaEkP80CB8q1WBbUHIlVuPIBQhi8\",\"y\":\"op40OaekSUJynfo1hClZWu8SAYMTf1OcaCUr0YErNSc\",\"alg\":\"ES256\"}"),
                    secpAssertions
                ),
                //ed25519
                arguments(
                    "did:jwk:eyJrdHkiOiJPS1AiLCJ1c2UiOiJzaWciLCJjcnYiOiJFZDI1NTE5Iiwia2lkIjoiMTUxZGY2ZWMwMTcxNDg4M2I4MTJmMjZmMmQ2M2U1ODQiLCJ4IjoicUJEc1l3M2s2Mm1VVDhVbUV4OTlYejN5Y2tpU1JtVHNMNmFhMjFaY0FWTSIsImFsZyI6IkVkRFNBIn0",
                    Json.decodeFromString<JsonObject>("{\"kty\":\"OKP\",\"use\":\"sig\",\"crv\":\"Ed25519\",\"kid\":\"151df6ec01714883b812f26f2d63e584\",\"x\":\"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM\",\"alg\":\"EdDSA\"}"),
                    ed25519Assertions
                ),
//                //rsa
//                arguments(
//                    "",
//                    Json.decodeFromString<JsonObject>(""),
//                    rsaAssertions
//                ),//not implemented core-crypto
            )
    }
}
