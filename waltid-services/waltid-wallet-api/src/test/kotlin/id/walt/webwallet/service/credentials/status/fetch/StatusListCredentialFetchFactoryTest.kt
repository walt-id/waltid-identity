package id.walt.webwallet.service.credentials.status.fetch


import io.mockk.mockk
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream
import kotlin.test.assertTrue

class StatusListCredentialFetchFactoryTest {
    private val sut = StatusListCredentialFetchFactory(defaultStrategy, entraStrategy)

    @ParameterizedTest
    @MethodSource
    fun `given a url, when instantiating a fetch strategy, then the corresponding strategy is returned`(
        url: String, strategy: StatusListCredentialFetchStrategy
    ) {
        val result = sut.new(url)
        assertTrue(result == strategy)
    }

    companion object {
        val defaultStrategy = mockk<DefaultStatusListCredentialFetchStrategy>()
        val entraStrategy = mockk<EntraStatusListCredentialFetchStrategy>()

        @JvmStatic
        fun `given a url, when instantiating a fetch strategy, then the corresponding strategy is returned`(): Stream<Arguments> =
            Stream.of(
                arguments("did:ion:EiD...<SNIP>?service=IdentityHub&queries=W3sibWV", entraStrategy),
                arguments("http://example.com/credentials/status/3", defaultStrategy),
                arguments("https://example.com/credentials/status/3", defaultStrategy),
                arguments("https://my.domain.example.com/credentials/status/3", defaultStrategy),
                arguments("https://my.domain.example.com/credentials/status-credential", defaultStrategy),
                arguments("https://my.domain.example.com/credentials/status-credential#3", defaultStrategy),
            )
    }
}
