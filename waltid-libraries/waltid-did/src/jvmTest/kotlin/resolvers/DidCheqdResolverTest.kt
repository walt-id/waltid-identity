package resolvers

import id.walt.crypto.keys.Key
import id.walt.did.dids.document.DidDocument
import id.walt.did.dids.resolver.local.DidCheqdResolver
import id.walt.did.dids.resolver.local.LocalResolverMethod
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

class DidCheqdResolverTest : DidResolverTestBase() {
    override val resolver: LocalResolverMethod = DidCheqdResolver()

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
    @Disabled // not implemented
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
            Stream.of(
                arguments(
                    "did:cheqd:testnet:W5a3426DZ1f4qBkYC9ZT6s",
                    Json.decodeFromString<JsonObject>("{\"kty\":\"OKP\",\"use\":\"sig\",\"crv\":\"Ed25519\",\"kid\":\"151df6ec01714883b812f26f2d63e584\",\"x\":\"qBDsYw3k62mUT8UmEx99Xz3yckiSRmTsL6aa21ZcAVM\",\"alg\":\"EdDSA\"}"),
                    ed25519DidAssertions
                )
            )

        @JvmStatic
        fun `given a did String, when calling resolveToKey, then the result is valid key`(): Stream<Arguments> =
            Stream.of(
                arguments(
                    "did:cheqd:testnet:38088d21-a5f8-4277-bd35-b36918d81c14",
                    Json.decodeFromString<JsonObject>("{\"kty\":\"OKP\",\"d\":\"24WxHxiKpnd1_BitZBU57ex8EKaNukiyC4punO4Lh-s\",\"crv\":\"Ed25519\",\"kid\":\"GaQD9pzL5wJyATB1UA2J71ygXgkykT1QOnL7uIBgcpo\",\"x\":\"bz2K3xX-D_R2_Pu7al6UCRXGSl1pzBRfEoD3bj94s_w\"}"),
                    ed25519KeyAssertions
                )
            )
    }
}
