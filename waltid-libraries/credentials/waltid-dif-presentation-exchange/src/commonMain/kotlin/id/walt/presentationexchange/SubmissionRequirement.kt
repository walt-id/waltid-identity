package id.walt.presentationexchange

import kotlinx.serialization.*

@Serializable
data class SubmissionRequirement(
    val name: String? = null,
    val purpose: String? = null,
    val rule: Rule,
    val count: Int? = null,
    val min: Int? = null,
    val max: Int? = null,
    val from: String? = null,
    @SerialName("from_nested")
    val fromNested: List<SubmissionRequirement>? = null
) {
    init {
        count?.let { check(it >= 1) { "count must be at least 1" } }
        min?.let { check(it >= 0) { "min must be at least 0" } }
        max?.let { check(it >= 0) { "max must be at least 0" } }

        if (from != null) {
            check(from.isNotBlank()) { "from must not be blank" }
        } else {
            checkNotNull(fromNested) { "Either 'from' or 'from_nested' must be provided" }
            check(fromNested.isNotEmpty()) { "from_nested must not be empty" }
        }
    }


    @Suppress("EnumEntryName")
    enum class Rule {
        all,
        pick
    }
}
