package resolvers

import id.walt.did.dids.resolver.UniresolverResolver
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.util.stream.Stream
import kotlin.test.assertEquals

class UniResolverTest {

    private val sut = UniresolverResolver()

    @ParameterizedTest
    @MethodSource
    fun `given a did String, when calling resolve, then the result is a valid did document`(
        did: String, document: String
    ) = runTest {
        val result = sut.resolve(did)
        assertEquals(true, result.isSuccess)
        assertEquals(document, result.getOrNull()?.toString())
    }

    @ParameterizedTest
    @MethodSource
    fun `given a did String, when calling resolveToKey, then the result is valid key`(
        did: String, key: String
    ) = runTest {
        //TODO: uniresolver publicKeyBase58 not implemented
        val result = sut.resolveToKey(did)
        assertEquals(true, result.isSuccess)
        assertEquals(key, result.getOrNull()?.exportJWK())
    }

    companion object {
        @JvmStatic
        fun `given a did String, when calling resolve, then the result is a valid did document`(): Stream<Arguments> =
            Stream.of(
                arguments("did:key:z6MkfXgppgAzxNZNijP35wjPdQjThkr78S3WXpsXLN8UpPH5#z6MkfXgppgAzxNZNijP35wjPdQjThkr78S3WXpsXLN8UpPH5",
                    Companion::class.java.classLoader.getResource("did-key/document.json")!!.path.let { File(it).readText() }
                        .replace("[\\s\\n\\r]".toRegex(), "")),
            )

        @JvmStatic
        fun `given a did String, when calling resolveToKey, then the result is valid key`(): Stream<Arguments> =
            Stream.of(
                arguments(
                    "did:key:z6MkfXgppgAzxNZNijP35wjPdQjThkr78S3WXpsXLN8UpPH5#z6MkfXgppgAzxNZNijP35wjPdQjThkr78S3WXpsXLN8UpPH5",
                    Companion::class.java.classLoader.getResource("did-key/publicKeyJwk.json")!!.path.let { File(it).readText() }
                        .replace("[\\s\\n\\r]".toRegex(), ""),
                ),
            )
    }
}