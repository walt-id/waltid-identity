package id.walt.crypto.utils

import org.kotlincrypto.hash.sha2.SHA256
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

object ShaUtils {

    // Helper to calculate SHA-256 and then Base64URL encode
    @OptIn(ExperimentalEncodingApi::class)
    fun calculateSha256Base64Url(input: String): String =
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
            .encode(
                SHA256().digest(input.encodeToByteArray())
            )

}
