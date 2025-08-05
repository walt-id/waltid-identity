package resolvers

import id.walt.did.dids.resolver.UniresolverResolver
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.condition.EnabledIf
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.net.URI
import java.util.stream.Stream
import kotlin.test.assertEquals

class UniResolverTest {

    private val sut = UniresolverResolver()

    @ParameterizedTest
    @MethodSource
    @EnabledIf("isUniresolverAvailable")
    @Disabled("uniresolver failing to resolve test dids")
    fun `given a did String, when calling resolve, then the result is a valid did document`(
        did: String, document: String,
    ) = runTest {
        println("Resolving: $did")
        val result = sut.resolve(did).getOrThrow()

        check(Json.parseToJsonElement(document) == result) { "Non equal: $document  !=  $result" }
    }

    @ParameterizedTest
    @MethodSource
    @EnabledIf("isUniresolverAvailable")
    @Disabled("uniresolver failing to resolve test dids")
    fun `given a did String, when calling resolveToKey, then the result is valid key`(
        did: String, key: String,
    ) = runTest {
        println("Resolving: $did")
        val result = sut.resolveToKey(did)
        assertEquals(true, result.isSuccess)
        assertEquals(key, result.getOrNull()?.exportJWK())
    }

    companion object {
        @JvmStatic
        fun `given a did String, when calling resolve, then the result is a valid did document`(): Stream<Arguments> =
            Stream.of(
                arguments("did:v1:test:nym:z6MkoPnnkWaXsC94xPJHNLUi15TLyCBe68jrKPi7PenS3pi4",
                    URI(
                        Companion::class.java.classLoader.getResource("uniresolver/base58/document.json")!!.toString()
                    ).path.let { File(it).readText() }
                        .replace("[\\s\\n\\r]".toRegex(), "")),
                arguments(
                    "did:cheqd:testnet:55dbc8bf-fba3-4117-855c-1e0dc1d3bb47",
                    URI(
                        Companion::class.java.classLoader.getResource("uniresolver/multibase/document.json")!!
                            .toString()
                    ).path.let { File(it).readText() }
                        .replace("[\\s\\n\\r]".toRegex(), ""),
                ),
// disable flaky test
//                arguments(
//                    "did:io:0x476c81C27036D05cB5ebfe30ae58C23351a61C4A",
//                    URI(
//                        Companion::class.java.classLoader.getResource("uniresolver/hex/document.json")!!.toString()
//                    ).path.let { File(it).readText() }
//                        .replace("[\\s\\n\\r]".toRegex(), ""),
//                ),
            )

        @JvmStatic
        fun `given a did String, when calling resolveToKey, then the result is valid key`(): Stream<Arguments> =
            Stream.of(
                arguments(
                    "did:v1:test:nym:z6MkoPnnkWaXsC94xPJHNLUi15TLyCBe68jrKPi7PenS3pi4",
                    URI(
                        Companion::class.java.classLoader.getResource("uniresolver/base58/publicKeyJwk.json")!!
                            .toString()
                    ).path.let { File(it).readText() }
                        .replace("[\\s\\n\\r]".toRegex(), ""),
                ),
                arguments(
                    "did:cheqd:testnet:55dbc8bf-fba3-4117-855c-1e0dc1d3bb47",
                    URI(
                        Companion::class.java.classLoader.getResource("uniresolver/multibase/publicKeyJwk.json")!!
                            .toString()
                    ).path.let { File(it).readText() }
                        .replace("[\\s\\n\\r]".toRegex(), ""),
                ),
// disable flaky test
//                arguments(
//                    "did:io:0x476c81C27036D05cB5ebfe30ae58C23351a61C4A",
//                    URI(
//                        Companion::class.java.classLoader.getResource("uniresolver/hex/publicKeyJwk.json")!!.toString()
//                    ).path.let { File(it).readText() }
//                        .replace("[\\s\\n\\r]".toRegex(), ""),
//                ),
            )

        @JvmStatic
        fun isUniresolverAvailable() = runCatching {
            runBlocking { UniresolverResolver().getSupportedMethods() }
        }.fold(onSuccess = { it.isSuccess }, onFailure = { false })
    }
}
