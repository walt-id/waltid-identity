package resolvers

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.resolver.local.DidKeyResolver
import id.walt.did.dids.resolver.local.LocalResolverMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class DidKeyResolverTest : DidResolverTestBase() {
    override val resolver: LocalResolverMethod = DidKeyResolver()

    @ParameterizedTest
    @MethodSource
    override fun `given a did String, when calling resolve, then the result is a valid did document`(
        did: String,
        key: JsonObject,
        resolverAssertion: resolverAssertion<DidDocument>,
    ) {
        super.`given a did String, when calling resolve, then the result is a valid did document`(did, key, resolverAssertion)
    }

    @ParameterizedTest
    @MethodSource
    override fun `given a did String, when calling resolveToKey, then the result is valid key`(
        did: String,
        key: JsonObject,
        resolverAssertion: resolverAssertion<Key>,
    ) {
        super.`given a did String, when calling resolveToKey, then the result is valid key`(did, key, resolverAssertion)
    }

    companion object {
        @JvmStatic
        fun `given a did String, when calling resolve, then the result is a valid did document`(): Stream<Arguments> =
            testData(secp256DidAssertions, ed25519DidAssertions, rsaDidAssertions)

        @JvmStatic
        fun `given a did String, when calling resolveToKey, then the result is valid key`(): Stream<Arguments> =
            testData(secp256KeyAssertions, ed25519KeyAssertions, rsaKeyAssertions)

        private fun <T> testData(
            secpAssertions: resolverAssertion<T>,
            ed25519Assertions: resolverAssertion<T>,
            rsaAssertions: resolverAssertion<T>,
        ) =
            Stream.of(
                //ed25519 (veramo)
                arguments(
                    "did:key:z6MkrP3u6dEz2gpWq89J7DMWtPCYW8dGrpPBzkfkDt9FXrQg",
                    Json.decodeFromString<JsonObject>("{\"kty\":\"OKP\",\"crv\":\"Ed25519\",\"x\":\"sTgvlFxftGMKOpengFu-N9AwgiSKwzdzmH75rxJh1ZE\"}"),
                    ed25519Assertions
                ),
                //secp256r1 (didlib)
                arguments(
                    "did:key:zDnaeWUm6JXpipAtL1N1hQFA4BQUwGCdaAVbpEZjN3Pf9nMrb",
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"use\":\"sig\",\"crv\":\"P-256\",\"kid\":\"488499670a514871aa39e8d0f7ef1b39\",\"x\":\"Wd1Y_UHnoM5zt-SSJeQWlAlLi4o8SIk8AvOvimIZeNQ\",\"y\":\"nNtkZmMNJp8kVJRtvO627KLMavvbfYHItTt7xNR84D4\",\"alg\":\"ES256\"}"),
                    secpAssertions
                ),
                //secp256k1 (uni-registrar)
                arguments(
                    "did:key:zQ3shTZmje6ppF4oboKTX85hUWGrR1nxrWnFA3tnJhsHEC8c4",
                    Json.decodeFromString<JsonObject>("{\"kty\":\"EC\",\"crv\":\"secp256k1\",\"x\":\"W3Mx2h5m8laNqq5SpmHSzkG2Eqn9EWQ2_TTz1yNIkvU\",\"y\":\"YYFSF5mM7ppXw9r9gH63YSV2dL1CkmMmqTGUwfbi8-I\"}"),
                    secpAssertions
                ),
                //rsa
                arguments(
                    "did:key:z4MXj1wBzi9jUstyQ7QXxaiUtzer96UgeJcWXBN1Z4vgX6Cpdu3yBRhaQDceisA4g8uUVgUMfmGkuRXJ22cFpJ5E2Qi49gz7r8jCSsxDQW4fsSqyF6d1Nd3H8k2rmU9D3U7rgisMBwZGE2fC2w1332WKzeXtSsyfdbWjDsEEwWHHtXRRnWFwQfPtp92KWL7H7awQhFJieh6We5hYVYA5JBRdPwUVFNTSyqYYhhtEJgiyHXDCevCgWnJmVwQ3dMpuwoj1QW4uN4WL1wUaD7ic8fLx3LxxmtXWA86GL4r1Prgistfymq7CJumGL5AEej42yqEya9rFfq2HzMi5DzypDqDVNEK1MLWwfFGHwqoxQ2m1JXpR8ZYZv",
                    Json.decodeFromString<JsonObject>("{\"kty\":\"RSA\",\"e\":\"AQAB\",\"use\":\"sig\",\"kid\":\"ab269ce10ce94b7c9565e30c034b5692\",\"alg\":\"RS256\",\"n\":\"0qbslQ5uMXL1Wk4dUD5ftrGWLhgaQENQn8AaPVREg12H_Mfr2GEL0IkBd7EQPeRFzRzngF2kWpij_nyueYKGQ3um_hione72pozP76etXNk4imTzmg3RsHcfPC5JBJAGpb5htnUQ5-VsuqbzlCUTOWNK4kIDWzbU0o-neglLAwU846_h6lTRI7xE1kh0iZyseAdx7sZ8Cd5eSYuvwQVxnNn0w-m9Bwd30g-s8xmqn9-7LBa0-UdumMLwtan4IGXltMJGYU9br1wsmz9vlG-TvfmxlgXzilJOJQMvlMKGXRmbUJRaNSYdrVJciEQEWK0tkaT45r3_LJw7dwx4DnNxzw\"}"),
                    rsaAssertions
                ),
            )
    }
}
