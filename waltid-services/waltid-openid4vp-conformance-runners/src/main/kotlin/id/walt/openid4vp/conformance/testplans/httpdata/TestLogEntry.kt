package id.walt.openid4vp.conformance.testplans.httpdata

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * A single entry of a conformance-suite test log (`GET /api/log/{testId}`).
 *
 * Only the fields the runners act on are modelled; the suite adds many more.
 */
@Serializable
data class TestLogEntry(
    @SerialName("_id")
    val id: String? = null,
    val src: String? = null,
    val msg: String? = null,
    /** `SUCCESS`, `FAILURE`, `WARNING`, `INFO`, `REVIEW`, ... - absent for plain log lines. */
    val result: String? = null,
    /** Spec sections this entry checks, e.g. `OID4VP-1FINAL-8.2`. */
    val requirements: List<String> = listOf(),
    /** Placeholder token for an image the tester is expected to upload. */
    val upload: String? = null,
    /** The uploaded image, once a placeholder has been filled. */
    val img: String? = null,
    /**
     * Value the suite compared, when this entry is a check.
     *
     * A gateway status here is a number (`502`). Other checks put strings or objects in the same
     * field, so this stays a [JsonElement] and callers pick out the numeric HTTP status themselves.
     */
    val actual: JsonElement? = null,
) {
    /** An image placeholder that is still waiting to be filled. */
    val isPendingUpload: Boolean get() = upload != null && img == null
}
