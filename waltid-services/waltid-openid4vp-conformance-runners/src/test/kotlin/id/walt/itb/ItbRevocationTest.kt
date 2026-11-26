package id.walt.itb

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ItbRevocationTest {
    @Test
    fun sharedStatusListNeedsDistinctIndicesOnTheSameList() {
        val first = claim("https://issuer.example/list", 0)
        checkStatusListReferences(listOf(first, claim("https://issuer.example/list", 1)))
        for (second in listOf(first, claim("https://issuer.example/other-list", 1))) {
            val error = assertFailsWith<ItbWalletRejection> { checkStatusListReferences(listOf(first, second)) }
            assertEquals("status_expected_shared_list_with_distinct_indices", error.code)
        }
    }

    private fun claim(uri: String, index: Int) = buildJsonObject {
        putJsonObject("status_list") {
            put("uri", uri)
            put("idx", index)
        }
    }
}
