package id.walt.dcql.jsonld

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

class BundledW3cContextsDigestTest {
    @Test
    fun runtimeSnapshotsMatchProvenanceDigests() {
        assertEquals(GeneratedBundledContexts.V1_JSON_SHA256, sha256(GeneratedBundledContexts.V1_JSON))
        assertEquals(GeneratedBundledContexts.V2_JSON_SHA256, sha256(GeneratedBundledContexts.V2_JSON))
        assertEquals(
            GeneratedBundledContexts.GAIA_X_DEVELOPMENT_JSON_SHA256,
            sha256(GeneratedBundledContexts.GAIA_X_DEVELOPMENT_JSON),
        )
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.encodeToByteArray())
        .joinToString("") { "%02x".format(it) }
}
